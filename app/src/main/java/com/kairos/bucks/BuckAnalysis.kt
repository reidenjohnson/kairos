package com.kairos.bucks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kairos.engine.bucks.BuckInsights
import com.kairos.engine.bucks.BuckPhoto
import com.kairos.engine.bucks.BuckReport
import com.kairos.engine.bucks.CamInfo
import com.kairos.engine.bucks.CameraWx
import com.kairos.engine.bucks.FeatureBuilder
import com.kairos.engine.bucks.Factors
import com.kairos.engine.bucks.HourFeatures
import com.kairos.engine.bucks.HuntWindow
import com.kairos.engine.bucks.HuntWindows
import com.kairos.engine.bucks.WxHour
import com.kairos.engine.bucks.DeerVisit
import com.kairos.engine.bucks.DateFix
import com.kairos.engine.bucks.StripDateCheck
import com.kairos.engine.bucks.HisWinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Runs the whole Bucks analysis: pulls (or reads cached) weather for every camera, builds the
 * hour features, and patterns every buck plus the young-buck pool. Results live in [state] so
 * every screen reads the same numbers.
 */
object BuckAnalysis {
    data class CamBundle(
        val cam: Camera,
        val baseline: List<HourFeatures>,
        val forecast: List<HourFeatures>,
        val builder: FeatureBuilder?,
        val all: List<HourFeatures>,
        /**
         * Wind for the map's timeline: HRRR every 15 min from the day before the first photo to
         * now, then the hourly forecast (16 days) after the last 15-min step.
         */
        val quarters: List<BuckWeather.Quarter> = emptyList(),
        /** The raw forecast hours (with the extras and the second model's wind), for the Hunt view. */
        val raw: List<WxHour> = emptyList(),
    ) {
        /** Last moment the map can play to (end of the forecast). */
        val end: Long get() = quarters.lastOrNull()?.t ?: 0L
        /** Hour features for an exact moment (pressure trend, front, moon...). */
        fun at(t: Long): HourFeatures? = builder?.let { it.atMoment(t, all) ?: it.atMoment(t, forecast) }
    }

    data class Result(
        val at: Long,
        val cams: Map<String, CamBundle>,
        val reports: Map<String, BuckReport>,
        /** Each buck's sits at his best camera (chronological). */
        val windows: Map<String, List<HuntWindow>>,
        /** camera id -> buck id -> that buck's sits at that camera (chronological). */
        val camWindows: Map<String, Map<String, List<HuntWindow>>>,
        val photoWx: Map<String, HourFeatures>,
        val notes: List<String>,
        /** camera id -> every deer visit there (any deer), with its wind and clock time. */
        val deerVisits: Map<String, List<DeerVisit>> = emptyMap(),
        /** photo id -> a strip date that looks misread (its temperature doesn't fit that day). */
        val dateFixes: Map<String, DateFix> = emptyMap(),
    )

    var state by mutableStateOf<Result?>(null)
        private set
    var running by mutableStateOf(false)
        private set

    private val lock = kotlinx.coroutines.sync.Mutex()

    /** One run at a time (the app re-runs on changes and hourly); each reads the newest data. */
    suspend fun run(@Suppress("UNUSED_PARAMETER") db: BuckDb) = withContext(Dispatchers.IO) {
        lock.lock()
        running = true
        try {
            state = compute(BuckStore.db)
        } finally {
            running = false
            lock.unlock()
        }
    }

    /**
     * Every deer visit at each camera, whatever the deer: photos sorted as a buck, young buck, doe
     * or "can't tell", grouped like buck visits (same camera, within the visit window). Unsorted
     * and no-deer photos are left out.
     */
    private fun deerVisits(db: BuckDb, wx: Map<String, HourFeatures>, zone: ZoneId): Map<String, List<DeerVisit>> {
        val deer = setOf(PhotoLabel.BUCK, PhotoLabel.DOE, PhotoLabel.DEER_UNKNOWN)
        return db.photos.filter { it.label in deer && it.cameraId != null && it.epochSec != null }
            .groupBy { it.cameraId!! }.mapValues { (_, ps) ->
                val sorted = ps.sortedBy { it.epochSec }
                val groups = ArrayList<MutableList<Photo>>()
                for (p in sorted) {
                    val last = groups.lastOrNull()?.last()
                    if (last != null && p.epochSec!! - last.epochSec!! <= BuckPrefs.visitGapSec) groups.last() += p else groups += mutableListOf(p)
                }
                groups.mapNotNull { g ->
                    val f = g.firstNotNullOfOrNull { wx[it.id] } ?: return@mapNotNull null
                    val mph = f.windMph ?: return@mapNotNull null
                    val z = java.time.Instant.ofEpochSecond(g.first().epochSec!!).atZone(zone)
                    val labels = g.flatMap { p ->
                        when (p.label) {
                            PhotoLabel.BUCK -> p.buckIds.mapNotNull { id -> db.buck(id)?.let { if (it.pool) "Young" else it.name } }
                            PhotoLabel.DOE -> listOf("Doe")
                            else -> listOf("Not sure")
                        }
                    }.toSet()
                    DeerVisit(g.first().epochSec!!, g.map { it.id }, labels, f.windDir?.takeIf { mph >= HisWinds.CALM_MPH }, mph, z.hour * 60 + z.minute, f.legal)
                }
            }
    }

    /** SPYPOINT app saves are named for when they were saved: Spypoint_20261008_011803.jpg. */
    private val SAVED = Regex("""(20\d{6})_(\d{6})""")

    /**
     * Strip dates whose temperature doesn't fit that day at the camera (StripDateCheck). Only
     * strip-read times are checked; EXIF and hand-set times are his or the camera's.
     */
    private fun dateFixes(db: BuckDb, bundles: Map<String, CamBundle>): Map<String, DateFix> {
        val out = HashMap<String, DateFix>()
        for (p in db.photos) {
            if (p.timeSource != TimeSource.STRIP) continue
            val read = p.capture ?: continue
            val f = p.stripTempF ?: continue
            val b = bundles[p.cameraId ?: continue] ?: continue
            if (b.all.isEmpty()) continue
            val zone = ZoneId.of(p.zone)
            val saved = SAVED.find(p.srcName)?.let { m ->
                runCatching { java.time.LocalDateTime.parse(m.groupValues[1] + m.groupValues[2], java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) }.getOrNull()
            }
            // Burst twins: same camera, same clock minute, same strip temperature.
            val twins = db.photos.filter { it.id != p.id && it.cameraId == p.cameraId && it.stripTempF == f && it.capture?.toLocalTime()?.withSecond(0) == read.toLocalTime().withSecond(0) }
                .mapNotNull { it.capture }
            fun model(t: Long): Double? {
                val i = b.all.binarySearchBy(t / 3600 * 3600) { it.t }
                if (i < 0) return null
                val a = b.all[i].tempF ?: return null
                val n = b.all.getOrNull(i + 1)?.takeIf { it.t == b.all[i].t + 3600 }?.tempF ?: return a
                return a + (n - a) * (t - b.all[i].t) / 3600.0
            }
            StripDateCheck.check(read, f, zone, ::model, saved, twins)?.let { out[p.id] = it }
        }
        return out
    }

    private fun compute(db: BuckDb): Result {
        val zone = ZoneId.systemDefault()
        Factors.rut = db.rut
        Factors.units = U
        BuckInsights.visitGapSec = BuckPrefs.visitGapSec
        val now = System.currentTimeMillis() / 1000
        val notes = ArrayList<String>()
        val bundles = HashMap<String, CamBundle>()
        for (cam in db.cameras) {
            val times = db.photos.filter { it.cameraId == cam.id }.mapNotNull { it.epochSec }
            if (cam.lat == null || cam.lon == null) {
                if (times.isNotEmpty()) notes += "${cam.name} has no location, so its photos have no weather."
                bundles[cam.id] = CamBundle(cam, emptyList(), emptyList(), null, emptyList())
                continue
            }
            // Through today, so the map can scrub any moment since the first photo.
            val all0 = db.photos.mapNotNull { it.epochSec }
            val hist = if (all0.isEmpty()) emptyList() else BuckWeather.history(
                cam, BuckWeather.utcDate(all0.min()).minusDays(3), BuckWeather.utcDate(now),
            )
            val fc = BuckWeather.forecast(cam)
            // Normals per local day, looked up once (NormalsRepository caches the 30-yr pull per spot).
            val days = (hist + fc).map { java.time.Instant.ofEpochSecond(it.t).atZone(zone).toLocalDate() }.distinct()
            val normals = days.associateWith { BuckWeather.normal(cam, it) }
            val builder = FeatureBuilder(cam.lat, cam.lon, zone, db.rut) { normals[it] }
            val all = builder.build(hist)
            // Baseline = the hours the camera was watching: first photo to last photo.
            val base = if (times.isEmpty()) emptyList() else all.filter { it.t >= times.min() - 3600 && it.t <= times.max() + 3600 }
            if (times.isNotEmpty() && hist.isEmpty()) notes += "${cam.name}: no weather yet (offline?). Pull to refresh when you have signal."
            // Every camera gets the whole span the ground has photos for, so the map can compare them.
            val span = db.photos.mapNotNull { it.epochSec }
            val q = if (span.isEmpty()) emptyList() else BuckWeather.quarters(cam, BuckWeather.utcDate(span.min()).minusDays(1), BuckWeather.utcDate(now).plusDays(1))
            // Future: the forecast's hours after the last 15-min step (hourly steps blend fine on the map).
            val lastQ = q.lastOrNull { it.mph != null }?.t ?: now
            val future = fc.filter { it.t > lastQ }.map { BuckWeather.Quarter(it.t, it.windMph, it.windDir, it.gustMph, it.tempF) }
            bundles[cam.id] = CamBundle(cam, base, builder.build(fc), builder, all, q + future, fc)
        }

        val camWx = bundles.mapValues { (_, b) ->
            val info = CamInfo(b.cam.id, b.cam.name, b.cam.lat, b.cam.lon, b.cam.facingDeg)
            CameraWx(info, b.baseline) { t -> b.builder?.atMoment(t, b.all) }
        }
        val photoWx = HashMap<String, HourFeatures>()
        for (p in db.photos) {
            val t = p.epochSec ?: continue
            val f = camWx[p.cameraId ?: continue]?.at?.invoke(t) ?: continue
            photoWx[p.id] = f
        }

        val reports = HashMap<String, BuckReport>()
        val windows = HashMap<String, List<HuntWindow>>()
        val camWindows = HashMap<String, HashMap<String, List<HuntWindow>>>()
        for (b in db.bucks) {
            val photos = db.photos.filter { b.id in it.buckIds && it.cameraId != null && it.epochSec != null }
                .map { BuckPhoto(it.id, it.cameraId!!, it.epochSec!!, it.heading) }
            val r = BuckInsights.analyze(b.id, photos, camWx, zone, now)
            reports[b.id] = r
            for ((camId, cb) in bundles) {
                if (cb.cam.lat == null) continue
                val info = CamInfo(cb.cam.id, cb.cam.name, cb.cam.lat, cb.cam.lon, cb.cam.facingDeg)
                val approach = r.cameras.firstOrNull { it.camera.id == camId }?.approachDeg
                camWindows.getOrPut(camId) { HashMap() }[b.id] = HuntWindows.upcoming(r, cb.forecast, cb.raw, now, info, approach, zone, cb.cam.windSectors)
            }
            windows[b.id] = r.cameras.firstOrNull()?.let { camWindows[it.camera.id]?.get(b.id) }.orEmpty()
        }
        return Result(System.currentTimeMillis(), bundles, reports, windows, camWindows, photoWx, notes, deerVisits(db, photoWx, zone), dateFixes(db, bundles))
    }
}
