package com.kairos.ui

import com.kairos.engine.Conditions
import com.kairos.engine.MoonMode
import com.kairos.engine.Side
import com.kairos.engine.Species
import com.kairos.engine.fCloud
import com.kairos.engine.fFront
import com.kairos.engine.fMoon
import com.kairos.engine.fRange
import com.kairos.engine.fTemp
import com.kairos.engine.fTrend
import com.kairos.engine.fWind
import kotlin.math.abs

/**
 * Turns a species' factor sub-scores into a plain-English "why". Presentation,
 * not scoring — it reuses the engine's pure factor functions.
 *
 * Each factor's *signed impact* is `weight * (sub - 0.5)`: positive means it's
 * pushing this species' score up, negative means dragging it down, and the
 * magnitude reflects how much this species actually cares (its weight). Ranking
 * by that makes the sentence species-specific: temperature dominates for moose,
 * wind for waterfowl, the pressure trend for bass.
 */

private class Impact(
    val up: String,    // phrase to use when this factor is helping
    val down: String,  // phrase to use when it's hurting
    val signed: Double,
)

private fun impacts(sp: Species, c: Conditions): List<Impact> {
    val tempIn = if (sp.side == Side.FISH) c.waterF else c.airF
    val w = sp.weights
    val tempUp = if (sp.side == Side.FISH) "good water temp" else "cool temps"
    val tempDown = if (sp.side == Side.FISH) "off-ideal water temp" else "warm temps"
    val (moonUp, moonDown) = when (sp.moonMode) {
        MoonMode.INVERSE -> "a dark moon" to "a bright moon"
        MoonMode.NEWFULL -> "the moon phase" to "the moon phase"
        MoonMode.NONE -> "the moon" to "the moon"
    }
    return listOf(
        Impact(tempUp, tempDown, w.temp * (fTemp(tempIn, sp.tempSpec) - 0.5)),
        Impact("falling pressure", "rising pressure", w.trend * (fTrend(c.pressureTrendInHg) - 0.5)),
        Impact("steady pressure", "pressure out of range", w.range * (fRange(c.pressureInHg) - 0.5)),
        Impact("an incoming cold front", "no cold front", w.front * (fFront(c.tempDropNext24hF) - 0.5)),
        Impact("ideal wind", "poor wind", w.wind * (fWind(c.windMph, sp.wind.lo, sp.wind.hi, sp.wind.hard) - 0.5)),
        Impact("cloud cover", "clear skies", w.cloud * (fCloud(c.cloudPct) - 0.5)),
        Impact(moonUp, moonDown, w.moon * (fMoon(c.moonIllum, sp.moonMode) - 0.5)),
    )
}

/** One-line explanation of the score: what's helping and what's hurting most. */
fun whyFor(sp: Species, c: Conditions): String {
    val top = impacts(sp, c)
        .sortedByDescending { abs(it.signed) }
        .take(3)
        .filter { abs(it.signed) > 0.02 } // ignore factors that barely move it

    val ups = top.filter { it.signed > 0 }.map { it.up }
    val downs = top.filter { it.signed < 0 }.map { it.down }

    val parts = mutableListOf<String>()
    if (ups.isNotEmpty()) parts += "helped by " + ups.joinToString(", ")
    if (downs.isNotEmpty()) parts += "held back by " + downs.joinToString(", ")
    if (parts.isEmpty()) return "Average conditions across the board."
    return parts.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
}
