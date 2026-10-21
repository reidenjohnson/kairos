package com.kairos.engine.bucks

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sunrise, sunset and solar noon for a spot and a calendar date, worked out on the device (no
 * network), so every old trail-cam photo can be placed against its own day's light. NOAA's
 * standard sunrise equation; good to about a minute in Maine's latitudes, which is finer than a
 * trail-cam clock.
 *
 * Times are unix seconds (UTC). [sunrise]/[sunset] are null on a day the sun never crosses the
 * horizon (never happens below the Arctic Circle; kept for safety).
 */
data class SunDay(val sunrise: Long?, val solarNoon: Long, val sunset: Long?)

object Sun {
    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val TILT = 23.4397

    /** The sun's centre at -0.833° (refraction + its radius): the official sunrise/sunset. */
    private const val HORIZON_DEG = -0.833

    fun day(date: LocalDate, lat: Double, lon: Double): SunDay {
        // Days since J2000 at noon UT of [date], then the local mean solar noon.
        val n = (date.toEpochDay() - 10957).toDouble()
        val jStar = n - lon / 360.0
        val m = norm(357.5291 + 0.98560028 * jStar)
        val mr = Math.toRadians(m)
        val c = 1.9148 * sin(mr) + 0.02 * sin(2 * mr) + 0.0003 * sin(3 * mr)
        val lambda = Math.toRadians(norm(m + c + 180.0 + 102.9372))
        val jTransit = J2000 + jStar + 0.0053 * sin(mr) - 0.0069 * sin(2 * lambda)
        val decl = asin(sin(lambda) * sin(Math.toRadians(TILT)))
        val phi = Math.toRadians(lat)
        val cosW = (sin(Math.toRadians(HORIZON_DEG)) - sin(phi) * sin(decl)) / (cos(phi) * cos(decl))
        val noon = toUnix(jTransit)
        if (cosW < -1 || cosW > 1) return SunDay(null, noon, null)
        val w = Math.toDegrees(acos(cosW))
        return SunDay(toUnix(jTransit - w / 360.0), noon, toUnix(jTransit + w / 360.0))
    }

    private fun norm(deg: Double) = ((deg % 360.0) + 360.0) % 360.0
    private fun toUnix(jd: Double): Long = Math.round((jd - UNIX_EPOCH_JD) * 86400.0)
}
