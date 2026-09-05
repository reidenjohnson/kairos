package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * The "Game Plan" — Kairos's tactical brain. It reads three things into one plan for
 * the day: the **season phase** (what the animal or fish is doing this time of year),
 * the **weather posture** (how today's pressure, front, wind, sky, and temperature
 * stack up), and the **timing windows** (when to be out).
 *
 * The voice is plain and direct — the way someone who's fished or hunted a place their
 * whole life would tell a beginner what to do, in words a beginner understands. Each
 * section leads with **one clear, do-this sentence** (the [PlanSection.brief]); the
 * longer [PlanSection.more] is there for anyone who wants the reasoning, but you never
 * have to read it to know what to do today.
 *
 * It's honest guidance grounded in established, consensus knowledge — never a promise.
 */

data class GamePlan(
    /** The one-punch read of the day — plain and short. */
    val headline: String,
    val phaseLabel: String,
    val sections: List<PlanSection>,
)

enum class PlanKind { WHERE, WHEN, HOW, WHY }

/** One section: a short do-this [brief] and an optional longer [more] for the curious. */
data class PlanSection(val kind: PlanKind, val label: String, val brief: String, val more: String)

/** A plain reading of today's weather, in the terms that actually change tactics. */
internal class WeatherRead(c: Conditions) {
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
    /** The post-front "bluebird" day: high, clearing, rising — a tough bite. */
    val bluebird = rising && highPressure && clear
    val airF = c.airF
    val waterF = c.waterF
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
 * otherwise (never filler — same season/weather/light reasoning).
 */
fun buildGamePlan(sp: Species, c: Conditions, date: LocalDate, timing: DayTiming?): GamePlan {
    val w = WeatherRead(c)
    return when (sp.name) {
        "Largemouth bass" -> largemouthPlan(sp, c, w, date, timing)
        "Whitetail deer" -> whitetailPlan(sp, c, w, date, timing)
        else -> genericPlan(sp, c, w, date, timing)
    }
}

/**
 * The **general side plan** shown on the Fish / Hunt tab — a rough, plain idea of
 * where to go and what to do today without picking a species. It's built on the most
 * representative pattern for the side (bass for fishing, deer for hunting) but framed
 * generally, so a beginner gets pointed in the right direction at a glance.
 */
fun buildSidePlan(side: Side, c: Conditions, date: LocalDate, timing: DayTiming?): GamePlan =
    if (side == Side.FISH) generalFishPlan(c, WeatherRead(c), date, timing)
    else generalHuntPlan(c, WeatherRead(c), date, timing)
