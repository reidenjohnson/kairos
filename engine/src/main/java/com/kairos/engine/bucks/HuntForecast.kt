package com.kairos.engine.bucks

import java.time.Instant
import java.time.ZoneId

/**
 * Is a forecast hour's wind trustworthy? Open-Meteo's best match (NOAA HRRR for ~42 h, then GFS;
 * checked 2026-10-13) against ECMWF IFS, a separate model, for the same hour. When two independent
 * models disagree, nobody knows that hour's wind yet.
 *
 * AGREE_DEG / CALM_MPH are this app's working definitions (shown in the app), not published thresholds.
 */
/** In order of how sure the wind is (ranking uses the order). */
enum class ModelCheck(val label: String) { AGREE("Models agree"), ONE("One model"), SPLIT("Models split") }

object WindCheck {
    const val AGREE_DEG = 45.0
    const val CALM_MPH = 4.0

    fun of(h: WxHour?): ModelCheck {
        val d = h?.windDir; val m = h?.windMph; val ad = h?.altDir; val am = h?.altMph
        if (d == null || m == null || ad == null || am == null) return ModelCheck.ONE
        if (m < CALM_MPH && am < CALM_MPH) return ModelCheck.AGREE
        // One model calls it calm (no direction to match on) and the other a real wind: the hour
        // would match his calm visits on one and his windy ones on the other. Seen 10/14 dawn:
        // HRRR 0.2 mph vs ECMWF NW 4.6, which the angle alone (39°) had called agreement.
        if (minOf(m, am) < HisWinds.CALM_MPH) return ModelCheck.SPLIT
        return if (angleBetween(d, ad) <= AGREE_DEG) ModelCheck.AGREE else ModelCheck.SPLIT
    }
}

/** The wind at one of his visits (HRRR at that moment at that camera). [dir] null = calm/unknown. */
data class VisitWind(val t: Long, val cameraId: String, val dir: Double?, val mph: Double, val tempF: Double?)

/**
 * "His winds": the winds he has actually moved on. A forecast hour matches a visit when its wind
 * comes from within [MATCH_DEG] of that visit's (one 8-point compass sector is 45° wide, so ±30°
 * keeps a neighbor's edge in without taking the whole neighbor), or when both are near calm.
 * A working definition, shown in the app.
 */
object HisWinds {
    const val MATCH_DEG = 30.0
    const val CALM_MPH = 3.0

    fun of(r: BuckReport): List<VisitWind> = r.visits.mapNotNull { v ->
        val f = v.f ?: return@mapNotNull null
        val mph = f.windMph ?: return@mapNotNull null
        VisitWind(v.start, v.cameraId, f.windDir?.takeIf { mph >= CALM_MPH }, mph, f.tempF)
    }

    fun matching(dir: Double?, mph: Double?, his: List<VisitWind>): List<VisitWind> {
        if (mph == null) return emptyList()
        val calm = mph < CALM_MPH
        return his.filter { w ->
            when {
                calm || w.dir == null -> calm && w.dir == null
                dir == null -> false
                else -> angleBetween(w.dir, dir) <= MATCH_DEG
            }
        }
    }
}

enum class SitKind(val label: String, val light: Light) { MORNING("Morning", Light.DAWN), EVENING("Evening", Light.EVENING) }

/**
 * One upcoming sit: the first 3 h of shooting light (sunrise-30 min on) or the last 3 h (to
 * sunset+30 min). Scored on its best hour; every number shown comes from that same hour.
 */
data class HuntWindow(
    val start: Long,
    val end: Long,
    val kind: SitKind,
    val score: Int,
    /** Start of the hour the score and the wind/temperature come from. */
    val bestHour: Long,
    val matches: List<String>,
    val against: List<String>,
    /** His visits on this hour's wind. */
    val hisWind: List<VisitWind>,
    /** His visits with a known wind (the "of N"). */
    val hisWindOf: Int,
    val windDir: Double?,
    val windMph: Double?,
    val gustMph: Double?,
    val tempF: Double?,
    val precipProb: Double?,
    val check: ModelCheck,
    val altDir: Double?,
    val windOk: Boolean?,
    val mentors: List<MentorHit> = emptyList(),
    /** Every point in [score], each with what earned it (sums to [score]; shown in the app). */
    val points: List<Pair<String, Int>> = emptyList(),
) {
    val light: Light get() = kind.light
}

/**
 * Scores every morning and evening sit in the forecast (16 days) at one camera. Points:
 * his own marks (Strong +2, Leaning +1, Rarely -2) and his usual time of day (+1), each times his
 * weight (1 under 5 visits, 2 under 15, else 3); his wind: +3 when the hour's wind is one he has
 * moved on (as much as a call every mentor agrees on), +1 per extra visit on it (max +5), only when
 * the two models don't split on the wind; the mentors' weather calls by consensus (1-3 each).
 * His wind leads on purpose (Reiden, 2026-10-13): "if they approach at NW winds, when is the next
 * good NW wind?"
 */
object HuntWindows {
    private const val SIT_SEC = 3 * 3600L
    private const val LIGHT_SEC = 30 * 60L
    const val HIS_WIND_PTS = 3

    /**
     * [approachDeg] = where the buck comes from at this camera, if known (for the scent check).
     * [yourWind] = the stand's wind sectors he picked ([StandWind]); when set, it is the scent check.
     */
    fun upcoming(r: BuckReport, forecast: List<HourFeatures>, raw: List<WxHour>, now: Long, cam: CamInfo?, approachDeg: Double?, zone: ZoneId, yourWind: Set<Int> = emptySet()): List<HuntWindow> {
        val lat = cam?.lat ?: return emptyList()
        val lon = cam.lon ?: return emptyList()
        val hours = forecast.filter { it.t + 3600 > now }.sortedBy { it.t }
        if (hours.isEmpty()) return emptyList()
        val rawAt = raw.associateBy { it.t }
        val his = HisWinds.of(r)
        val marks = r.findings.filter { it.factorKey != "phase" }
        val hisLights = r.visits.mapNotNull { it.f }.filter { it.legal }.map { it.light }.toSet()
        val factorsByKey = (listOf(Factors.light) + Factors.weather).associateBy { it.key }
        val w = when { r.visits.size < 5 -> 1; r.visits.size < 15 -> 2; else -> 3 }
        val safe = approachDeg

        class Scored(val h: HourFeatures, val s: Int, val m: List<String>, val a: List<String>, val hw: List<VisitWind>, val check: ModelCheck, val ok: Boolean?, val pts: List<Pair<String, Int>>)
        fun scoreOf(h: HourFeatures): Scored {
            val pts = ArrayList<Pair<String, Int>>()
            val m = ArrayList<String>(); val a = ArrayList<String>()
            for (f in marks) {
                val bin = factorsByKey[f.factorKey]?.of(h) ?: continue
                if (bin != f.bin.index) continue
                when (f.bin.verdict) {
                    Verdict.STRONG -> { pts += "fits him: ${f.bin.label}" to 2 * w; m += f.bin.label }
                    Verdict.LEANING -> { pts += "fits him: ${f.bin.label}" to w; m += f.bin.label }
                    Verdict.AVOIDS -> { pts += "not his: ${f.bin.label}" to -2 * w; a += f.bin.label }
                    Verdict.NONE -> {}
                }
            }
            if (h.light in hisLights) { pts += "his usual time (${h.light.label.lowercase()})" to w; m += "his usual time (${h.light.label.lowercase()})" }
            val check = WindCheck.of(rawAt[h.t])
            val hw = if (check == ModelCheck.SPLIT) emptyList() else HisWinds.matching(h.windDir, h.windMph, his)
            if (hw.isNotEmpty()) pts += "his wind (${hw.size} visit${if (hw.size == 1) "" else "s"})" to HIS_WIND_PTS + minOf(hw.size - 1, 2)
            MentorWeather.hits(h).distinctBy { it.ruleId }.forEach { pts += it.label to MentorWeather.points(it) }
            // Scent: his own pick for the stand, else the buck's approach. Not on a split hour.
            val ok = when {
                check == ModelCheck.SPLIT -> null
                yourWind.isNotEmpty() -> StandWind.fits(yourWind, h.windDir, h.windMph)
                safe != null && h.windDir != null && (h.windMph ?: 0.0) >= HisWinds.CALM_MPH -> angleBetween(h.windDir, safe) <= 67.5
                else -> null
            }
            if (ok == true && yourWind.isNotEmpty()) { pts += "your wind for this stand" to StandWind.PTS; m += "your wind for this stand" }
            return Scored(h, pts.sumOf { it.second }, m, a, hw, check, ok, pts)
        }

        val first = Instant.ofEpochSecond(hours.first().t).atZone(zone).toLocalDate()
        val last = Instant.ofEpochSecond(hours.last().t).atZone(zone).toLocalDate()
        val out = ArrayList<HuntWindow>()
        var d = first
        while (!d.isAfter(last)) {
            val sun = Sun.day(d, lat, lon)
            val sr = sun.sunrise; val ss = sun.sunset
            if (sr != null && ss != null) {
                for (kind in SitKind.entries) {
                    val start = if (kind == SitKind.MORNING) sr - LIGHT_SEC else ss + LIGHT_SEC - SIT_SEC
                    val end = start + SIT_SEC
                    if (end <= now) continue
                    val inSit = hours.filter { it.t < end && it.t + 3600 > start }
                    // A sit needs its whole span in the forecast, or it's scored on a fragment.
                    if (inSit.isEmpty() || inSit.first().t > start || inSit.last().t + 3600 < end) continue
                    // An hour on a wrong wind is never the sit's best while another hour isn't.
                    val best = inSit.map(::scoreOf).maxWithOrNull(compareBy<Scored> { it.ok != false }.thenBy { it.s }.thenBy { -it.h.t })!!
                    val h = best.h
                    val rw = rawAt[h.t]
                    out += HuntWindow(
                        start = maxOf(start, now), end = end, kind = kind, score = best.s, bestHour = h.t,
                        matches = best.m.distinct(), against = best.a.distinct(),
                        hisWind = best.hw, hisWindOf = his.size,
                        windDir = h.windDir, windMph = h.windMph, gustMph = h.gustMph, tempF = h.tempF,
                        precipProb = rw?.precipProb, check = best.check, altDir = rw?.altDir,
                        windOk = best.ok,
                        points = best.pts,
                        mentors = MentorWeather.hits(h).distinctBy { it.ruleId },
                    )
                }
            }
            d = d.plusDays(1)
        }
        return out
    }

    /** Best first: a wind that blows your scent the wrong way goes last; then score, the surer wind, sooner. */
    fun ranked(ws: List<HuntWindow>): List<HuntWindow> = ws.sortedWith(
        compareBy<HuntWindow> { it.windOk == false }
            .thenByDescending { it.score }
            .thenBy { it.check.ordinal }
            .thenBy { it.start },
    )
}

/**
 * One deer visit at a camera, any deer (named bucks, young bucks, does, "can't tell"): its wind
 * and its clock time. Photos of the same camera close together are one visit, so a burst of 10
 * frames counts once.
 */
data class DeerVisit(val t: Long, val photoIds: List<String>, val labels: Set<String>, val dir: Double?, val mph: Double, val minuteOfDay: Int, val legal: Boolean = true)

/**
 * What a camera's own photos say about a forecast hour or sit: which visits came on that wind, and
 * which also came at that time of day (within [TIME_MIN] minutes of the clock, either way). Same
 * wind rule as [HisWinds]. For "when is this stand good", whatever deer it caught.
 */
object CamActivity {
    const val TIME_MIN = 60

    fun windMatches(dir: Double?, mph: Double?, visits: List<DeerVisit>): List<DeerVisit> {
        val asWind = visits.map { VisitWind(it.t, "", it.dir, it.mph, null) }
        val hit = HisWinds.matching(dir, mph, asWind).map { it.t }.toSet()
        return visits.filter { it.t in hit }
    }

    /** Minutes between two clock times, the short way around midnight. */
    fun clockGap(a: Int, b: Int): Int { val d = Math.abs(a - b) % 1440; return minOf(d, 1440 - d) }

    /** Visits on this hour's wind that also came within [TIME_MIN] of this hour's middle. */
    fun windAndTime(h: WxHour, zone: ZoneId, visits: List<DeerVisit>): List<DeerVisit> {
        val mid = Instant.ofEpochSecond(h.t + 1800).atZone(zone).let { it.hour * 60 + it.minute }
        return windMatches(h.windDir, h.windMph, visits).filter { clockGap(it.minuteOfDay, mid) <= TIME_MIN }
    }

    /**
     * For a sit: visits whose wind matches any sit hour, and visits whose clock time falls inside
     * the sit (±30 min) on the wind forecast for that same time. Split-model hours don't count.
     */
    fun forSit(start: Long, end: Long, hours: List<WxHour>, zone: ZoneId, visits: List<DeerVisit>): Pair<List<DeerVisit>, List<DeerVisit>> {
        val inSit = hours.filter { it.t < end && it.t + 3600 > start && WindCheck.of(it) != ModelCheck.SPLIT }
        val wind = inSit.flatMap { windMatches(it.windDir, it.windMph, visits) }.distinctBy { it.t }
        fun clock(t: Long) = Instant.ofEpochSecond(t).atZone(zone).let { it.hour * 60 + it.minute }
        val s = clock(start - 1800); val e = clock(end + 1800)
        val both = visits.filter { v ->
            val inside = if (s <= e) v.minuteOfDay in s..e else v.minuteOfDay >= s || v.minuteOfDay <= e
            if (!inside) return@filter false
            // The forecast hour at the visit's clock time on the sit's day.
            val h = inSit.minByOrNull { clockGap(clock(it.t + 1800), v.minuteOfDay) } ?: return@filter false
            windMatches(h.windDir, h.windMph, listOf(v)).isNotEmpty()
        }
        return wind to both
    }
}

/** A future hour that looks like a photo's moment, with how close each piece is. */
data class SimilarHour(
    val h: WxHour,
    val legal: Boolean,
    /** Degrees between the photo's wind and this hour's (null = both calm). */
    val windDiff: Double?,
    val clockGapMin: Int,
    val tempDiff: Double?,
    val mphDiff: Double?,
    /** Which sit this hour falls in (first or last 3 h of shooting light), if any. */
    val sit: SitKind? = null,
)

/**
 * "Show me similar weather": future hours on the photo's wind (same rule as [HisWinds]: within
 * 30°, or both calm; models not split), closest first. Closeness = wind-direction gap (per 15°)
 * + clock-time gap (per hour) + temperature gap (per 10°F) + wind-speed gap (per 5 mph): a stated
 * ordering, every gap shown. One hour per day, so the options are different days. [sameTime]
 * keeps hours within 90 min of the photo's clock time; [huntable] keeps the sit hours (first or
 * last 3 h of shooting light, as [HuntWindows] defines a sit), ranked without the clock.
 */
object SimilarWeather {
    const val SAME_TIME_MIN = 90

    data class Result(val sameTime: List<SimilarHour>, val huntable: List<SimilarHour>, val anyOnWind: Int)

    fun find(dir: Double?, mph: Double?, tempF: Double?, minuteOfDay: Int, forecast: List<HourFeatures>, raw: List<WxHour>, now: Long, zone: ZoneId, take: Int = 3): Result {
        val rawAt = raw.associateBy { it.t }
        val photo = listOf(VisitWind(0, "", dir?.takeIf { (mph ?: 0.0) >= HisWinds.CALM_MPH }, mph ?: 0.0, tempF))
        // Gaps are measured from the hour's middle, so an hour whose middle has passed is no longer an option.
        val cands = forecast.filter { it.t + 1800 > now }.mapNotNull { f ->
            val r = rawAt[f.t] ?: return@mapNotNull null
            if (WindCheck.of(r) == ModelCheck.SPLIT) return@mapNotNull null
            if (HisWinds.matching(r.windDir, r.windMph, photo).isEmpty()) return@mapNotNull null
            val mid = Instant.ofEpochSecond(f.t + 1800).atZone(zone).let { it.hour * 60 + it.minute }
            SimilarHour(
                r, f.legal,
                if (dir != null && r.windDir != null && (mph ?: 0.0) >= HisWinds.CALM_MPH) angleBetween(dir, r.windDir) else null,
                CamActivity.clockGap(mid, minuteOfDay),
                if (tempF != null && r.tempF != null) r.tempF - tempF else null,
                if (mph != null && r.windMph != null) r.windMph - mph else null,
                sit = when {
                    !f.legal -> null
                    (f.minsFromSunrise ?: Int.MAX_VALUE) in -30 until 150 -> SitKind.MORNING
                    (f.minsToSunset ?: Int.MAX_VALUE) in -30 until 150 -> SitKind.EVENING
                    else -> null
                },
            )
        }
        fun weather(s: SimilarHour) = (s.windDiff ?: 0.0) / 15.0 + Math.abs(s.tempDiff ?: 0.0) / 10.0 + Math.abs(s.mphDiff ?: 0.0) / 5.0
        fun dist(s: SimilarHour) = weather(s) + s.clockGapMin / 60.0
        fun bestPerDay(xs: List<SimilarHour>, key: (SimilarHour) -> Double) = xs
            .groupBy { Instant.ofEpochSecond(it.h.t).atZone(zone).toLocalDate() }.values
            .map { day -> day.minByOrNull(key)!! }
            .sortedBy(key).take(take)
        return Result(
            sameTime = bestPerDay(cands.filter { it.clockGapMin <= SAME_TIME_MIN }, ::dist),
            // Huntable: sit hours, closeness without the clock (a different time of day by design).
            huntable = bestPerDay(cands.filter { it.sit != null }, ::weather),
            anyOnWind = cands.size,
        )
    }
}
