package com.kairos.engine.bucks

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A buck's pattern, worked out ONLY from his own sightings and the model weather at his cameras.
 * Every number on the buck page comes from here, and every "he likes X" is measured against how
 * often X happened at that camera while it was up (the baseline), with an exact binomial test
 * for luck. Nothing is filled in: unknown weather is left out and said so.
 */

enum class Heading(val label: String) { LEFT("Left"), RIGHT("Right"), TOWARD("Toward cam"), AWAY("Away") }

/** A camera as the analysis sees it. [facingDeg] = compass direction the lens points. */
data class CamInfo(val id: String, val name: String, val lat: Double?, val lon: Double?, val facingDeg: Double?)

/** One photo of this buck. [t] = capture time, unix seconds. */
data class BuckPhoto(val photoId: String, val cameraId: String, val t: Long, val heading: Heading?)

/**
 * A camera's weather for the analysis: [baseline] = feature hours inside the camera's active
 * window (what could have happened while it was watching), [at] = features for an exact moment.
 */
class CameraWx(val cam: CamInfo, val baseline: List<HourFeatures>, val at: (Long) -> HourFeatures?)

/** Photos of one buck at one camera close together (BuckInsights.visitGapSec) = one visit. */
data class Visit(
    val cameraId: String,
    val start: Long,
    val end: Long,
    val photoIds: List<String>,
    val heading: Heading?,
    val f: HourFeatures?,
)

enum class Verdict(val label: String) { STRONG("Strong"), LEANING("Leaning"), AVOIDS("Rarely"), NONE("") }

data class BinStat(
    val label: String,
    val visits: Int,
    val share: Double,      // of his visits with this factor known
    val baseShare: Double,  // of the comparable camera hours
    val lift: Double,       // share / baseShare
    val pHigh: Double,      // P(this many or more by luck)
    val pLow: Double,       // P(this few or fewer by luck)
    val verdict: Verdict,
    /** Position in [Factor.bins]: what code matches on (labels follow the user's units). */
    val index: Int,
)

data class FactorStat(val key: String, val title: String, val n: Int, val baseHours: Int, val bins: List<BinStat>)

data class Finding(val factorKey: String, val factorTitle: String, val bin: BinStat, val text: String)

data class CamStat(
    val camera: CamInfo,
    val visits: Int,
    val legalVisits: Int,
    val lastSeen: Long,
    val medianGapDays: Double?,
    val daysSinceLast: Int,
    val due: Boolean,
    val approachDeg: Double?,  // compass direction he comes FROM (needs facing + headings)
    val travelCounts: Map<String, Int>,
)

data class Move(
    val fromId: String,
    val toId: String,
    val count: Int,
    val medianGapMin: Int,
    val distanceM: Double?,
    val bearingDeg: Double?,
    val examples: List<Pair<Long, Long>>, // (left from, arrived to)
)

data class WeekStat(val weekStart: LocalDate, val visits: Int, val legalVisits: Int)

data class MentorLink(val ruleId: String, val because: String, val counterpoint: Boolean = false)

data class TimeWindow(val light: Light, val text: String)

/** One thing to do: a short title, the detail, and the mentor rules behind it. */
data class Step(val title: String, val detail: String, val ruleIds: List<String> = emptyList())

data class Plan(
    val headline: String,
    val confidence: String,
    val bestCameraId: String?,
    val when_: List<String>,
    val conditions: List<String>,
    val avoid: List<String>,
    val where: List<String>,
    val wind: List<String>,
    val rhythm: List<String>,
    val gaps: List<String>,
    val scout: List<String>,
    val mentors: List<MentorLink>,
    val window: TimeWindow?,
    /** The ordered "do this" steps. */
    val actions: List<Step> = emptyList(),
    /** Is there a pattern yet, in plain words, and is it ready to hunt on. */
    val pattern: String = "",
    val patternReady: Boolean = false,
    /** When there's no pattern yet: the two sourced ways to play it. */
    val patient: Step? = null,
    val aggressive: Step? = null,
    /** Night-only so far: the data says stay out of his core for now. */
    val hold: Boolean = false,
)

data class BuckReport(
    val buckId: String,
    val photoCount: Int,
    val visits: List<Visit>,
    val firstSeen: Long?,
    val lastSeen: Long?,
    val cameras: List<CamStat>,
    val light: FactorStat?,
    val factors: List<FactorStat>,
    val findings: List<Finding>,
    val moves: List<Move>,
    val weeks: List<WeekStat>,
    val windVsTravel: Map<String, Int>,
    val testsRun: Int,
    val plan: Plan,
)

object BuckInsights {
    /** Photos of one buck at one camera this close together = one visit. Set from the app's settings. */
    @Volatile var visitGapSec = 10 * 60L
    const val MOVE_MAX_SEC = 18 * 3600L

    fun visits(photos: List<BuckPhoto>, cams: Map<String, CameraWx>): List<Visit> {
        val out = ArrayList<Visit>()
        for ((camId, ps) in photos.groupBy { it.cameraId }) {
            val sorted = ps.sortedBy { it.t }
            var group = ArrayList<BuckPhoto>()
            fun flush() {
                if (group.isEmpty()) return
                val heading = group.mapNotNull { it.heading }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val start = group.first().t
                out += Visit(camId, start, group.last().t, group.map { it.photoId }, heading, cams[camId]?.at?.invoke(start))
                group = ArrayList()
            }
            for (p in sorted) {
                if (group.isNotEmpty() && p.t - group.last().t > visitGapSec) flush()
                group += p
            }
            flush()
        }
        return out.sortedBy { it.start }
    }

    fun analyze(buckId: String, photos: List<BuckPhoto>, cams: Map<String, CameraWx>, zone: ZoneId, now: Long): BuckReport {
        val vs = visits(photos, cams)
        val camStats = cameraStats(vs, cams, zone, now)
        val light = factorStat(Factors.light, vs, cams)
        val factors = Factors.weather.mapNotNull { factorStat(it, vs, cams) }
        val all = listOfNotNull(light) + factors
        val findings = all.flatMap { fs ->
            fs.bins.filter { it.verdict != Verdict.NONE }.map { Finding(fs.key, fs.title, it, findingText(fs, it)) }
        }.sortedWith(compareBy<Finding> { it.bin.verdict.ordinal }.thenByDescending { if (it.bin.verdict == Verdict.AVOIDS) 1 / it.bin.lift.coerceAtLeast(0.01) else it.bin.lift })
        val tests = all.sumOf { fs -> fs.bins.count { it.visits >= 3 || fs.n * it.baseShare >= 3 } }
        val moves = moves(vs, cams)
        // Every week from his first visit through this one, empty weeks included, so the last
        // entry really is "this week" and a quiet week shows as a zero, not a gap.
        fun weekOf(t: Long) = Instant.ofEpochSecond(t).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val byWeek = vs.groupBy { weekOf(it.start) }
        val weeks = if (vs.isEmpty()) emptyList() else generateSequence(byWeek.keys.min()) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(maxOf(weekOf(now), byWeek.keys.max())) }
            .map { w -> byWeek[w].orEmpty().let { v -> WeekStat(w, v.size, v.count { it.f?.legal == true }) } }.toList()
        val wvt = windVsTravel(vs, cams)
        val report = BuckReport(
            buckId = buckId,
            photoCount = photos.size,
            visits = vs,
            firstSeen = vs.firstOrNull()?.start,
            lastSeen = vs.lastOrNull()?.start,
            cameras = camStats,
            light = light,
            factors = factors,
            findings = findings,
            moves = moves,
            weeks = weeks,
            windVsTravel = wvt,
            testsRun = tests,
            plan = Plan("", "", null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null),
        )
        return report.copy(plan = BuckPlanner.plan(report, cams, zone, now))
    }

    /** His share in each bin vs the comparable camera hours. */
    fun factorStat(factor: Factor, vs: List<Visit>, cams: Map<String, CameraWx>): FactorStat? {
        val known = vs.mapNotNull { v -> v.f?.let { f -> factor.of(f)?.let { b -> Triple(v, f, b) } } }
        val n = known.size
        if (n == 0) return null
        val k = IntArray(factor.bins.size)
        known.forEach { k[it.third]++ }

        // Baseline: per (camera, light) cell when stratified, per camera otherwise; each cell's
        // bin shares weighted by the buck's own visits in that cell.
        fun cellOf(camId: String, f: HourFeatures) = if (factor.stratify) "$camId|${f.light}" else camId
        val weights = known.groupingBy { cellOf(it.first.cameraId, it.second) }.eachCount()
        val base = DoubleArray(factor.bins.size)
        var wSum = 0.0
        var baseHours = 0
        for ((cell, w) in weights) {
            val camId = cell.substringBefore('|')
            val hours = cams[camId]?.baseline ?: continue
            val inCell = hours.filter { cellOf(camId, it) == cell }.mapNotNull { factor.of(it) }
            if (inCell.isEmpty()) continue
            baseHours += inCell.size
            val counts = IntArray(factor.bins.size)
            inCell.forEach { counts[it]++ }
            for (b in base.indices) base[b] += w * counts[b].toDouble() / inCell.size
            wSum += w
        }
        if (wSum == 0.0) return FactorStat(factor.key, factor.title, n, 0, factor.bins.mapIndexed { i, l -> BinStat(l, k[i], k[i].toDouble() / n, Double.NaN, Double.NaN, 1.0, 1.0, Verdict.NONE, i) })
        for (b in base.indices) base[b] /= wSum

        val bins = factor.bins.mapIndexed { i, label ->
            val share = k[i].toDouble() / n
            val p0 = base[i]
            val lift = if (p0 > 0) share / p0 else if (k[i] > 0) 99.0 else 1.0
            val pHigh = Binomial.upper(n, k[i], p0)
            val pLow = Binomial.lower(n, k[i], p0)
            val expected = n * p0
            val verdict = when {
                k[i] >= 3 && lift >= 1.5 && pHigh <= 0.05 -> Verdict.STRONG
                k[i] >= 2 && lift >= 1.3 && pHigh <= 0.20 -> Verdict.LEANING
                expected >= 3 && k[i] <= 0.4 * expected && pLow <= 0.10 -> Verdict.AVOIDS
                else -> Verdict.NONE
            }
            BinStat(label, k[i], share, p0, lift, pHigh, pLow, verdict, i)
        }
        return FactorStat(factor.key, factor.title, n, baseHours, bins)
    }

    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"
    private fun times(x: Double) = if (x >= 10) "${x.roundToInt()}x" else "${x.r1()}x"
    private fun pText(p: Double) = when {
        p < 0.001 -> "under 1 in 1,000"
        p < 0.01 -> "about 1 in ${(1 / p).roundToInt() / 10 * 10}"
        else -> "about 1 in ${(1 / p).roundToInt().coerceAtLeast(1)}"
    }

    private fun findingText(fs: FactorStat, b: BinStat): String {
        val against = if (fs.key == "light") "of the hours" else "of the hours at the same cameras and times of day"
        return when (b.verdict) {
            Verdict.AVOIDS ->
                "${b.visits} of ${fs.n} visits with ${b.label.lowercase()} (${pct(b.share)}), though it was ${pct(b.baseShare)} $against. " +
                    "Odds that few happen by luck: ${pText(b.pLow)}."
            else ->
                "${b.visits} of ${fs.n} visits with ${b.label.lowercase()} (${pct(b.share)}), vs ${pct(b.baseShare)} $against: " +
                    "${times(b.lift)} what chance gives. Odds that many by luck: ${pText(b.pHigh)}."
        }
    }

    private fun cameraStats(vs: List<Visit>, cams: Map<String, CameraWx>, zone: ZoneId, now: Long): List<CamStat> =
        vs.groupBy { it.cameraId }.mapNotNull { (camId, v) ->
            val cam = cams[camId]?.cam ?: CamInfo(camId, "Unknown camera", null, null, null)
            val days = v.map { Instant.ofEpochSecond(it.start).atZone(zone).toLocalDate() }.distinct().sorted()
            val gaps = days.zipWithNext { a, b -> (b.toEpochDay() - a.toEpochDay()).toDouble() }
            val median = gaps.median()
            val last = v.maxOf { it.start }
            val since = (Instant.ofEpochSecond(now).atZone(zone).toLocalDate().toEpochDay() - Instant.ofEpochSecond(last).atZone(zone).toLocalDate().toEpochDay()).toInt() // calendar days
            val travel = v.mapNotNull { travelBearing(it, cam) }
            val approach = circularMean(travel.map { (it + 180) % 360 })
            CamStat(
                camera = cam,
                visits = v.size,
                legalVisits = v.count { it.f?.legal == true },
                lastSeen = last,
                medianGapDays = median,
                daysSinceLast = since,
                due = median != null && since >= median,
                approachDeg = if (travel.size >= 2) approach else null,
                travelCounts = travel.groupingBy { compass8(it) }.eachCount(),
            )
        }.sortedWith(compareByDescending<CamStat> { it.legalVisits }.thenByDescending { it.visits })

    /** Compass direction he was walking, from the photo's left/right/toward/away and the lens facing. */
    fun travelBearing(v: Visit, cam: CamInfo): Double? {
        val facing = cam.facingDeg ?: return null
        val h = v.heading ?: return null
        // Camera looks along [facing]; a deer walking frame-left heads facing-90.
        val d = when (h) {
            Heading.LEFT -> facing - 90
            Heading.RIGHT -> facing + 90
            Heading.TOWARD -> facing + 180
            Heading.AWAY -> facing
        }
        return (d % 360 + 360) % 360
    }

    private fun windVsTravel(vs: List<Visit>, cams: Map<String, CameraWx>): Map<String, Int> {
        val out = linkedMapOf("Into the wind" to 0, "Crosswind" to 0, "Wind at his back" to 0)
        for (v in vs) {
            val cam = cams[v.cameraId]?.cam ?: continue
            val travel = travelBearing(v, cam) ?: continue
            val f = v.f ?: continue
            if ((f.windMph ?: 0.0) < 3) continue
            val from = f.windDir ?: continue
            val a = angleBetween(from, travel)
            val key = when { a <= 45 -> "Into the wind"; a >= 135 -> "Wind at his back"; else -> "Crosswind" }
            out[key] = out.getValue(key) + 1
        }
        return out
    }

    private fun moves(vs: List<Visit>, cams: Map<String, CameraWx>): List<Move> {
        val pairs = vs.zipWithNext().filter { (a, b) -> a.cameraId != b.cameraId && b.start - a.end in 0..MOVE_MAX_SEC }
        return pairs.groupBy { it.first.cameraId to it.second.cameraId }.map { (k, list) ->
            val a = cams[k.first]?.cam
            val b = cams[k.second]?.cam
            val geo = if (a?.lat != null && a.lon != null && b?.lat != null && b.lon != null) {
                distanceM(a.lat, a.lon, b.lat, b.lon) to bearing(a.lat, a.lon, b.lat, b.lon)
            } else null
            Move(
                fromId = k.first, toId = k.second, count = list.size,
                medianGapMin = (list.map { ((it.second.start - it.first.end) / 60).toDouble() }.median() ?: 0.0).roundToInt(),
                distanceM = geo?.first, bearingDeg = geo?.second,
                examples = list.map { it.first.end to it.second.start },
            )
        }.sortedByDescending { it.count }
    }
}

internal fun List<Double>.median(): Double? {
    if (isEmpty()) return null
    val s = sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

internal fun circularMean(degs: List<Double>): Double? {
    if (degs.isEmpty()) return null
    val x = degs.sumOf { cos(Math.toRadians(it)) }
    val y = degs.sumOf { sin(Math.toRadians(it)) }
    if (sqrt(x * x + y * y) / degs.size < 0.3) return null // spread all around: no single direction
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}

fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dl = Math.toRadians(lon2 - lon1)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
