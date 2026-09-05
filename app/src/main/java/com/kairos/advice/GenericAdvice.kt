package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Chronotype
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import com.kairos.engine.TempSpec
import java.time.LocalDate

/**
 * Trait-driven plans, written plainly. Two jobs live here:
 *  - [genericPlan]: the per-species fallback for species without a full deep guide yet.
 *    Not filler — it reasons from the same real levers (the species' temperature liking,
 *    its low-light habits, and today's weather and light).
 *  - [generalFishPlan] / [generalHuntPlan]: the **general side plan** shown on the Fish /
 *    Hunt tab, so you get a rough, plain idea of where to go without picking a species.
 *
 * Deep, species-specific guides (like bass and deer) are being written for each in turn.
 */

// ---- Weather-driven sections shared by the plans below --------------------------------

private fun whenSection(w: WeatherRead, windows: String, lowLight: Boolean): PlanSection {
    val brief = buildString {
        append("Best window today is $windows. ")
        when {
            w.frontIncoming -> append("A weather front is coming — get out ahead of it, that's the best feeding window of the stretch.")
            w.falling -> append("The pressure is dropping, which turns fish and game on — favor the next few hours.")
            lowLight -> append("This one sees best in dim light: dawn, dusk, and cloudy skies are far and away your best odds.")
            w.bluebird -> append("It's a bright day after a front — a tough bite — so stick to first and last light.")
            else -> append("No big weather change today, so lean on first and last light.")
        }
    }
    val more = "Falling pressure ahead of a storm is the strongest short-term trigger there is; a bright, calm, high-pressure day right after a front is the toughest. Clouds and a little wind stretch the good hours out."
    return PlanSection(PlanKind.WHEN, "When", brief, more)
}

// ---- Per-species fallback -------------------------------------------------------------

internal fun genericPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val fish = sp.side == Side.FISH
    val lowLight = sp.chronotype == Chronotype.LOW_LIGHT
    val windows = windowsText(timing, sp.side)

    val whereBrief = if (fish) {
        when (sp.tempSpec) {
            is TempSpec.Coldwater -> "Fish deep and cool. These fish need cold, oxygen-rich water, so they hold off the deeper spots and near cold inflows, coming shallow only in the low light of dawn and dusk."
            is TempSpec.Band -> "Fish the edges — drop-offs, weed lines, points, and the banks the wind is blowing into, where the small baitfish they eat get pushed together."
            else -> "Fish where the depth or cover changes and food collects — points, weed edges, and drop-offs."
        }
    } else {
        "Set up between where they feed and where they bed — hunt the food and the trails to it, and let the terrain funnel them past you."
    }
    val whereMore = if (fish)
        "Fish gang up where something concentrates their food and gives them an ambush spot. Find that edge and you've found the fish."
    else
        "Comfort and food run their day: they bed where they feel safe and travel a predictable route to feed. Sit on that route, downwind, and stay hidden."

    val howBrief = if (fish) {
        when {
            w.overcast || w.windy -> "Cover water with a moving lure — the low light and ripple let them roam and hunt, so keep it moving to find active fish."
            w.bluebird || (w.calm && w.clear) -> "Slow down and go small. In bright, calm water they're picky — light line, natural colors, quiet approach."
            else -> "Start with a moving lure to find them, then slow down and work the spot where you get bit."
        }
    } else {
        if (w.windy) "Hunt the sheltered, downwind side of cover where deer bed out of the wind, and let the wind hide your movement."
        else "Sit still, play the wind so your scent blows away from the deer, and out-wait them — don't move too much, too soon."
    }
    val howMore = if (fish)
        (if (w.clear && !w.overcast) "In bright, clear water keep colors natural and lifelike." else "In gray or stained water go bolder — brighter or darker — so they can find it by its outline.") + " When you catch one, slow down and work that spot; they group up."
    else
        "Scent control beats everything in the deer woods — one whiff of you and they're gone. Get set early, stay quiet, and let the day come to you."

    val whyBrief = when {
        w.frontIncoming || w.falling -> "A dropping barometer before a storm sets off a short, hard feeding window."
        w.bluebird -> "The bright, high-pressure air after a front makes them cautious and tight to cover."
        lowLight -> "Their eyes are built for dim light, so dawn, dusk, and cloudy days are when they hunt and feed."
        else -> "With calm weather, the daily light rhythm rules — feeding clusters at first and last light."
    }

    return GamePlan(
        headline = if (fish) "Read the season, the weather, and the light together — here's the plan for today."
        else "Read the calendar, the weather, and the wind together — here's the plan for today.",
        phaseLabel = if (fish) "Seasonal pattern" else "Daily pattern",
        sections = listOf(
            PlanSection(PlanKind.WHERE, "Where", whereBrief, whereMore),
            whenSection(w, windows, lowLight),
            PlanSection(PlanKind.HOW, "How", howBrief, howMore),
            PlanSection(PlanKind.WHY, "Why", whyBrief, "It's all one question: are conditions telling them to feed and move, or to hide and wait? Today's answer is above."),
        ),
    )
}

// ---- General side plans (Fish / Hunt tab) --------------------------------------------

internal fun generalFishPlan(c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val cooling = date.monthValue >= 9
    val water = c.waterF
    val windows = windowsText(timing, Side.FISH)

    // A plain, general season read for a Maine lake.
    val (label, headline, whereBrief, whereMore) = when {
        water < 46 -> arrayOf(
            "Cold water",
            "The water's cold, so fish are deep and slow. Fewer bites, but they can be big — fish patiently.",
            "Fish the deepest spots near where you'd fish in summer, and keep your lure slow and close to the bottom. Cold fish won't chase.",
            "In cold water fish barely move to save energy, so they stack up in deep water and wait for food to come to them.",
        )
        cooling && water < 72 -> arrayOf(
            "Fall feed-up",
            "The lake's cooling, so fish are following baitfish (small fish they eat) into the shallows to fatten up for winter. Fish the backs of coves, early and late.",
            "Focus on the backs of coves and creek arms, and the banks the wind is blowing into — that's where the small fish get pushed together and the bigger fish feed on them.",
            "As the water cools, huge schools of baitfish move shallow, and everything that eats them follows. This is one of the best times of year to catch a lot of fish.",
        )
        water > 74 -> arrayOf(
            "Summer heat",
            "It's warm, so fish shade and deep water. The shallow bite is best at dawn and dusk; midday, go deeper.",
            "Early and late, fish the shade — docks, fallen trees, weed mats. When the sun gets high, move out to deeper spots like weed edges and drop-offs where it's cooler.",
            "Warm water holds less oxygen and bright sun is uncomfortable, so fish pull into shade and depth in the heat of the day and feed in the cool low-light hours.",
        )
        else -> arrayOf(
            "Warming up",
            "The water's warming, so fish are moving shallow to feed. Fish the sun-warmed, wind-protected banks.",
            "Look at north-facing, wind-protected banks and dark-bottomed bays — they warm first and pull fish and their food up shallow.",
            "As the lake warms in spring, fish follow the warmth toward the shallows to feed and, later, to spawn — the warmest water is the most active water.",
        )
    }

    val howBrief = when {
        w.overcast || w.windy -> "Cover water with a moving lure that looks like a small fish — the low light and ripple let fish roam and hunt."
        w.bluebird || (w.calm && w.clear) -> "Slow down and go small in the bright, calm water — light line and natural colors."
        else -> "Start with a moving lure to find fish, then slow down where you get bit."
    }
    val howMore = (if (w.clear && !w.overcast) "In bright, clear water use natural, lifelike colors." else "In gray or stained water use bolder colors so fish can find it by its outline.") +
        " When you catch one, slow down and fish that exact spot — fish group up, so there are usually more."

    val whyBrief = when {
        w.frontIncoming || w.falling -> "Falling pressure before a storm makes fish feed hard, so today's a good day to be out."
        w.bluebird -> "It's bright after a front — fish are sluggish, so expect a slower day and fish early and late."
        else -> "Steady weather means the daily rhythm rules: fish feed in low light and rest when the sun is high."
    }

    return GamePlan(
        headline = headline as String,
        phaseLabel = label as String,
        sections = listOf(
            PlanSection(PlanKind.WHERE, "Where", whereBrief as String, whereMore as String),
            whenSection(w, windows, lowLight = false),
            PlanSection(PlanKind.HOW, "How", howBrief, howMore),
            PlanSection(PlanKind.WHY, "Why", whyBrief, "Fish feed hardest when a storm is coming and the pressure is falling, and slow down on bright, calm days after a front. Match the season for where, the weather for when."),
        ),
    )
}

internal fun generalHuntPlan(c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    // Deer are the main hunt species, so the general hunt plan reads like a deer day.
    return whitetailPlan(SPECIES_DEER, c, w, date, timing).let { deer ->
        GamePlan(
            headline = deer.headline,
            phaseLabel = deer.phaseLabel,
            sections = deer.sections,
        )
    }
}

private val SPECIES_DEER: Species = com.kairos.engine.SPECIES.first { it.name == "Whitetail deer" }
