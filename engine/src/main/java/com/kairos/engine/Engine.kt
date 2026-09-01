package com.kairos.engine

import kotlin.math.roundToInt

/**
 * The current conditions the engine scores against. Pure data — the app fills
 * this from Open-Meteo (weather), the local [moonInfo] calc, and the
 * [SEBAGO_WATER_F] proxy; tests fill it directly.
 */
data class Conditions(
    /** Live air temperature, °F (used for hunt species). */
    val airF: Double,
    /** Water temperature proxy, °F (used for fish species). */
    val waterF: Double,
    /** Wind speed, mph. */
    val windMph: Double,
    /** Cloud cover, percent 0..100. */
    val cloudPct: Double,
    /** Surface pressure, inHg. */
    val pressureInHg: Double,
    /** Pressure change over the last ~6h, inHg (negative = falling). */
    val pressureTrendInHg: Double,
    /** Coldest drop coming in the next 24h, °F (positive = front incoming). */
    val tempDropNext24hF: Double,
    /** Moon illumination, 0 (new) .. 1 (full). */
    val moonIllum: Double,
)

/** Plain-English rating buckets for a 0..100 score. */
enum class Rating { PRIME, GOOD, FAIR, SLOW }

/** Bucket a 0..100 [pct] into a [Rating]. */
fun rating(pct: Int): Rating = when {
    pct >= 75 -> Rating.PRIME
    pct >= 55 -> Rating.GOOD
    pct >= 40 -> Rating.FAIR
    else -> Rating.SLOW
}

/** Raw weighted score in [0, 1] for [sp] under [c]. */
fun score(sp: Species, c: Conditions): Double {
    val tempIn = if (sp.side == Side.FISH) c.waterF else c.airF
    val w = sp.weights
    return w.temp * fTemp(tempIn, sp.tempSpec) +
        w.trend * fTrend(c.pressureTrendInHg) +
        w.range * fRange(c.pressureInHg) +
        w.front * fFront(c.tempDropNext24hF) +
        w.wind * fWind(c.windMph, sp.wind.lo, sp.wind.hi, sp.wind.hard) +
        w.cloud * fCloud(c.cloudPct) +
        w.moon * fMoon(c.moonIllum, sp.moonMode)
}

/** Score for [sp] as a 0..100 integer. */
fun scorePercent(sp: Species, c: Conditions): Int = (score(sp, c) * 100).roundToInt()

/** A scored result row, ready for the UI. */
data class SpeciesScore(val species: Species, val percent: Int) {
    val rating: Rating get() = rating(percent)
}

/**
 * Score every species under [c]. Optionally filter to one [side]; results are
 * sorted best-first, matching how the reference prints each list.
 */
fun scoreAll(c: Conditions, side: Side? = null): List<SpeciesScore> =
    SPECIES.asSequence()
        .filter { side == null || it.side == side }
        .map { SpeciesScore(it, scorePercent(it, c)) }
        .sortedByDescending { it.percent }
        .toList()
