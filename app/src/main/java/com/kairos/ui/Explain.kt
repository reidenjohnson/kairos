package com.kairos.ui

import com.kairos.engine.Chronotype
import com.kairos.engine.Conditions
import com.kairos.engine.MoonMode
import com.kairos.engine.Side
import com.kairos.engine.Species
import com.kairos.engine.TempSpec
import com.kairos.engine.fCloud
import com.kairos.engine.fFront
import com.kairos.engine.fMoon
import com.kairos.engine.fRange
import com.kairos.engine.fTemp
import com.kairos.engine.fTrend
import com.kairos.engine.fWind
import kotlin.math.abs
import kotlin.math.roundToInt

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

/** Whether a factor is currently favorable, unfavorable, or neutral for a species. */
enum class FactorDir { UP, DOWN, NEUTRAL }

/**
 * One factor's contribution, for the species detail screen: a label, the current
 * reading, a plain-English note, an up/down/neutral arrow, and [weight] — how much
 * this species actually cares (drives the ordering and the "matters" bar).
 */
data class FactorRow(
    val label: String,
    val value: String,
    val note: String,
    val dir: FactorDir,
    val weight: Double,
)

private fun dirOf(sub: Double): FactorDir = when {
    sub > 0.58 -> FactorDir.UP
    sub < 0.42 -> FactorDir.DOWN
    else -> FactorDir.NEUTRAL
}

/**
 * The full per-factor breakdown for a species, most-important first. Each factor's
 * reading and note are species-aware; factors this species ignores (moon for most)
 * are dropped. Presentation only — it reads the engine's pure factor functions.
 */
fun factorBreakdown(sp: Species, c: Conditions): List<FactorRow> {
    val w = sp.weights
    val fish = sp.side == Side.FISH
    val rows = mutableListOf<FactorRow>()

    val tIn = if (fish) c.waterF else c.airF
    val tSub = fTemp(tIn, sp.tempSpec)
    val tNote = when (val s = sp.tempSpec) {
        is TempSpec.Band ->
            if (tSub > 0.58) "Near the ideal ~${s.ideal.roundToInt()}°F" else "Off the ideal ~${s.ideal.roundToInt()}°F band"
        is TempSpec.Cold ->
            if (tSub > 0.58) "Cool — good for daytime movement" else if (tSub < 0.42) "Warm — cuts daytime movement" else "Middling temps"
        TempSpec.Coldwater ->
            if (tIn <= 62) "Cool enough for coldwater fish" else "Warm — coldwater fish slow above 62°F"
    }
    rows += FactorRow(if (fish) "Water temp" else "Air temp", "${tIn.roundToInt()}°F", tNote, dirOf(tSub), w.temp)

    val trSub = fTrend(c.pressureTrendInHg)
    val d = c.pressureTrendInHg
    val trVal = when {
        d < -0.01 -> "falling ${"%.2f".format(-d)}\""
        d > 0.01 -> "rising ${"%.2f".format(d)}\""
        else -> "steady"
    }
    val trNote = when (dirOf(trSub)) {
        FactorDir.UP -> "Falling pressure is a feeding trigger"
        FactorDir.DOWN -> "Rising pressure — less of a trigger"
        FactorDir.NEUTRAL -> "Little pressure change"
    }
    rows += FactorRow("Pressure trend", trVal, trNote, dirOf(trSub), w.trend)

    val rSub = fRange(c.pressureInHg)
    rows += FactorRow(
        "Pressure",
        "${"%.2f".format(c.pressureInHg)}\"",
        if (rSub > 0.58) "In the comfortable band (~30\")" else "Outside the ideal pressure band",
        dirOf(rSub),
        w.range,
    )

    val fSub = fFront(c.tempDropNext24hF)
    rows += FactorRow(
        "Cold front",
        "−${c.tempDropNext24hF.roundToInt()}° / 24h",
        if (fSub > 0.55) "A cold front's moving in — a prime window" else "No real front coming",
        dirOf(fSub),
        w.front,
    )

    val wSub = fWind(c.windMph, sp.wind.lo, sp.wind.hi, sp.wind.hard)
    val wNote = when {
        wSub > 0.58 -> "In the ideal ${sp.wind.lo.roundToInt()}–${sp.wind.hi.roundToInt()} mph band"
        c.windMph > sp.wind.hi -> "Windier than ideal"
        else -> "Calmer than ideal"
    }
    rows += FactorRow("Wind", "${c.windMph.roundToInt()} mph", wNote, dirOf(wSub), w.wind)

    val cSub = fCloud(c.cloudPct)
    rows += FactorRow(
        "Cloud cover",
        "${c.cloudPct.roundToInt()}%",
        if (c.cloudPct >= 55) "Overcast extends the movement window" else "Clear skies",
        dirOf(cSub),
        w.cloud,
    )

    if (w.moon > 0.001) {
        val mSub = fMoon(c.moonIllum, sp.moonMode)
        val mNote = when (sp.moonMode) {
            MoonMode.INVERSE -> if (mSub > 0.58) "Dark moon — better for hare" else "Bright moon — hare stay hunkered"
            MoonMode.NEWFULL -> if (mSub > 0.58) "Near new or full — a walleye window" else "Mid-phase moon"
            MoonMode.NONE -> ""
        }
        rows += FactorRow("Moon", "${(c.moonIllum * 100).roundToInt()}% lit", mNote, dirOf(mSub), w.moon)
    }

    return rows.sortedByDescending { it.weight }
}

/**
 * A short, plain-English "game plan": how to read today's conditions into a tactic.
 * Grounded in the same evidence the score uses (pressure, fronts, temp, light) — it
 * is labeled guidance, never a claim of certainty, and stops short of specific lure
 * calls (that's the research-gated lure work).
 */
fun gamePlan(sp: Species, c: Conditions): String {
    val fish = sp.side == Side.FISH
    val parts = mutableListOf<String>()

    parts += when {
        fFront(c.tempDropNext24hF) > 0.5 ->
            "A cold front is moving in — the hours just before it usually fire best, so try to be out ahead of the change."
        fTrend(c.pressureTrendInHg) > 0.62 ->
            "Pressure is falling, which tends to switch ${if (fish) "fish" else "game"} on — favor the next several hours."
        c.pressureTrendInHg > 0.03 && fRange(c.pressureInHg) > 0.6 ->
            "High, steady pressure means a slower ${if (fish) "bite" else "day"} — slow down and be patient."
        else ->
            "No single weather trigger today — lean on the best light windows."
    }

    if (fish) {
        val ideal = fTemp(c.waterF, sp.tempSpec)
        parts += when {
            ideal < 0.4 && c.waterF > 62 -> "Water's warm — work deeper structure and shade, and the cooler edges of the day."
            ideal < 0.4 -> "Water's cold and fish are sluggish — downsize and slow your presentation."
            else -> "Water temp is in a good range — cover water and stay on structure edges."
        }
    } else {
        parts += if (fTemp(c.airF, sp.tempSpec) > 0.55) {
            "Cool temps favor daytime movement — sit longer on food and travel routes."
        } else {
            "It's warm for the season — expect movement to bunch at first and last light."
        }
    }

    parts += if (sp.chronotype == Chronotype.LOW_LIGHT) {
        "This one leans low-light: dawn, dusk, and overcast are your best bets."
    } else {
        "First and last light are the highest-odds windows."
    }

    return parts.joinToString(" ")
}
