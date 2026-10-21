package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.HuntMethod
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * The "Game Plan", Kairos's tactical brain. It reads three things into one plan for
 * the day: the **season phase** (what the animal or fish is doing this time of year),
 * the **weather posture** (how today's pressure, front, wind, sky, and temperature
 * stack up), and the **timing windows** (when to be out).
 *
 * The voice is plain and direct, the way someone who's fished or hunted a place their
 * whole life would tell a beginner what to do, in words a beginner understands. Each
 * section leads with **one clear, do-this sentence** (the [PlanSection.brief]); the
 * longer [PlanSection.more] is there for anyone who wants the reasoning, but you never
 * have to read it to know what to do today.
 *
 * It's honest guidance grounded in established, consensus knowledge, never a promise.
 */

/**
 * A plan has two layers. The **card** shows just [headline] + [tacticLine] + [whyBrief]
 * three sentences: exactly what to do, what to throw or how to hunt it, and a quick
 * why. Tapping the card opens the full page, which lays out every [PlanSection] (the
 * detailed Where / When / How / Why). The card is built to *vary* day to day: the
 * headline and tactic fold in today's weather on top of the season.
 */
data class GamePlan(
    val phaseLabel: String,
    /** What to do and where, the punch. Weather + season aware. */
    val headline: String,
    /** What to throw (fishing) or how to hunt it: a couple options and how to work them. */
    val tacticLine: String,
    /** A one-sentence why. */
    val whyBrief: String,
    /** The full breakdown for the plan page. */
    val sections: List<PlanSection>,
)

enum class PlanKind { WHERE, WHEN, HOW, WHY }

/** One section of the full page: a short do-this [brief] and a longer [more]. */
data class PlanSection(val kind: PlanKind, val label: String, val brief: String, val more: String)

/**
 * Assemble the standard four-section plan (Where / When / How / Why). The How brief is
 * always the [tacticLine] so the card and page agree. Shared by the per-species guides.
 */
internal fun fourSectionPlan(
    phaseLabel: String,
    headline: String,
    tacticLine: String,
    whyBrief: String,
    whereBrief: String,
    whereMore: String,
    whenBrief: String,
    whenMore: String,
    howMore: String,
    whyMore: String,
): GamePlan = GamePlan(
    phaseLabel = phaseLabel,
    headline = headline,
    tacticLine = tacticLine,
    whyBrief = whyBrief,
    sections = listOf(
        PlanSection(PlanKind.WHERE, "Where", whereBrief, whereMore),
        PlanSection(PlanKind.WHEN, "When", whenBrief, whenMore),
        PlanSection(PlanKind.HOW, "How", tacticLine, howMore),
        PlanSection(PlanKind.WHY, "Why", whyBrief, whyMore),
    ),
)

/** A plain reading of today's weather, in the terms that actually change tactics. */
internal class WeatherRead(c: Conditions, val precipMmHr: Double = 0.0) {
    val trend = c.pressureTrendInHg
    val falling = trend < -0.03
    val rising = trend > 0.03
    val steady = !falling && !rising
    val pressure = c.pressureInHg
    val highPressure = pressure >= 30.10
    val lowPressure = pressure <= 29.85
    val frontIncoming = c.tempDropNext24hF >= 6.0
    val bigFront = c.tempDropNext24hF >= 12.0
    val windMph = c.windMph
    val windy = c.windMph >= 12.0
    val breezy = c.windMph in 6.0..12.0
    val calm = c.windMph < 6.0
    val cloudPct = c.cloudPct
    val overcast = c.cloudPct >= 55.0
    val partly = c.cloudPct in 25.0..55.0
    val clear = c.cloudPct < 25.0
    /** The post-front "bluebird" day: high, clearing, rising, a tough bite. */
    val bluebird = rising && highPressure && clear
    val airF = c.airF
    val waterF = c.waterF
    // Rain. Light/moderate rain is a strong positive for fish (dims light, masks the
    // fish, washes food and oxygen in); a downpour muddies the water and changes tactics.
    val raining = precipMmHr >= 0.2
    val heavyRain = precipMmHr >= 7.6
    val lightRain = raining && !heavyRain
}

/**
 * Today's weather posture, the single biggest lever on what to actually do. Every
 * plan reads this so the advice changes day to day, not just month to month:
 *  - FEEDING: a front is coming or pressure is dropping, a hard, short feeding window.
 *  - TOUGH: the bright, high-pressure "bluebird" day right after a front, a slow bite.
 *  - ROAMING: clouds and/or wind, so they're up and hunting, so cover water.
 *  - STEADY: nothing pushing them, so lean on the light windows.
 */
internal enum class Mood { FEEDING, TOUGH, ROAMING, STEADY }

internal fun WeatherRead.mood(): Mood = when {
    frontIncoming || falling -> Mood.FEEDING
    lightRain -> Mood.ROAMING // rain dims the light and washes food in, so they hunt
    bluebird -> Mood.TOUGH
    overcast || windy -> Mood.ROAMING
    else -> Mood.STEADY
}

/** Formats the day's best windows into "6–10 AM and 5–8 PM", or a light-based fallback. */
internal fun windowsText(timing: DayTiming?, side: Side): String {
    if (timing == null) return "first light and the last hour before dark"
    val windows = timing.bestWindows(side)
    if (windows.isEmpty()) return "first and last light"
    val parts = windows.map { "${hr(it.first)}–${hr(it.last + 1)}" }
    return when (parts.size) {
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }
}

private fun hr(h24: Int): String {
    val h = ((h24 % 24) + 24) % 24
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 $ampm"
}

/**
 * Build a species' plan. Deep content where it exists; a true, trait-driven plan
 * otherwise (never filler, same season/weather/light reasoning).
 */
fun buildGamePlan(
    sp: Species,
    c: Conditions,
    date: LocalDate,
    timing: DayTiming?,
    precipMmHr: Double = 0.0,
    /** For deer: which weapon the hunt is planned around, so the How adapts (archery is a
     *  close-range wind/scent game, a rifle reaches out, muzzleloader is one late shot).
     *  null = a general, method-agnostic plan. Ignored by species without method choices. */
    method: HuntMethod? = null,
    /** For bear: which method (bait / hounds / spot & stalk) the hunt is planned around. */
    bearApproach: BearApproach? = null,
): GamePlan {
    val w = WeatherRead(c, precipMmHr)
    return when (sp.name) {
        "Whitetail deer" -> whitetailPlan(sp, c, w, date, timing, method)
        "Black bear" -> blackBearPlan(sp, c, w, date, timing, bearApproach)
        "Largemouth bass" -> largemouthPlan(sp, c, w, date, timing)
        "Smallmouth bass" -> smallmouthPlan(sp, c, w, date, timing)
        "Walleye" -> walleyePlan(sp, c, w, date, timing)
        "Brook trout" -> brookTroutPlan(sp, c, w, date, timing)
        "Landlocked salmon" -> landlockedSalmonPlan(sp, c, w, date, timing)
        "Lake trout (togue)" -> lakeTroutPlan(sp, c, w, date, timing)
        "Northern pike" -> pikePlan(sp, c, w, date, timing)
        "Chain pickerel" -> pickerelPlan(sp, c, w, date, timing)
        "Yellow perch" -> yellowPerchPlan(sp, c, w, date, timing)
        "White perch" -> whitePerchPlan(sp, c, w, date, timing)
        "Black crappie" -> blackCrappiePlan(sp, c, w, date, timing)
        "Panfish (sunfish)" -> sunfishPlan(sp, c, w, date, timing)
        "Moose" -> moosePlan(sp, c, w, date, timing)
        "Elk" -> elkPlan(sp, c, w, date, timing)
        "Snowshoe hare" -> snowshoeHarePlan(sp, c, w, date, timing)
        "Upland birds" -> uplandPlan(sp, c, w, date, timing)
        "Waterfowl" -> waterfowlPlan(sp, c, w, date, timing)
        "Wild turkey" -> wildTurkeyPlan(sp, c, w, date, timing)
        "Coyote" -> coyotePlan(sp, c, w, date, timing)
        // "Black bear" handled above (takes the approach choice).
        else -> genericPlan(sp, c, w, date, timing)
    }
}

/**
 * The **general side plan** shown on the Fish / Hunt tab, a rough, plain idea of
 * where to go and what to do today without picking a species. It's built on the most
 * representative pattern for the side (bass for fishing, deer for hunting) but framed
 * generally, so a beginner gets pointed in the right direction at a glance.
 */
fun buildSidePlan(side: Side, c: Conditions, date: LocalDate, timing: DayTiming?, precipMmHr: Double = 0.0): GamePlan {
    val w = WeatherRead(c, precipMmHr)
    return if (side == Side.FISH) generalFishPlan(c, w, date, timing) else generalHuntPlan(c, w, date, timing)
}

/**
 * One tight sentence for the Today card: WHEN to go + WHAT to do, fused. Combines the
 * day's best window with the first, punchiest clause of the species' tactic (which
 * already carries the lure, speed, and color). Meant to be readable at a glance without
 * opening the plan, e.g. "Best 6–10 AM & 5–8 PM: cover water with a jerkbait or
 * fire-tiger crankbait, reeled steady across the rock."
 */
fun todaysPlayLine(sp: Species, c: Conditions, date: LocalDate, timing: DayTiming?, precipMmHr: Double = 0.0): String {
    val plan = buildGamePlan(sp, c, date, timing, precipMmHr)
    val window = windowsText(timing, sp.side).replace(" and ", " & ")
    val tactic = firstClause(plan.tacticLine)
    return "Best $window: $tactic"
}

/** The first sentence of a tactic blurb — the one that names what to throw / how to hunt. */
private fun firstClause(tactic: String): String {
    val end = tactic.indexOf(". ")
    val first = if (end > 0) tactic.substring(0, end + 1) else tactic
    return first.replaceFirstChar { it.lowercaseChar() }
}
