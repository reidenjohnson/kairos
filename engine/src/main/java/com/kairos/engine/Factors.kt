package com.kairos.engine

import kotlin.math.abs

/**
 * Kairos scoring factors — a faithful Kotlin port of the Python reference
 * (`forecast.py`) in the sibling `kairos` docs folder. Every factor returns a
 * 0..1 sub-score; the per-species weighted sum lives in [Engine].
 *
 * The weights and the research behind each factor are documented in SOURCES.md.
 * These functions are pure (no I/O, no clock) so they unit-test on the JVM.
 */

/** Clamp to the [0, 1] range. */
fun clamp(x: Double): Double = x.coerceIn(0.0, 1.0)

/**
 * Barometric pressure trend over the last ~6h, in inHg (negative = falling).
 * A fall of 0.10 inHg/6h is treated as a strong, full-strength trigger.
 */
fun fTrend(pressureTrendInHg: Double): Double =
    clamp(0.5 + (-pressureTrendInHg) / 0.10 * 0.5)

/**
 * Absolute pressure sweet spot. Whitetail feeding peaks 29.9–30.3 inHg;
 * fish comfort 29.7–30.4. Centered on 30.05, full width ±0.55.
 */
fun fRange(inHg: Double): Double = clamp(1 - abs(inHg - 30.05) / 0.55)

/** Cold-front trigger: a ~12°F drop coming in the next 24h = full strength. */
fun fFront(tempDropF: Double): Double = clamp(tempDropF / 12)

/** Cloud cover as a fraction of full overcast. */
fun fCloud(cloudPct: Double): Double = clamp(cloudPct / 100)

/** How a species responds to temperature. */
sealed interface TempSpec {
    /** Cold-loving game: better the colder it is, across [lo, hi]. */
    data class Cold(val lo: Double, val hi: Double) : TempSpec

    /** Warmwater fish: peaks at [ideal], falling off over [spread]. */
    data class Band(val ideal: Double, val spread: Double) : TempSpec

    /** Salmon / togue / brookie: fine while cool, cliff above 62°F. */
    data object Coldwater : TempSpec
}

/** Temperature sub-score for the given species [spec]. */
fun fTemp(tempF: Double, spec: TempSpec): Double = when (spec) {
    is TempSpec.Cold -> clamp((spec.hi - tempF) / (spec.hi - spec.lo))
    is TempSpec.Band -> clamp(1 - abs(tempF - spec.ideal) / spec.spread)
    TempSpec.Coldwater -> when {
        tempF <= 62 -> 1.0
        tempF >= 75 -> 0.05
        else -> clamp(1 - (tempF - 62) / 13)
    }
}

/**
 * Wind sub-score. Full score inside the ideal band [lo, hi]; ramps down below
 * [lo] and above [hi], reaching ~0 at the hard cutoff [hard].
 */
fun fWind(mph: Double, lo: Double, hi: Double, hard: Double): Double = when {
    mph in lo..hi -> 1.0
    mph < lo -> if (lo != 0.0) clamp(0.5 + mph / (lo * 2)) else 1.0
    else -> clamp(1 - (mph - hi) / (hard - hi))
}

/** How a species responds to moonlight. */
enum class MoonMode {
    /** Negligible — the evidence default for everything but hare and walleye. */
    NONE,

    /** Snowshoe hare: a bright moon is worse. */
    INVERSE,

    /** Walleye: peaks near both new and full. */
    NEWFULL,
}

/** Moon sub-score. [illum] is 0 (new) .. 1 (full). */
fun fMoon(illum: Double, mode: MoonMode): Double = when (mode) {
    MoonMode.INVERSE -> clamp(1 - illum)
    MoonMode.NEWFULL -> clamp(abs(illum - 0.5) * 2)
    MoonMode.NONE -> 0.0
}
