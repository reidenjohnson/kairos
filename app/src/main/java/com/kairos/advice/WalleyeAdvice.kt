package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Walleye — deep content. Walleye are the low-light specialist: their eyes gather light
 * far better than their prey's, so wind, cloud, stain, dusk, and dark are when they own
 * the water (the "walleye chop"). The plan reads the season phase (water temp + calendar)
 * plus today's [Mood], and leans harder on light than any other fish. Grounded in the
 * walleye-behavior consensus cited in SOURCES.md.
 */

private enum class WalleyePhase(val label: String) {
    COLD("Cold water"),
    SPRING("Spring / post-spawn"),
    SUMMER("Summer"),
    FALL("Fall run"),
}

private fun walleyePhase(waterF: Double, month: Int): WalleyePhase = when {
    waterF < 40 || month == 12 || month == 1 || month == 2 -> WalleyePhase.COLD
    month in 3..5 && waterF < 55 -> WalleyePhase.SPRING
    month >= 9 && waterF < 66 -> WalleyePhase.FALL
    else -> WalleyePhase.SUMMER
}

internal fun walleyePlan(
    sp: Species,
    c: Conditions,
    w: WeatherRead,
    date: LocalDate,
    timing: DayTiming?,
): GamePlan {
    val phase = walleyePhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        WalleyePhase.COLD -> "Walleye are deep and slow in the cold, schooled tight on the deepest structure and feeding in a short low-light window."
        WalleyePhase.SPRING -> "Walleye are shallow after the spawn, feeding on rock, points, and the first weed edges in six to fourteen feet."
        WalleyePhase.SUMMER -> "Walleye have pulled out to deeper structure and weed edges. They feed in the low-light hours and hold deep through the bright of the day."
        WalleyePhase.FALL -> "Walleye are following schools of baitfish toward the shallows, and they feed hard in the last light and after dark."
    }
    val moodClause = when {
        w.heavyRain -> " A downpour is staining the water, which walleye like: fish it slower and a touch bolder near inflows."
        w.lightRain -> " Rain is falling. It dims the light, so they are up and feeding. Get on it."
        mood == Mood.FEEDING -> " A front is moving in and the pressure is dropping, so they are feeding right now. Get out ahead of it."
        mood == Mood.TOUGH -> " But it is bright, calm, and high-pressure behind a front, the walleye's worst light, so they have slid deep. Save your effort for the last hour."
        mood == Mood.ROAMING -> " There is wind and cloud, a walleye's favorite, the classic walleye chop, so they are up on the windblown structure hunting."
        else -> " No big weather push today, so live and die by first light, last light, and after dark."
    }
    val headline = core + moodClause

    val tacticLine = if (mood == Mood.TOUGH) {
        "Go deep and slow: a jig tipped with a minnow or soft plastic, or a slow live-bait rig on the deepest structure, and save your best effort for the last hour of light."
    } else when (phase) {
        WalleyePhase.SPRING ->
            "Cast a light jig, an eighth to three-sixteenths ounce, with a soft-plastic minnow to shorelines, points, and weed edges in six to fourteen feet. Hop a lipless crank in the stained water."
        WalleyePhase.SUMMER ->
            "Work the deep structure and weed edges: a jig or a live-bait rig on the drops, or troll a crawler harness or crankbait along the edges. Fish the low-light window hardest."
        WalleyePhase.FALL ->
            "Cast blade baits or jigging spoons on the drops in eighteen to thirty feet, and throw a stickbait or shallow jerkbait along the shore just before and after dark."
        WalleyePhase.COLD ->
            "Jig a spoon, blade bait, or minnow-tipped jig slow and vertical on the deepest structure, with long pauses between lifts."
    }

    val whyBrief = when {
        w.heavyRain || w.lightRain -> "Rain and stain cut the light, and walleye see better in dim water than their prey does, so a little murk tips the odds their way."
        mood == Mood.FEEDING -> "Falling pressure ahead of a storm sets off a short, hard feed, and walleye time it to the low-light edges of the day."
        mood == Mood.TOUGH -> "Bright, calm, high-pressure sun is the walleye's worst light, so they slide deep and wait for the sun to drop before they feed."
        mood == Mood.ROAMING -> "Walleye eyes gather light far better than baitfish eyes, so wind, cloud, and chop that dim the water hand them the advantage, and they hunt the windblown structure boldly."
        else -> "Walleye are wired for low light, so first light, last light, and after dark are when they leave the depths to hunt."
    }

    val (whereBrief, whereMore) = when (phase) {
        WalleyePhase.COLD -> Pair(
            "Fish the deepest structure, slow. In the cold, walleye school tight and barely move.",
            "Look for the deepest main-lake structure: sharp drops, humps, and points near deep water. They bunch up down there, so once you find one you often find a pile. Keep the lure slow and vertical, right in their face.",
        )
        WalleyePhase.SPRING -> Pair(
            "Fish shallow rock, points, and the first weed edges in six to fourteen feet, where post-spawn walleye feed.",
            "After spawning on rock and in tributaries, walleye slide back to nearby shorelines, points, drop-offs, and green weed edges to recover and feed. Wind-blown, stained banks are best because they pull baitfish and dim the light.",
        )
        WalleyePhase.SUMMER -> Pair(
            "Fish deeper structure and weed edges. Walleye have pulled off the shallows into cooler, dimmer water.",
            "Work main-lake humps, points, deep weed edges, and drop-offs. The fish slide up onto the tops and edges to feed in low light and pull back down when the sun is high, so let the light tell you how deep.",
        )
        WalleyePhase.FALL -> Pair(
            "Fish the drops near baitfish, and the shallows after dark. Walleye are chasing bait toward shore.",
            "Schools of baitfish move shallow from late September on, and walleye follow. Work the deeper drops in the day and move shallow along shore just before and after dark, when the biggest fish of the year come up to feed.",
        )
    }

    val whenBrief = buildString {
        append("Best window today is $windows, and dawn, dusk, and after dark beat everything. ")
        when (mood) {
            Mood.FEEDING -> append("A front is coming, so get out ahead of it. A dropping barometer plus low light is the best walleye feed of the stretch.")
            Mood.TOUGH -> append("It is bright and calm after a front, the toughest walleye light, so wait for the last hour and into dark.")
            Mood.ROAMING -> append("Wind and cloud, the walleye chop, stretch the good hours well past dawn and dusk today.")
            Mood.STEADY -> append("No big weather change, so live and die by the low-light windows.")
        }
    }
    val whenMore = "Walleye feed on the edges of light. Wind, cloud, rain, and stain that dim the water all lengthen the good hours, while bright, calm, high sun shrinks them to almost nothing until dusk. A falling barometer ahead of a storm adds a hard feed on top, and the new-moon and full-moon windows are the one place the moon earns a little weight for this fish."

    val howBrief = tacticLine
    val howMore = buildString {
        when (mood) {
            Mood.TOUGH -> append("Because it is bright and calm, go deep and slow and shrink your expectations until the sun drops. A minnow-tipped jig picked along the bottom is hard to beat. ")
            Mood.ROAMING -> append("Use the walleye chop: fish the structure the wind is blowing into and cover water. The dim, rippled surface has them hunting. ")
            Mood.STEADY -> append("On a steady day, be on your spot for the last hour of light and stay into dark. ")
            Mood.FEEDING -> append("They are feeding, so cover water and fish a little faster to find the active school. ")
        }
        append(
            if (w.clear && !w.overcast) "In clear water, natural minnow and perch colors and a slower fall are best."
            else "In stained or low-light water, add contrast or a little flash and thump so they can track it.",
        )
        append(" Walleye bite soft, so watch your line and set on anything that feels heavy or different.")
    }

    val whyMore = "Walleye are built around a light-gathering eye that outperforms their prey's in dim water, so their whole life runs on light more than temperature or pressure. Wind, cloud, rain, stain, dusk, and dark are their edge, and they push onto structure to hunt when the light drops. Bright, calm, high-pressure sun is the opposite and pins them deep until evening. A falling barometer ahead of a storm adds a short, hard feed, and unlike most fish the new-moon and full-moon windows show a real, if small, effect for walleye."

    return GamePlan(
        phaseLabel = phase.label,
        headline = headline,
        tacticLine = tacticLine,
        whyBrief = whyBrief,
        sections = listOf(
            PlanSection(PlanKind.WHERE, "Where", whereBrief, whereMore),
            PlanSection(PlanKind.WHEN, "When", whenBrief, whenMore),
            PlanSection(PlanKind.HOW, "How", howBrief, howMore),
            PlanSection(PlanKind.WHY, "Why", whyBrief, whyMore),
        ),
    )
}
