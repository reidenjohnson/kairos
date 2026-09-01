package com.kairos.engine

import java.time.LocalDate
import kotlin.math.sin

/**
 * On-device moon phase — a faithful port of `astral` 3.2 (`astral.moon`), so the
 * app needs no library and no network for the moon. Verified against astral's
 * outputs in MoonTest. Dates are treated as UTC calendar dates, matching astral.
 */

/** Moon info for a date: fractional illumination and a human phase name. */
data class MoonInfo(val illum: Double, val phaseName: String)

private val PHASE_NAMES = arrayOf(
    "new", "waxing crescent", "first quarter", "waxing gibbous",
    "full", "waning gibbous", "last quarter", "waning crescent",
)

/** Julian Day number for the start of [date] (astral.julian.julianday). */
internal fun julianDay(date: LocalDate): Double {
    var year = date.year
    var month = date.monthValue
    val day = date.dayOfMonth
    if (month <= 2) {
        year -= 1
        month += 12
    }
    val a = year / 100                       // int division, matches Python int()
    val b = 2 - a + a / 4                     // Gregorian calendar
    return (365.25 * (year + 4716)).toInt() +
        (30.6001 * (month + 1)).toInt() +
        day + b - 1524.5
}

/**
 * Moon phase as a float in [0, 28): 0..7 new, 7..14 first quarter,
 * 14..21 full, 21..28 last quarter. Port of astral's `_phase_asfloat`.
 */
internal fun moonPhase(date: LocalDate): Double {
    val jd = julianDay(date)
    val dt = Math.pow(jd - 2382148, 2.0) / (41048480.0 * 86400.0)
    val t = (jd + dt - 2451545.0) / 36525.0
    val t2 = t * t
    val t3 = t2 * t

    val d = Math.toRadians((297.85 + 445267.1115 * t - 0.0016300 * t2 + t3 / 545868).mod(360.0))
    val m = Math.toRadians((357.53 + 35999.0503 * t).mod(360.0))
    val m1 = Math.toRadians((134.96 + 477198.8676 * t + 0.0089970 * t2 + t3 / 69699).mod(360.0))

    var elong = Math.toDegrees(d) + 6.29 * sin(m1)
    elong -= 2.10 * sin(m)
    elong += 1.27 * sin(2 * d - m1)
    elong += 0.66 * sin(2 * d)
    elong = elong.mod(360.0)
    val elongInt = elong.toInt()             // Python int() truncation
    var phase = (elongInt + 6.43) / 360.0 * 28.0
    if (phase >= 28.0) phase -= 28.0
    return phase
}

/** Fractional illumination (0 new .. 1 full) and phase name for [date]. */
fun moonInfo(date: LocalDate): MoonInfo {
    val ph = moonPhase(date)
    val illum = if (ph <= 14) ph / 14 else (28 - ph) / 14
    val name = PHASE_NAMES[((ph.mod(28.0)) / 3.5).toInt()]
    return MoonInfo(illum, name)
}
