package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * The "Game Plan" — Kairos's tactical brain. It reads three things into one
 * plain-spoken plan for the day: the **season phase** (what the animal or fish is
 * doing this time of year), the **weather posture** (how today's pressure, front,
 * wind, sky, and temperature stack up), and the **timing windows** (when to be out).
 *
 * The voice is deliberate: the way someone who has fished or hunted a place their
 * whole life would walk you through the day — specific, situational, and honest.
 * Not "pressure is 30.1 so fish are active," but "they'll pull up shallow to feed
 * on the last of the warm water — start with a topwater at first light, then…".
 *
 * Everything here is presentation grounded in established, consensus knowledge
 * (seasonal patterns, cold-front behavior, light windows). It never invents a
 * certainty; it points you in the right direction the way a good mentor would.
 */

data class GamePlan(
    /** The situational read — one or two sentences that frame the whole day. */
    val headline: String,
    val phaseLabel: String,
    val sections: List<PlanSection>,
)

enum class PlanKind { WHERE, WHEN, HOW, WHY }

data class PlanSection(val kind: PlanKind, val label: String, val body: String)

/**
 * A plain reading of today's weather, in the terms that actually change tactics.
 * These are the levers the per-species content reasons over.
 */
internal class WeatherRead(c: Conditions) {
    val trend = c.pressureTrendInHg
    val falling = trend < -0.03
    val fastFalling = trend < -0.06
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
    /** The classic post-front "bluebird" day: high, clearing, rising — a tough bite. */
    val bluebird = rising && highPressure && clear
    val airF = c.airF
    val waterF = c.waterF
}

/** Formats the day's best windows into "6–10 AM and 5–8 PM", or a light-based fallback. */
internal fun windowsText(timing: DayTiming?, side: Side): String {
    if (timing == null) return "first light and the last hour before dark"
    val windows = timing.bestWindows(side)
    if (windows.isEmpty()) return "first and last light — the odds hold fairly steady between"
    return windows.joinToString(" and ") { "${hr(it.first)}–${hr(it.last + 1)}" }
}

private fun hr(h24: Int): String {
    val h = ((h24 % 24) + 24) % 24
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 $ampm"
}

/**
 * Build the plan. Dispatches to the deep per-species content where it exists and
 * falls back to a still-true, trait-driven plan otherwise (never generic filler —
 * the fallback reasons from the same season/weather/timing levers).
 */
fun buildGamePlan(sp: Species, c: Conditions, date: LocalDate, timing: DayTiming?): GamePlan {
    val w = WeatherRead(c)
    return when (sp.name) {
        "Largemouth bass" -> largemouthPlan(sp, c, w, date, timing)
        "Whitetail deer" -> whitetailPlan(sp, c, w, date, timing)
        else -> genericPlan(sp, c, w, date, timing)
    }
}
