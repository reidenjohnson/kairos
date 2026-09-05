package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Chronotype
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import com.kairos.engine.TempSpec

/**
 * The trait-driven plan for species that don't yet have a full deep guide. It is NOT
 * filler — it reasons from the same real levers (the species' temperature preference,
 * its low-light habits, and today's front/pressure/wind/sky/light) to give honest,
 * situational guidance. Deep, species-specific content (like the bass and deer guides)
 * is being written for each of these in turn; until then this stays truthful and useful.
 */
internal fun genericPlan(
    sp: Species,
    c: Conditions,
    w: WeatherRead,
    date: java.time.LocalDate,
    timing: DayTiming?,
): GamePlan {
    val fish = sp.side == Side.FISH
    val lowLight = sp.chronotype == Chronotype.LOW_LIGHT
    val windows = windowsText(timing, sp.side)

    val where = if (fish) {
        when (sp.tempSpec) {
            is TempSpec.Coldwater ->
                "Coldwater fish hold where the water stays cool and oxygenated — off the deeper structure, near inflows and spring seeps, and up shallow only in the cold, low-light edges of the day. As the surface warms through fall they slide back toward the shallows and the bait."
            is TempSpec.Band ->
                "Find the structure that concentrates them — points, drop-offs, weed edges, and the windblown banks where the wind pushes bait. They set up where cover meets a change in depth."
            else ->
                "Work structure and edges — the places where depth or cover changes and bait collects."
        }
    } else {
        "Tie your setup to the food and the cover: hunt the trails and edges between where they feed and where they bed, and let the terrain funnel them past you."
    }

    val whenBody = buildString {
        append("Best windows today: $windows. ")
        when {
            w.frontIncoming ->
                append("A front is on the way — the hours ahead of it are the best feeding window of the stretch, so be out before the change.")
            w.falling ->
                append("Falling pressure tends to switch them on — favor the next several hours.")
            lowLight ->
                append("This one is a low-light specialist: dawn, dusk, overcast, and (where legal) after dark are far and away your best odds.")
            w.bluebird ->
                append("It's a high, bright post-front day — a tough hand — so the bite shrinks to the very first and last light.")
            else ->
                append("With no strong trigger, the light windows do the work — first and last light.")
        }
    }

    val how = buildString {
        if (fish) {
            when {
                w.overcast || w.windy ->
                    append("Low light and a little chop let them roam and chase, so cover water with a moving bait; ")
                w.bluebird || (w.calm && w.clear) ->
                    append("Bright and calm means finicky — slow down, downsize, lighten your line, and fish natural colors; ")
                else ->
                    append("Match your speed to their mood — start moving, slow down if they won't commit; ")
            }
            append(
                if (w.clear && !w.overcast) "in the bright, clear light keep colors natural and subtle."
                else "in the low, gray light go bolder — brighter or darker so they can find it by silhouette.",
            )
        } else {
            append("Hunt from a stand where you can stay unseen and downwind — play the wind so your scent blows away from where you expect them. ")
            append(
                if (w.windy) "In the wind they'll bed in sheltered, leeward cover, so hunt the protected side and let the wind hide your movement."
                else "Slow down and out-sit them; the common mistake is moving too much, too soon.",
            )
        }
    }

    val why = when {
        w.frontIncoming || w.falling ->
            "A dropping barometer and an incoming front cue heavy feeding before the weather shuts things down — it's the strongest short-term trigger there is."
        w.bluebird ->
            "The high, bright, stable air behind a front makes them cautious and tight to cover, so the bite compresses into the edges of the day."
        lowLight ->
            "Their eyes are built for low light, giving them the advantage at dawn, dusk, and under cloud — which is exactly when they hunt and feed."
        else ->
            "With settled weather the daily light rhythm rules — movement and feeding cluster at first and last light."
    }

    return GamePlan(
        headline = "A ${sp.name.lowercase()} plan for today's conditions — read the season, the weather, and the light together.",
        phaseLabel = if (fish) "Seasonal pattern" else "Daily pattern",
        sections = listOf(
            PlanSection(PlanKind.WHERE, if (fish) "Where they are" else "Where to set up", where),
            PlanSection(PlanKind.WHEN, if (fish) "When to go" else "When to hunt", whenBody),
            PlanSection(PlanKind.HOW, if (fish) "How to work it" else "How to hunt it", how),
            PlanSection(PlanKind.WHY, "Why", why),
        ),
    )
}
