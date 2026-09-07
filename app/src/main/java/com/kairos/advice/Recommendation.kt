package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Rating
import com.kairos.engine.Side
import com.kairos.engine.SpeciesScore
import com.kairos.engine.rating
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The honest "what should I actually do?" call. Not just which score is highest — a
 * real recommendation about whether it's worth going at all, and for what: track down
 * game or go fish. It judges ABSOLUTE quality (the rating bands), only considers what's
 * in season, and looks across the week for the best day, so it can say "fishing's the
 * move — Thursday's your day" or "slow week, maybe wait."
 */
enum class RecLevel { GOOD, FAIR, SLOW }

data class WeekPick(
    /** The punch: what's worth doing. */
    val headline: String,
    /** Concrete backup: the species, the honest quality, the best day + window this week. */
    val detail: String,
    val level: RecLevel,
)

/**
 * @param inSeasonToday today's scores for species that are ENABLED and IN SEASON (fish
 *        always qualify; hunt only when open). Already filtered by the caller.
 * @param weekTiming per-day, per-side outlook for the coming days (for the best-day read).
 */
fun weekRecommendation(
    inSeasonToday: List<SpeciesScore>,
    weekTiming: List<DayTiming>,
    today: LocalDate,
): WeekPick {
    if (inSeasonToday.isEmpty()) {
        return WeekPick(
            "Nothing's in season right now",
            "None of your species are open today. Turn more on in Settings, or wait for a season to open.",
            RecLevel.SLOW,
        )
    }

    val best = inSeasonToday.maxByOrNull { it.percent }!!
    val side = best.species.side
    val sideVerb = if (side == Side.FISH) "Fishing" else "Hunting"
    val otherBest = inSeasonToday.filter { it.species.side != side }.maxByOrNull { it.percent }

    val level = when (rating(best.percent)) {
        Rating.PRIME, Rating.GOOD -> RecLevel.GOOD
        Rating.FAIR -> RecLevel.FAIR
        Rating.SLOW -> RecLevel.SLOW
    }

    val headline = when (level) {
        RecLevel.GOOD -> "$sideVerb is your best bet"
        RecLevel.FAIR -> "$sideVerb is the pick, but only fair"
        RecLevel.SLOW -> "Slow stretch — nothing's really firing"
    }

    val parts = mutableListOf<String>()

    // What leads today, in plain quality terms.
    parts += when (level) {
        RecLevel.GOOD -> "${best.species.name} leads at ${best.percent} — conditions actually line up, so it's worth getting out."
        RecLevel.FAIR -> "${best.species.name} tops out at ${best.percent} — fishable, not prime. Go if you've got the itch."
        RecLevel.SLOW -> "The best on the board is ${best.species.name} at ${best.percent}. Honestly a day to skip unless you just want to be out."
    }

    // The best DAY this week for that side, with its window — concrete, not vague.
    val bestDay = weekTiming.maxByOrNull { it.scoreForSide(side) }
    if (bestDay != null) {
        val isToday = bestDay.date == today
        val dayScore = bestDay.scoreForSide(side)
        val window = bestDay.bestWindows(side).firstOrNull()
        val windowText = window?.let { ", best ${hour12(it.first)}–${hour12(it.last + 1)}" } ?: ""
        parts += if (isToday) {
            "Today's actually the best day this week for it$windowText."
        } else {
            "${dayName(bestDay.date)} looks like the best day this week (${dayScore})$windowText."
        }
    }

    // The honest head-to-head: is the other side worth considering instead?
    if (otherBest != null) {
        val otherVerb = if (otherBest.species.side == Side.FISH) "fishing" else "hunting"
        parts += if (best.percent - otherBest.percent >= 8) {
            "That beats $otherVerb (${otherBest.species.name} at ${otherBest.percent}), so it's the better use of a free day."
        } else {
            "$otherVerb is close behind (${otherBest.species.name} at ${otherBest.percent}) if you'd rather."
        }
    }

    return WeekPick(headline, parts.joinToString(" "), level)
}

private fun dayName(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)

private fun hour12(h24: Int): String {
    val h = ((h24 % 24) + 24) % 24
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 $ampm"
}
