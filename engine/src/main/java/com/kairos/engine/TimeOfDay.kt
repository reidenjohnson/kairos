package com.kairos.engine

import kotlin.math.exp
import kotlin.math.max

/**
 * Time-of-day activity — the intraday layer that turns a single "how good is
 * today" score into a "when today" curve. Most game and fish feed hardest around
 * first and last light (crepuscular activity); this is well established across
 * species, independent of the moon/solunar folklore we deliberately gutted.
 *
 * This is a *timing multiplier*, not a weather factor: it modulates the conditions
 * score across the hours of a day so peak windows stand out, without changing how
 * good the day is overall. See SOURCES.md ("Time of day").
 */

/** When a species is most active through the day. */
enum class Chronotype {
    /** Peaks at dawn and dusk, active through daylight, quiet at night (deer, bass, trout…). */
    CREPUSCULAR,

    /** Peaks at dawn/dusk but also feeds in the dark; sensitive to bright midday (walleye, togue). */
    LOW_LIGHT,
}

/**
 * Relative activity at [hour] (0..24, may be fractional) given local [sunriseHour]
 * and [sunsetHour], in 0..1. Two Gaussian peaks at first and last light, on a
 * baseline that differs by [type]. Widened a touch so the peaks read as ~2-3h
 * windows rather than spikes.
 */
fun timeOfDayActivity(
    hour: Double,
    sunriseHour: Double,
    sunsetHour: Double,
    type: Chronotype,
): Double {
    val sigma = 1.3 // hours; controls how wide the dawn/dusk windows are
    fun bump(center: Double): Double {
        val d = hour - center
        return exp(-(d * d) / (2 * sigma * sigma))
    }
    val peak = max(bump(sunriseHour), bump(sunsetHour))
    val isDaylight = hour in sunriseHour..sunsetHour
    return when (type) {
        Chronotype.CREPUSCULAR -> clamp(0.30 + 0.70 * peak + if (isDaylight) 0.15 else 0.0)
        Chronotype.LOW_LIGHT -> clamp(0.35 + 0.65 * peak + if (!isDaylight) 0.15 else 0.0)
    }
}

/**
 * Convert raw activity (0..1) to a gentle score multiplier (0.6..1.0), so off-peak
 * hours are clearly lower on the curve but the conditions score is never zeroed —
 * a great-conditions day still reads well at midday, just below the dawn/dusk peaks.
 */
fun activityMultiplier(activity: Double): Double = 0.6 + 0.4 * clamp(activity)
