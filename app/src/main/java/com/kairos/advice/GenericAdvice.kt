package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Chronotype
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import com.kairos.engine.TempSpec
import java.time.LocalDate

/**
 * Trait-driven plans on the two-layer model. Two jobs:
 *  - [genericPlan]: the per-species fallback for species without a full deep guide yet
 *    — real reasoning from the species' temperature liking, low-light habits, and today's
 *    [Mood], never filler.
 *  - [generalFishPlan] / [generalHuntPlan]: the general side plan on the Fish / Hunt tab.
 *
 * Deep, species-specific guides (like bass and deer) are being written for each in turn.
 */

// ---- Per-species fallback -------------------------------------------------------------

internal fun genericPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val fish = sp.side == Side.FISH
    val lowLight = sp.chronotype == Chronotype.LOW_LIGHT
    val mood = w.mood()
    val windows = windowsText(timing, sp.side)

    val whereBrief = if (fish) {
        when (sp.tempSpec) {
            is TempSpec.Coldwater -> "Fish deep and cool. These fish need cold, oxygen-rich water, so they hold off the deeper spots and near cold inflows, coming shallow only in low light."
            is TempSpec.Band -> "Fish the edges — drop-offs, weed lines, points, and the banks the wind is blowing into, where the smaller fish they eat get pushed together."
            else -> "Fish where the depth or cover changes and prey collects — points, weed edges, and drop-offs."
        }
    } else {
        "Set up between where they feed and where they bed — hunt the food and the trails to it, and let the terrain funnel them past you."
    }
    val whereMore = if (fish)
        "Fish gang up where something concentrates their prey and gives them an ambush spot. Find that edge and you've found the fish."
    else
        "Comfort and food run their day: they bed where they feel safe and travel a predictable route to feed. Sit on that route, downwind and hidden."

    val core = if (fish) whereBrief.substringBefore(".") + "." else whereBrief
    val moodClause = when {
        mood == Mood.FEEDING -> " A front's coming and pressure's falling, so they're feeding — get out ahead of it."
        mood == Mood.TOUGH -> " It's bright and high-pressure after a front, so they're sluggish — slow down and fish the edges of light."
        lowLight -> " This one sees best in dim light, so dawn, dusk, and cloudy skies are far and away your best odds."
        mood == Mood.ROAMING -> " Clouds and wind have them up and hunting, so cover water."
        else -> " No big weather push today, so lean on first and last light."
    }
    val headline = core + moodClause

    val tacticLine = if (fish) {
        when {
            mood == Mood.TOUGH || (w.calm && w.clear) -> "Slow down and go small — a finesse worm or a light jig on light line, in natural colors, worked slow."
            mood == Mood.ROAMING || mood == Mood.FEEDING -> "Cover water with a moving lure that looks like a small fish — a crankbait, spinnerbait, or swimbait reeled steady, and a topwater in low light."
            else -> "Start with a moving lure to find them, then slow down with a worm or jig where you get bit."
        }
    } else {
        if (w.windy) "Hunt the sheltered, downwind side where they bed out of the wind, and let the wind hide your movement."
        else "Sit still, play the wind so your scent blows away from them, and out-wait them — don't move too much, too soon."
    }

    val whyBrief = when {
        mood == Mood.FEEDING -> "A dropping barometer before a storm sets off a short, hard feeding window."
        mood == Mood.TOUGH -> "The bright, high-pressure air after a front makes them cautious and tight to cover."
        lowLight -> "Their eyes are built for dim light, so dawn, dusk, and clouds are when they hunt."
        else -> "With calm weather, the daily light rhythm rules — feeding clusters at first and last light."
    }

    val whenBrief = buildString {
        append("Best window today is $windows. ")
        when {
            mood == Mood.FEEDING -> append("A front is coming — get out ahead of it, that's the best feeding window of the stretch.")
            lowLight -> append("Dawn, dusk, and cloud cover are your best shot with this one.")
            mood == Mood.TOUGH -> append("Bright day after a front — a tough bite — so stick to first and last light.")
            else -> append("No big weather change, so lean on first and last light.")
        }
    }
    val howMore = if (fish)
        (if (w.clear && !w.overcast) "In bright, clear water use natural, lifelike colors." else "In gray or stained water go bolder so they can find it by its outline.") + " When you catch one, slow down — they group up."
    else
        "Scent control beats everything in the deer woods — get set early, stay quiet, and let the day come to you."

    return GamePlan(
        phaseLabel = if (fish) "Seasonal pattern" else "Daily pattern",
        headline = headline,
        tacticLine = tacticLine,
        whyBrief = whyBrief,
        sections = listOf(
            PlanSection(PlanKind.WHERE, "Where", whereBrief, whereMore),
            PlanSection(PlanKind.WHEN, "When", whenBrief, "Falling pressure ahead of a storm is the strongest short-term trigger; a bright, calm, high-pressure day after a front is the toughest. Clouds and a little wind stretch the good hours out."),
            PlanSection(PlanKind.HOW, "How", tacticLine, howMore),
            PlanSection(PlanKind.WHY, "Why", whyBrief, "It comes down to one question: are conditions telling them to feed and move, or to hide and wait? Today's answer is above."),
        ),
    )
}

// ---- General side plans (Fish / Hunt tab) --------------------------------------------

internal fun generalFishPlan(c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val cooling = date.monthValue >= 9
    val water = c.waterF
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    data class Season(val label: String, val core: String, val whereBrief: String, val whereMore: String)
    val s = when {
        water < 46 -> Season(
            "Cold water",
            "The water's cold, so fish are deep and slow — fewer bites, but they run big.",
            "Fish the deepest spots near where you'd fish in summer, and keep the lure slow and near the bottom.",
            "In cold water fish barely move to save energy, so they stack up deep and wait for food to come to them.",
        )
        cooling && water < 72 -> Season(
            "Fall feed-up",
            "The lake's cooling, so fish are herding the smaller fish they eat into the shallows to fatten up for winter — hit the backs of coves.",
            "Focus on the backs of coves and creek arms and the banks the wind is blowing into, where the prey gets pushed together.",
            "As the water cools, huge schools of prey move shallow and everything that eats them follows — one of the best times of year to catch numbers.",
        )
        water > 74 -> Season(
            "Summer heat",
            "It's warm, so fish shade and depth — the shallow bite is best at dawn and dusk, deeper through midday.",
            "Early and late, fish the shade — docks, fallen trees, weed mats. When the sun's high, move out to deeper edges where it's cooler.",
            "Warm water holds less oxygen and bright sun is uncomfortable, so fish pull to shade and depth by day and feed in the cool low-light hours.",
        )
        else -> Season(
            "Warming up",
            "The water's warming, so fish are sliding shallow to feed — work the sun-warmed, wind-protected banks.",
            "Look at north-facing, wind-protected banks and dark-bottomed bays — they warm first and pull fish and their prey up shallow.",
            "As the lake warms in spring, fish follow the warmth toward the shallows to feed and, later, to spawn — the warmest water is the most active.",
        )
    }

    val moodClause = when {
        w.heavyRain -> " A downpour's muddying the water — slow down, hug cover, and use something they can find in the murk."
        w.lightRain -> " Rain's falling — it dims the light and washes food in, so they're up and feeding. Get on it."
        mood == Mood.FEEDING -> " A front's moving in and the pressure's falling, so they're feeding — get out now."
        mood == Mood.TOUGH -> " But it's bright and high-pressure after a front, so expect a slower day — fish early and late."
        mood == Mood.ROAMING -> " Clouds and wind have them roaming, so cover water."
        else -> " No big weather push, so lean on the light windows."
    }

    val tacticLine = when {
        mood == Mood.TOUGH || (w.calm && w.clear) -> "Slow down and go small — a finesse worm or light jig, natural colors, light line."
        else -> "Cover water with a moving lure that looks like a small fish — a crankbait, spinnerbait, or lipless crank reeled steady; walk a topwater when fish break the surface."
    }
    val whyBrief = when {
        w.heavyRain -> "Heavy rain muddies the water and cuts visibility, so fish hold tight to cover and hunt by feel."
        w.lightRain -> "Rain dims the light and dimples the surface, so fish drop their guard and feed — and runoff washes food and oxygen into the edges."
        mood == Mood.FEEDING -> "Falling pressure before a storm makes fish feed hard — a good day to be out."
        mood == Mood.TOUGH -> "The bright, high-pressure sky after a front makes fish sluggish and tight to cover."
        mood == Mood.ROAMING -> "Clouds and wind dim the light and ripple the surface, so fish roam and hunt."
        else -> "With steady weather, fish feed in low light and rest when the sun is high."
    }

    val whenBrief = buildString {
        append("Best window today is $windows. ")
        when (mood) {
            Mood.FEEDING -> append("A front is coming — get out ahead of it, the hours before bad weather are the best feeding of the stretch.")
            Mood.TOUGH -> append("Bright day after a front — a tough bite — so fish first and last light.")
            else -> append("Lean on first and last light.")
        }
    }
    val howMore = (if (w.clear && !w.overcast) "In bright, clear water use natural, lifelike colors." else "In gray or stained water use bolder colors so fish can find it by its outline.") +
        " When you catch one, fish that exact spot — fish group up, so there are usually more."

    return GamePlan(
        phaseLabel = s.label,
        headline = s.core + moodClause,
        tacticLine = tacticLine,
        whyBrief = whyBrief,
        sections = listOf(
            PlanSection(PlanKind.WHERE, "Where", s.whereBrief, s.whereMore),
            PlanSection(PlanKind.WHEN, "When", whenBrief, "Fish feed hardest when a storm is coming and the pressure is falling, and slow down on bright, calm days after a front. Match the season for where, the weather for when."),
            PlanSection(PlanKind.HOW, "How", tacticLine, howMore),
            PlanSection(PlanKind.WHY, "Why", whyBrief, "Fish are ambush hunters that rely on cover and low light. Weather changes flip a short feeding switch; bright, stable days do the opposite."),
        ),
    )
}

internal fun generalHuntPlan(c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan =
    whitetailPlan(SPECIES_DEER, c, w, date, timing)

private val SPECIES_DEER: Species = com.kairos.engine.SPECIES.first { it.name == "Whitetail deer" }
