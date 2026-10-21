package com.kairos.engine.bucks

import com.kairos.engine.moonInfo
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Turns a camera's raw hourly weather into the features a buck's sightings are judged on:
 * light period, pressure trend, the day's swing, fronts, rain, temperature vs normal, moon, and
 * the calendar phase. The same features are built for EVERY hour the camera was up (the
 * baseline) and for each sighting, so "he shows on a west wind" is always measured against
 * how often a west wind actually blew there.
 */

/** One hour of model weather at a camera. [t] = start of the hour, unix seconds UTC. null = no data. */
data class WxHour(
    val t: Long,
    val tempF: Double?,
    val windMph: Double?,
    val windDir: Double?,     // FROM, degrees
    val gustMph: Double?,
    val pressureHpa: Double?, // sea level
    val cloudPct: Double?,
    val precipIn: Double?,    // in the hour
    val source: String = "HRRR",
    // Forecast-only extras (null on history hours).
    val feelsF: Double? = null,
    val humidityPct: Double? = null,
    val precipProb: Double? = null, // %, chance of precipitation in the hour
    val snowIn: Double? = null,
    val code: Int? = null,          // WMO weather code
    /** The second model's wind (ECMWF IFS), for the "models agree?" check. */
    val altDir: Double? = null,
    val altMph: Double? = null,
)

/** Light period by the sun on that day, not the clock. Legal hours in Maine = sunrise-30 to sunset+30. */
enum class Light(val label: String) {
    DAWN("Dawn"), MORNING("Morning"), MIDDAY("Midday"), AFTERNOON("Afternoon"), EVENING("Evening"), NIGHT("Night");
}

enum class FrontState(val label: String) { AFTER_COLD_FRONT("0-36 h after a cold front"), PRESSURE_FALLING("Pressure falling (weather coming)"), NONE("No front") }

enum class RainState(val label: String) { RAINING("Raining"), JUST_ENDED("Rain ended < 6 h"), DRY("Dry") }

/**
 * The hunter's own rut dates. Default = Reiden's read for his ground (rut Nov 6-18, 2026-10-07);
 * pre-rut = "late October into early November" (DEER_KNOWLEDGE_BASE.md, Pre-rut) = Oct 20 to the
 * day before the rut. Set from the Bucks screen; region-aware, not hard-wired to Maine.
 */
data class RutCalendar(val rutStart: MonthDay = MonthDay.of(11, 6), val rutEnd: MonthDay = MonthDay.of(11, 18), val preRutStart: MonthDay = MonthDay.of(10, 20)) {
    fun phase(d: LocalDate): SeasonPhase {
        val md = MonthDay.from(d)
        return when {
            d.monthValue in 6..8 -> SeasonPhase.SUMMER
            d.monthValue in 2..5 -> SeasonPhase.WINTER
            d.monthValue == 12 || d.monthValue == 1 -> SeasonPhase.POST_RUT
            md.isBefore(preRutStart) -> SeasonPhase.EARLY
            md.isBefore(rutStart) -> SeasonPhase.PRE_RUT
            !md.isAfter(rutEnd) -> SeasonPhase.RUT
            else -> SeasonPhase.POST_RUT
        }
    }

    fun label(p: SeasonPhase): String = when (p) {
        SeasonPhase.SUMMER -> "Summer"
        SeasonPhase.EARLY -> "Early season (to ${fmt(preRutStart.minusDays())})"
        SeasonPhase.PRE_RUT -> "Pre-rut (${fmt(preRutStart)} - ${fmt(rutStart.minusDays())})"
        SeasonPhase.RUT -> "Rut (${fmt(rutStart)} - ${fmt(rutEnd)})"
        SeasonPhase.POST_RUT -> "Post-rut (after ${fmt(rutEnd)})"
        SeasonPhase.WINTER -> "Late winter/spring"
    }

    private fun MonthDay.minusDays(): MonthDay = MonthDay.from(atYear(2025).minusDays(1))
    private fun fmt(m: MonthDay) = "${java.time.Month.of(m.monthValue).name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${m.dayOfMonth}"
}

enum class SeasonPhase { SUMMER, EARLY, PRE_RUT, RUT, POST_RUT, WINTER }

/** Everything known about one moment at one camera. Raw values are kept for the charts. */
data class HourFeatures(
    val t: Long,
    val light: Light,
    val legal: Boolean,
    val minsFromSunrise: Int?,
    val minsToSunset: Int?,
    val tempF: Double?,
    val windMph: Double?,
    val windDir: Double?,
    val gustMph: Double?,
    val pressureHpa: Double?,
    val cloudPct: Double?,
    val precipIn: Double?,
    val pressTrend3h: Double?,  // hPa change over the last 3 h
    val tempDay24: Double?,     // mean of the last 24 h minus the 24 h before (°F)
    val windDay24: Double?,     // same, mph
    val front: FrontState?,
    val rain: RainState?,
    val departureF: Double?,    // the local day's (high+low)/2 departure from the 30-yr normal
    val moonIllum: Double,
    val phase: SeasonPhase,
    val source: String,
)

/** Sun position of a moment: light period, legal-hours flag, and minutes from sunrise / to sunset. */
data class LightAt(val light: Light, val legal: Boolean, val minsFromSunrise: Int?, val minsToSunset: Int?)

fun lightAt(t: Long, lat: Double, lon: Double, zone: ZoneId): LightAt {
    val date = Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
    val sun = Sun.day(date, lat, lon)
    val sr = sun.sunrise ?: return LightAt(Light.NIGHT, false, null, null)
    val ss = sun.sunset ?: return LightAt(Light.NIGHT, false, null, null)
    val noon = sun.solarNoon
    val m = 60L
    val light = when {
        t < sr - 30 * m || t > ss + 30 * m -> Light.NIGHT
        t < sr + 60 * m -> Light.DAWN
        t < noon - 60 * m -> Light.MORNING
        t < noon + 90 * m -> Light.MIDDAY
        t < ss - 120 * m -> Light.AFTERNOON
        else -> Light.EVENING
    }
    return LightAt(light, light != Light.NIGHT, ((t - sr) / 60).toInt(), ((ss - t) / 60).toInt())
}

/** Builds [HourFeatures] for a camera's hourly series. */
class FeatureBuilder(
    private val lat: Double,
    private val lon: Double,
    private val zone: ZoneId,
    private val rut: RutCalendar = RutCalendar(),
    /** The 30-yr normal (lowF, highF) for a date at this camera, or null if unknown. */
    private val normalFor: (LocalDate) -> Pair<Double, Double>?,
) {
    fun build(hours: List<WxHour>): List<HourFeatures> {
        if (hours.isEmpty()) return emptyList()
        val sorted = hours.sortedBy { it.t }
        val byT = HashMap<Long, Int>(sorted.size * 2).apply { sorted.forEachIndexed { i, h -> put(h.t, i) } }
        fun at(t: Long): WxHour? = byT[t]?.let { sorted[it] }
        val fronts = coldFrontTimes(sorted, ::at)
        val departure = dailyDepartures(sorted)
        return sorted.map { h -> features(h, ::at, fronts, departure) }
    }

    /** Features for an exact moment (a sighting): weather from the nearest hour, light from the exact time. */
    fun atMoment(t: Long, built: List<HourFeatures>): HourFeatures? {
        if (built.isEmpty()) return null
        val hour = ((t + 1800) / 3600) * 3600
        val i = built.binarySearchBy(hour) { it.t }
        val f = if (i >= 0) built[i] else return null
        val l = lightAt(t, lat, lon, zone)
        return f.copy(t = t, light = l.light, legal = l.legal, minsFromSunrise = l.minsFromSunrise, minsToSunset = l.minsToSunset)
    }

    private fun features(
        h: WxHour,
        at: (Long) -> WxHour?,
        fronts: LongArray,
        departure: Map<LocalDate, Double>,
    ): HourFeatures {
        val l = lightAt(h.t + 1800, lat, lon, zone) // classify an hour by its middle
        val p3 = at(h.t - 3 * 3600)?.pressureHpa
        val trend = if (h.pressureHpa != null && p3 != null) h.pressureHpa - p3 else null
        val tempDay = dayMeanChange(h.t, at) { it.tempF }
        val windDay = dayMeanChange(h.t, at) { it.windMph }
        val front = frontState(h, at, fronts)
        val rain = rainState(h, at)
        val date = Instant.ofEpochSecond(h.t).atZone(zone).toLocalDate()
        return HourFeatures(
            t = h.t, light = l.light, legal = l.legal,
            minsFromSunrise = l.minsFromSunrise, minsToSunset = l.minsToSunset,
            tempF = h.tempF, windMph = h.windMph, windDir = h.windDir, gustMph = h.gustMph,
            pressureHpa = h.pressureHpa, cloudPct = h.cloudPct, precipIn = h.precipIn,
            pressTrend3h = trend, tempDay24 = tempDay, windDay24 = windDay,
            front = front, rain = rain, departureF = departure[date],
            moonIllum = moonInfo(date).illum, phase = rut.phase(date), source = h.source,
        )
    }

    /** Mean of the last 24 h minus the mean of the 24 h before. Needs 20+ hours in each half. */
    private fun dayMeanChange(t: Long, at: (Long) -> WxHour?, v: (WxHour) -> Double?): Double? {
        fun mean(from: Int, to: Int): Double? {
            val xs = (from..to).mapNotNull { k -> at(t - k * 3600L)?.let(v) }
            return if (xs.size >= 20) xs.average() else null
        }
        val recent = mean(0, 23) ?: return null
        val before = mean(24, 47) ?: return null
        return recent - before
    }

    private fun rainState(h: WxHour, at: (Long) -> WxHour?): RainState? {
        val p = h.precipIn ?: return null
        if (p >= 0.01) return RainState.RAINING
        val recent = (1..6).mapNotNull { at(h.t - it * 3600L)?.precipIn }
        return if (recent.any { it >= 0.01 }) RainState.JUST_ENDED else RainState.DRY
    }

    private fun frontState(h: WxHour, at: (Long) -> WxHour?, fronts: LongArray): FrontState? {
        if (h.pressureHpa == null) return null
        // The most recent cold-front passage at or before this hour.
        val i = fronts.binarySearch(h.t).let { if (it >= 0) it else -it - 2 }
        if (i >= 0 && h.t - fronts[i] <= 36 * 3600L) return FrontState.AFTER_COLD_FRONT
        val p12 = at(h.t - 12 * 3600L)?.pressureHpa ?: return null
        return if (h.pressureHpa - p12 <= -3.0) FrontState.PRESSURE_FALLING else FrontState.NONE
    }

    /**
     * Cold-front passages: a pressure low point (lowest within ±6 h) followed by a 3+ hPa rise
     * within 12 h, with the next 24 h averaging 4+ °F colder than the 24 h before. Pressure,
     * temperature and the 24-h means are each also judged on their own, so a weak front call
     * never stands alone.
     */
    private fun coldFrontTimes(sorted: List<WxHour>, at: (Long) -> WxHour?): LongArray {
        val out = ArrayList<Long>()
        for (h in sorted) {
            val p = h.pressureHpa ?: continue
            val window = (-6..6).mapNotNull { at(h.t + it * 3600L)?.pressureHpa }
            if (window.size < 11 || window.min() < p) continue
            val p12 = at(h.t + 12 * 3600L)?.pressureHpa ?: continue
            if (p12 - p < 3.0) continue
            val after = (1..24).mapNotNull { at(h.t + it * 3600L)?.tempF }
            val before = (1..24).mapNotNull { at(h.t - it * 3600L)?.tempF }
            if (after.size < 20 || before.size < 20) continue
            if (after.average() > before.average() - 4.0) continue
            if (out.isNotEmpty() && h.t - out.last() < 24 * 3600L) continue // one passage, one mark
            out += h.t
        }
        return out.toLongArray()
    }

    /** Each complete local day's departure from normal: ((low-nLow)+(high-nHigh))/2. */
    private fun dailyDepartures(sorted: List<WxHour>): Map<LocalDate, Double> {
        val byDay = sorted.groupBy { Instant.ofEpochSecond(it.t).atZone(zone).toLocalDate() }
        val out = HashMap<LocalDate, Double>()
        for ((d, hs) in byDay) {
            val temps = hs.mapNotNull { it.tempF }
            if (temps.size < 22) continue
            val n = normalFor(d) ?: continue
            out[d] = ((temps.min() - n.first) + (temps.max() - n.second)) / 2.0
        }
        return out
    }
}

/** 8-point compass name for a FROM direction. */
fun compass8(deg: Double): String {
    val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
}

/** Smallest angle between two bearings, 0..180. */
fun angleBetween(a: Double, b: Double): Double {
    val d = abs(((a - b) % 360 + 360) % 360)
    return if (d > 180) 360 - d else d
}

internal fun Double.r1(): Double = (this * 10).roundToLong() / 10.0
