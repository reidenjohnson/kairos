package com.kairos.data

import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Wind vector math, in ONE place so the convention can't drift between the map, the particles
 * and the sheet. Two conventions meet here:
 *  - **Direction** is meteorological "FROM", degrees clockwise from north (0 = a north wind,
 *    blowing south). That is what Open-Meteo returns and what the UI always shows.
 *  - **u / v** are the components the air moves TOWARD: u = east, v = north (the WindNinja
 *    pack format uses the same). Particles drift along (u, v), i.e. downwind.
 * So a north wind (from 0°) at 10 mph is u = 0, v = -10.
 */
object WindMath {

    /** u (east, toward) from speed + FROM direction. */
    fun u(speed: Double, fromDeg: Double): Double = -speed * sin(Math.toRadians(fromDeg))

    /** v (north, toward) from speed + FROM direction. */
    fun v(speed: Double, fromDeg: Double): Double = -speed * cos(Math.toRadians(fromDeg))

    fun speed(u: Double, v: Double): Double = hypot(u, v)

    /** FROM direction in [0, 360) for a toward-vector (u, v). Calm (0, 0) reads as 0. */
    fun fromDeg(u: Double, v: Double): Double {
        if (u == 0.0 && v == 0.0) return 0.0
        val d = Math.toDegrees(atan2(-u, -v))
        return (d + 360.0) % 360.0
    }

    private val COMPASS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    /** 16-point compass letters for a direction, e.g. 337° -> "NNW". */
    fun compass(deg: Double): String {
        val i = Math.floorMod(Math.round(deg / 22.5).toInt(), 16)
        return COMPASS[i]
    }

    // ---- Web Mercator, normalized to [0, 1] (x east, y SOUTH) — what the map draws in. ----

    fun mercX(lon: Double): Double = (lon + 180.0) / 360.0

    fun mercY(lat: Double): Double {
        val r = Math.toRadians(lat.coerceIn(-85.05, 85.05))
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / Math.PI) / 2.0
    }

    fun lonOf(x: Double): Double = x * 360.0 - 180.0

    fun latOf(y: Double): Double = Math.toDegrees(atan(sinh(Math.PI * (1.0 - 2.0 * y))))
}
