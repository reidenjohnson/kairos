package com.kairos.bucks

import com.kairos.data.WindMath
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Reads a camera's 15-minute series at any second: wind blended as vectors (so 350° -> 10° turns
 * through north, not back through south), temperature and gusts linearly. Null outside the series
 * or across a gap longer than an hour.
 */
object Quarters {
    data class Now(val t: Long, val mph: Double, val dir: Double, val gust: Double?, val tempF: Double?)

    fun at(series: List<BuckWeather.Quarter>, t: Long): Now? {
        if (series.isEmpty()) return null
        var i = series.binarySearchBy(t) { it.t }
        if (i >= 0) return series[i].let { q -> if (q.mph == null || q.dir == null) null else Now(t, q.mph, q.dir, q.gust, q.tempF) }
        i = -i - 1
        val a = series.getOrNull(i - 1) ?: return null
        val b = series.getOrNull(i) ?: return null
        if (b.t - a.t > 3600) return null
        val am = a.mph ?: return null; val ad = a.dir ?: return null
        val bm = b.mph ?: return null; val bd = b.dir ?: return null
        val k = (t - a.t).toDouble() / (b.t - a.t)
        val u = lerp(WindMath.u(am, ad), WindMath.u(bm, bd), k)
        val v = lerp(WindMath.v(am, ad), WindMath.v(bm, bd), k)
        val mph = hypot(u, v)
        // (u, v) is where the air goes; the FROM direction is the opposite.
        val dir = (Math.toDegrees(atan2(-u, -v)) + 360) % 360
        return Now(t, mph, dir, both(a.gust, b.gust, k), both(a.tempF, b.tempF, k))
    }

    /** Points every 15 min from [from] to [to] (for the "wind swung" check). */
    fun between(series: List<BuckWeather.Quarter>, from: Long, to: Long) = series.filter { it.t in from..to }

    private fun lerp(a: Double, b: Double, k: Double) = a + (b - a) * k
    private fun both(a: Double?, b: Double?, k: Double) = if (a != null && b != null) lerp(a, b, k) else a ?: b
}
