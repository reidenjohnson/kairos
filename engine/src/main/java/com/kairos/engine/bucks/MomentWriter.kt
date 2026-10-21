package com.kairos.engine.bucks

import com.kairos.engine.moonInfo
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The story of one photo, written from what was actually true then: the minutes from sunrise or
 * sunset, where this visit sits in HIS history (first daylight one? same clock time as last
 * week?), the weather that stood out, which side of the camera was downwind, and what to do with
 * it. Every "do this" comes from his own data or a mentor rule / research source (ids attached);
 * herd habits are labeled as such. Nothing is padded: a line only appears when its fact exists.
 */

/**
 * How the app wants numbers shown (the user's units). Inputs are always °F / mph / inches / hPa;
 * thresholds stay in the units their sources state and only their display converts.
 */
interface UnitFmt {
    fun temp(f: Double): String
    fun tempDelta(f: Double): String
    fun speed(mph: Double): String
    fun compass(deg: Double): String
    fun clock(t: Long, zone: ZoneId): String
    /** "°F" or "°C". */
    val tempUnit: String
    /** A temperature, number only, in [tempUnit]. */
    fun tempNum(f: Double): Int
    /** A temperature difference, number only (9°F = 5°C). */
    fun tempDeltaNum(f: Double): Int
    /** "mph", "km/h", "kt" or "m/s". */
    val speedUnit: String
    fun speedNum(mph: Double): Int
    /** A rain amount with its unit: "0.10 in" / "2.5 mm". */
    fun rain(inches: Double): String
    /** A pressure change with its unit, no sign: "2 hPa" / "0.06 inHg". */
    fun pressAmt(hpa: Double): String
}

/** °F, mph, inches, hPa, 12-hour clock: the engine's default and the tests'. */
object Imperial : UnitFmt {
    private val HM = DateTimeFormatter.ofPattern("h:mm a")
    override fun temp(f: Double) = "${f.roundToInt()}°F"
    override fun tempDelta(f: Double) = "${f.roundToInt()}°"
    override fun speed(mph: Double) = "${mph.roundToInt()} mph"
    override fun compass(deg: Double) = compass8(deg)
    override fun clock(t: Long, zone: ZoneId): String = HM.format(Instant.ofEpochSecond(t).atZone(zone))
    override val tempUnit = "°F"
    override fun tempNum(f: Double) = f.roundToInt()
    override fun tempDeltaNum(f: Double) = f.roundToInt()
    override val speedUnit = "mph"
    override fun speedNum(mph: Double) = mph.roundToInt()
    override fun rain(inches: Double) = "%.2f in".format(inches)
    override fun pressAmt(hpa: Double) = if (hpa == hpa.roundToInt().toDouble()) "${hpa.roundToInt()} hPa" else "%.1f hPa".format(hpa)
}

data class StoryLine(val label: String, val text: String)

data class MomentStory(
    /** One bold line: "38 min before sunrise". */
    val headline: String,
    /** Under it: "His 2nd visit · first in daylight". */
    val sub: String,
    /** The moment, him, the weather, the wind at this camera. */
    val lines: List<StoryLine>,
    /** Ordered, specific actions with their sources. */
    val steps: List<Step>,
)

/** A wind reading near the photo (from the 15-minute series), for "the wind swung" lines. */
data class WindAt(val t: Long, val dir: Double?, val mph: Double?)

object MomentWriter {
    private val DAY = DateTimeFormatter.ofPattern("EEE MMM d")

    fun write(
        f: HourFeatures,
        t: Long,
        zone: ZoneId,
        camId: String,
        camName: String,
        buckName: String?,
        pool: Boolean,
        /** This buck's visits, every camera, any order. Empty for a doe / unsorted photo. */
        visits: List<Visit>,
        rut: RutCalendar,
        u: UnitFmt,
        /** Wind every 15 min around the photo (optional); finer than the hour in [f]. */
        nearby: List<WindAt> = emptyList(),
    ): MomentStory {
        val who = when {
            buckName == null -> "this deer"
            pool -> "this young buck"
            else -> buckName
        }
        val lines = ArrayList<StoryLine>()
        val steps = ArrayList<Step>()

        // ---- the moment against the sun
        val (headline, lightLine) = lightStory(f)
        lightLine?.let { lines += StoryLine("Light", it) }

        // ---- where this sits in his history
        val sorted = visits.sortedBy { it.start }
        val idx = sorted.indexOfFirst { t in it.start..maxOf(it.end, it.start) }.takeIf { it >= 0 }
            ?: sorted.indexOfLast { it.start <= t }.takeIf { it >= 0 }
        val n = sorted.size
        val daylight = sorted.count { it.f?.legal == true }
        val atCam = sorted.count { it.cameraId == camId }
        val subBits = ArrayList<String>()
        if (n > 0 && idx != null) {
            val thisLegal = f.legal
            subBits += "${ordinal(idx + 1)} of $n visits"
            val firstDaylight = thisLegal && sorted.take(idx).none { it.f?.legal == true }
            if (firstDaylight) subBits += "his first in shooting light"
            val prev = sorted.getOrNull(idx - 1)
            val next = sorted.getOrNull(idx + 1)
            val hist = buildString {
                append(
                    when {
                        n == 1 -> "This is ${possessive(who)} only visit so far"
                        atCam == n -> "All $n of ${possessive(who)} visits are at $camName"
                        else -> "$atCam of ${possessive(who)} $n visits ${if (atCam == 1) "is" else "are"} at $camName"
                    },
                )
                append(". ")
                when {
                    daylight == 0 -> append("Every one so far is outside shooting light.")
                    daylight == n -> append("Every one is in shooting light.")
                    else -> append("$daylight in shooting light.")
                }
                prev?.let { p ->
                    val gap = gapText(t - p.start)
                    append(" Before this: $gap earlier, ${u.clock(p.start, zone)}" + (if (p.cameraId != camId) " at another camera." else "."))
                    sameClock(p.start, t, zone)?.let { append(" Within $it min of the same clock time: a route he repeats.") }
                }
                next?.let { x -> append(" Next: ${gapText(x.start - t)} later.") }
            }
            lines += StoryLine("His history", hist)
        }

        // ---- weather that stood out
        val wx = ArrayList<String>()
        f.tempF?.let { tf ->
            val dep = f.departureF
            wx += u.temp(tf) + when {
                dep == null || abs(dep) < 1.5 -> ", about normal for the date"
                dep > 0 -> ", ${u.tempDelta(dep)} warmer than normal for the date"
                else -> ", ${u.tempDelta(-dep)} colder than normal for the date"
            }
        }
        f.tempDay24?.let { d -> if (abs(d) >= 5) wx += "the last 24 h ran ${u.tempDelta(abs(d))} ${if (d < 0) "colder" else "warmer"} than the day before" }
        when (f.front) {
            FrontState.AFTER_COLD_FRONT -> wx += "a cold front had just passed"
            FrontState.PRESSURE_FALLING -> wx += "pressure was falling (weather on the way)"
            else -> {}
        }
        when (f.rain) {
            RainState.RAINING -> wx += "it was raining"
            RainState.JUST_ENDED -> wx += "rain had just stopped"
            else -> {}
        }
        val moon = moonInfo(Instant.ofEpochSecond(t).atZone(zone).toLocalDate())
        wx += "moon ${(f.moonIllum * 100).roundToInt()}% lit (${moon.phaseName.lowercase()})"
        lines += StoryLine("Weather", wx.joinToString("; ").replaceFirstChar { it.uppercase() } + ".")

        // ---- wind at this camera
        val dir = f.windDir
        val mph = f.windMph
        if (dir != null && mph != null) {
            if (mph < 3) {
                lines += StoryLine("Wind", "Near calm (${u.speed(mph)}). Scent pools and drifts with thermals on a night like this, so the forecast direction means little.")
            } else {
                val down = (dir + 180) % 360
                val g = f.gustMph?.takeIf { it >= mph + 5 }?.let { ", gusting ${u.speed(it)}" } ?: ""
                lines += StoryLine(
                    "Wind",
                    "From the ${u.compass(dir)} at ${u.speed(mph)}$g, blowing toward the ${u.compass(down)}. " +
                        "The downwind side of $camName was ${u.compass(down)}: a stand there on this wind keeps your scent off the trail.",
                )
                // A swing inside the 15-minute series: mentors say the new wind shows up as puffs early.
                val ds = nearby.filter { it.dir != null && (it.mph ?: 0.0) >= 3 && abs(it.t - t) <= 2 * 3600 }
                if (ds.size >= 3) {
                    val first = ds.first().dir!!; val last = ds.last().dir!!
                    val swing = angleBetween(first, last)
                    if (swing >= 45) lines += StoryLine(
                        "Wind shift",
                        "The wind swung ${swing.roundToInt()}° in the 2 hours around this, ${u.compass(first)} to ${u.compass(last)}. Scrub the map to watch it turn.",
                    )
                }
            }
        }

        // ---- what to do with it
        val minsAfterSunset = f.minsToSunset?.let { -it }?.takeIf { it > 0 }
        val minsBeforeSunrise = f.minsFromSunrise?.let { -it }?.takeIf { it > 0 }
        val nightOnly = n > 0 && daylight == 0
        when {
            f.legal && buckName != null -> {
                steps += Step(
                    "Hunt this exact window",
                    "$who was on his feet here in shooting light, ${headline.lowercase()}. Sit $camName at the same time of day" +
                        (if (dir != null && (mph ?: 0.0) >= 3) " on a ${u.compass(dir)} wind, from the ${u.compass((dir + 180) % 360)} side." else ", from the downwind side of the day's wind."),
                    listOf("m-where_to_hunt-small-daylight-core-01", "m-wind-dont-force-wrong-wind-01"),
                )
                if (f.light == Light.EVENING || f.light == Light.AFTERNOON) steps += Step(
                    "Get in early, stay late",
                    "Be settled well before this time; leave only when it's dark and clear. Climbing down early is how a buck learns your stand.",
                    listOf("m-access-exit-unseen-01"),
                )
            }
            minsAfterSunset != null && minsAfterSunset <= 90 -> steps += Step(
                "He got up close by",
                "Only $minsAfterSunset min after sunset, so his bed is likely not far. Sit the evening on the cover side, 50-100 yd off where he beds, not out on the food.",
                listOf("m-stand_placement-evening-near-bedding-staging-01", "m-stand_placement-staging-cover-off-field-edge-01"),
            )
            minsBeforeSunrise != null && minsBeforeSunrise <= 90 -> steps += Step(
                "He was heading for bed",
                "$minsBeforeSunrise min before sunrise: deer are usually coming off food toward bedding then (Penn State GPS). The way he was walking points at his bed: look for the thickest cover that way.",
                listOf("m-where_to_hunt-bedding-layers-from-food-01", "m-where_to_hunt-early-morning-back-of-bedding-01"),
            )
            else -> steps += Step(
                "This marks food or a route, not his bed",
                "Deep in the night deer feed in bouts and move between food (Penn State GPS). Use this to learn what he's eating here; hunt him closer to cover.",
                listOf("m-wth-feeding-cycle-01", "m-where_to_hunt-daylight-moves-in-cover-01"),
            )
        }
        if (nightOnly && buckName != null && !pool) steps += Step(
            "Don't push in for him yet",
            "All $n of his visits are after dark. Night-only bucks are moving in daylight somewhere else; wait for his first daylight photo before you hunt here, and keep camera checks rare.",
            listOf("m-scouting-night-only-not-nocturnal-01", "m-scouting-limit-camera-checks-01"),
        )
        // What the mentors said about the weather in play.
        MentorWeather.hits(f).filter { it.plus }.forEach { h ->
            steps += Step(
                "${h.label}: a mentor go",
                "${h.agree} source${if (h.agree == 1) "" else "s"} call this a good condition (${h.basis.label.lowercase()})." + (h.caveat?.let { " $it." } ?: ""),
                listOf(h.ruleId),
            )
        }
        // The rut ahead.
        val date = Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
        val toRut = daysUntil(date, rut.rutStart)
        if (f.phase == SeasonPhase.EARLY || f.phase == SeasonPhase.PRE_RUT) if (toRut in 1..60) steps += Step(
            "$toRut days to your rut",
            "Bucks shift more movement into daylight as the rut comes on (MSU GPS: about a third in Oct-Nov, over 40% in the rut). Expect more daylight from him soon.",
            emptyList(),
        )

        val sub = (listOf(camName) + subBits).joinToString("  ·  ")
        return MomentStory(headline, sub, lines, steps)
    }

    /** "38 min before sunrise" + a line on what that light means. */
    private fun lightStory(f: HourFeatures): Pair<String, String?> {
        val sr = f.minsFromSunrise
        val ss = f.minsToSunset
        val legal = if (f.legal) "in shooting light" else "outside shooting light"
        fun hm(m: Int) = if (m < 90) "$m min" else "${m / 60} h ${m % 60} min".replace(" 0 min", "")
        return when {
            ss != null && ss in 0..180 -> "${hm(ss)} before sunset" to "Evening, $legal. The 2 hours around sunset are the busiest of a deer's day (Penn State GPS)."
            ss != null && ss < 0 && -ss <= 180 -> "${hm(-ss)} after sunset" to (if (f.legal) "Last shooting light." else "After dark, $legal.") + " The evening peak runs about an hour past sunset (MSU GPS)."
            sr != null && sr < 0 && -sr <= 180 -> "${hm(-sr)} before sunrise" to (if (f.legal) "First shooting light." else "Still dark, $legal.") + " The morning peak is within 2 hours of sunrise (Penn State GPS)."
            sr != null && sr in 0..180 -> "${hm(sr)} after sunrise" to "Morning, $legal. The morning peak is within 2 hours of sunrise (Penn State GPS)."
            f.light == Light.NIGHT -> {
                val after = ss?.let { -it }
                val before = sr?.let { -it }
                val text = when {
                    after != null && after > 0 && (before == null || before <= 0 || after <= before) -> "${hm(after)} after dark"
                    before != null && before > 0 -> "${hm(before)} before first light"
                    else -> "Middle of the night"
                }
                text to "Night. Deer move less now (~60 m/hr) and feed in bouts (Penn State GPS)."
            }
            else -> {
                val m = sr ?: 0
                "Midday, ${hm(abs(m))} after sunrise" to "Daylight, $legal. The quietest part of a deer's day: mostly bedded, short stand-up bouts (Penn State GPS)."
            }
        }
    }

    private fun ordinal(i: Int) = "$i" + when {
        i % 100 in 11..13 -> "th"
        i % 10 == 1 -> "st"
        i % 10 == 2 -> "nd"
        i % 10 == 3 -> "rd"
        else -> "th"
    }

    private fun possessive(name: String) = if (name.startsWith("this")) "its" else if (name.endsWith("s")) "$name'" else "$name's"

    private fun gapText(sec: Long): String {
        val h = sec / 3600.0
        return when {
            h < 1 -> "${(sec / 60).coerceAtLeast(1)} min"
            h < 36 -> "${h.roundToInt()} h"
            else -> "${(h / 24).roundToInt()} days"
        }
    }

    /** Minutes apart by clock time (ignoring the date) when that's within 60 and the days differ. */
    private fun sameClock(a: Long, b: Long, zone: ZoneId): Int? {
        val za = Instant.ofEpochSecond(a).atZone(zone); val zb = Instant.ofEpochSecond(b).atZone(zone)
        if (za.toLocalDate() == zb.toLocalDate()) return null
        val ma = za.hour * 60 + za.minute; val mb = zb.hour * 60 + zb.minute
        val d = abs(ma - mb).let { minOf(it, 1440 - it) }
        return d.takeIf { it <= 60 }
    }

    private fun daysUntil(d: LocalDate, md: MonthDay): Int {
        var target = md.atYear(d.year)
        if (target.isBefore(d)) target = md.atYear(d.year + 1)
        return (target.toEpochDay() - d.toEpochDay()).toInt()
    }
}
