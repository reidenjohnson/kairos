package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Panfish — yellow perch, white perch, black crappie, and sunfish/bluegill. All are
 * schooling fish that spawn shallow in spring, slide to slightly deeper, cooler water in
 * summer, and tighten into schools in fall. They differ in light: crappie and white perch
 * are low-light, suspending feeders (dawn, dusk, and into dark); yellow perch and bluegill
 * feed by day, closer to the bottom and cover. Grounded in the consensus cited in SOURCES.md.
 */

private enum class PanfishPhase(val label: String) {
    COLD("Cold water"),
    SPAWN("Spring / spawn"),
    SUMMER("Summer"),
    FALL("Fall"),
}

private fun panfishPhase(waterF: Double, month: Int): PanfishPhase {
    val cooling = month >= 9
    return when {
        waterF < 48 -> PanfishPhase.COLD
        cooling && waterF < 66 -> PanfishPhase.FALL
        waterF >= 70 -> PanfishPhase.SUMMER
        !cooling && waterF >= 48 -> PanfishPhase.SPAWN
        else -> PanfishPhase.SUMMER
    }
}

private fun panfishMoodClause(w: WeatherRead, mood: Mood): String = when {
    w.raining -> " Rain is dimming the light, so the school is up and feeding. Get on it."
    mood == Mood.FEEDING -> " A front is moving in and the pressure is dropping, so they are feeding right now. Get out ahead of it."
    mood == Mood.TOUGH -> " But it is a bright, high-pressure bluebird day, which shuts panfish down hard. Go small, slow, and a little deeper."
    mood == Mood.ROAMING -> " Wind and cloud have the school roaming the edges, so cover water until you find them."
    else -> " No big weather push today, so lean on first and last light."
}

private fun panfishWhen(windows: String, mood: Mood, lowLight: Boolean): String = buildString {
    append("Best window today is $windows. ")
    when {
        mood == Mood.FEEDING -> append("A front is coming, so get out ahead of it for the best bite of the stretch.")
        mood == Mood.TOUGH -> append("Bright day after a front, a tough panfish bite, so downsize and fish the low-light edges.")
        lowLight -> append("Dawn, dusk, and cloud cover are far and away your best odds with this one.")
        else -> append("Lean on first and last light.")
    }
}

private const val PANFISH_WHEN_MORE =
    "Panfish are schooling fish, so once you catch one, work that exact spot: there are usually many more. Falling pressure ahead of a storm turns them on, and a bright, high, calm bluebird day after a front is the toughest bite of all, sending them small and deep. Find the school and the size of the bite is set by the sky."

// ---- Yellow perch ---------------------------------------------------------------------

internal fun yellowPerchPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = panfishPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        PanfishPhase.COLD -> "Perch are schooled in the deeper basins, biting a small bait worked slow near the bottom."
        PanfishPhase.SPAWN -> "Perch are shallow and schooled up over sand and weed edges after their early spawn, feeding by day."
        PanfishPhase.SUMMER -> "Perch schools are roaming the weed edges, drop-offs, and rocky bottom, feeding on the bottom through the day."
        PanfishPhase.FALL -> "Perch schools are tightening up just off the rocky points and drop-offs, feeding hard before winter."
    }
    val headline = core + panfishMoodClause(w, mood)

    val tacticLine = when {
        mood == Mood.TOUGH -> "Downsize and fish the bottom: a small jig or a piece of worm on a drop-shot, crawled slow along the deeper edges."
        else -> "Work the bottom near cover: a small jig, a minnow or worm on a bottom rig, or a tiny spoon jigged over sand, rock, and weed edges."
    }
    val whyBrief = "Perch are daytime, bottom-oriented school feeders, so they hunt along the bottom near cover and stack up where the food is."

    val (whereBrief, whereMore) = when (phase) {
        PanfishPhase.FALL -> Pair(
            "Fish just off the rocky points and drops. Fall perch tighten into big schools there.",
            "As it cools, perch concentrate off rocky points, gravel, and the first drop-offs, feeding up for winter. Find one school and you have found a lot of fish, so anchor on it and work it.",
        )
        PanfishPhase.SUMMER, PanfishPhase.SPAWN -> Pair(
            "Fish the bottom along weed edges, sand flats, and rock. Perch roam these in loose schools by day.",
            "Perch are almost always near the bottom, moving along weed edges, sand, gravel, and drop-offs looking for food. They can be spread out, so keep moving until you hit a school, then stay on it.",
        )
        PanfishPhase.COLD -> Pair(
            "Fish the deeper basins near the bottom. In the cold, perch school tight and deep.",
            "They pack into the deeper flats and basins and barely chase, so put a small bait right on the bottom and work it slowly. A classic ice-fishing target for the same reason.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        panfishWhen(windows, mood, lowLight = false), PANFISH_WHEN_MORE,
        howMore = "Perch feed on the bottom, so keep your bait down there: a small jig, a worm or minnow on a bottom rig, or a little spoon. They bite in the daytime, so you do not have to wait for evening. When you catch one, drop right back down, because the school is right there.",
        whyMore = "Yellow perch are daytime, bottom-hugging schooling fish. They travel in packs along weed edges, sand, and rock, eating small invertebrates and minnows off the bottom, and they gang up tighter as the water cools. Unlike crappie they feed well in daylight, so a bright day is less of a killer, though a falling barometer still turns them on and a hard bluebird day still slows them. Find the school and stay on it.",
    )
}

// ---- White perch ----------------------------------------------------------------------

internal fun whitePerchPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = panfishPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        PanfishPhase.COLD -> "White perch are schooled deep in the basins, biting a small bait worked slow near the bottom."
        PanfishPhase.SPAWN -> "White perch are schooled shallow after the spawn, feeding best in the evening and low light."
        PanfishPhase.SUMMER -> "White perch are suspended over deeper water in big schools by day and pushing shallow to feed at dusk and into dark."
        PanfishPhase.FALL -> "White perch are in tight schools on the drops and points, feeding hard in the low-light hours."
    }
    val headline = core + panfishMoodClause(w, mood)

    val tacticLine = when {
        mood == Mood.TOUGH -> "Go small and find the depth: a tiny jig or a spoon counted down to the suspended school, worked slow, and save your effort for the evening."
        else -> "Fish a small jig, a spoon, or a worm and count it down to the school, and work the shallows hard at dusk and into dark."
    }
    val whyBrief = "White perch are low-light schooling feeders, so they suspend deep by day and push shallow to feed at dusk and after dark."

    val (whereBrief, whereMore) = when (phase) {
        PanfishPhase.SUMMER, PanfishPhase.FALL -> Pair(
            "Find the suspended schools over deeper water, and the shallows at dusk. White perch roam in big schools.",
            "By day they suspend over the deeper basins and drops, so watch your sonar and count a lure down to their level. At dusk and into dark they push up onto flats, points, and shorelines to feed, which is the easiest time to catch them.",
        )
        PanfishPhase.SPAWN -> Pair(
            "Fish the shallow flats and shoreline in the evening. Post-spawn white perch feed up in low light.",
            "They gather in the shallows to feed as the light drops, so work the flats, points, and shoreline at dusk. During the bright of the day they slide back to deeper water.",
        )
        PanfishPhase.COLD -> Pair(
            "Fish the deep basins near the bottom. In the cold, white perch school tight and deep.",
            "They pack into the deeper water and barely move, so put a small bait on the bottom and work it slowly, and expect the best of it in the low-light window.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        panfishWhen(windows, mood, lowLight = true), PANFISH_WHEN_MORE,
        howMore = "White perch suspend, so depth control is everything: count a small jig or spoon down until you find them, then repeat that count. They feed by feel and sight in dim water, so the evening and after-dark bite in the shallows is the fastest fishing. When you find the school, it is often huge.",
        whyMore = "White perch are schooling, low-light feeders. They spend the bright of the day suspended over deeper water and move shallow to feed hard at dusk and into the night, which is when they are easiest to catch. They travel in big schools, so it is feast or famine: nothing until you find them, then a fish a cast. A falling barometer helps; a bright bluebird day is the toughest.",
    )
}

// ---- Black crappie --------------------------------------------------------------------

internal fun blackCrappiePlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = panfishPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        PanfishPhase.COLD -> "Crappie are schooled deep and slow over the basins and near deep brush, biting a slow-falling jig."
        PanfishPhase.SPAWN -> "Crappie have moved into the shallow bays and onto the points to spawn, holding tight to brush, docks, and weed edges."
        PanfishPhase.SUMMER -> "Crappie are suspended over deeper structure and brush by day, feeding best at dawn, dusk, and after dark."
        PanfishPhase.FALL -> "Crappie are schooling on the midlake shoals and rock piles, feeding up in the low-light hours."
    }
    val headline = core + panfishMoodClause(w, mood)

    val tacticLine = when {
        mood == Mood.TOUGH -> "Slow way down: a small jig or a minnow under a float, held dead-still beside brush, and save your time for first and last light."
        else -> "Drop a small jig or a minnow under a float tight to brush, docks, and weed edges, and count a jig down to any suspended school."
    }
    val whyBrief = "Crappie are the classic low-light, suspending feeders around cover, so dawn, dusk, and after dark are best, and a bright bluebird sky shuts them off fastest of any panfish."

    val (whereBrief, whereMore) = when (phase) {
        PanfishPhase.SPAWN -> Pair(
            "Fish the shallow bays and points, tight to brush and docks. Crappie are spawning and holding on cover.",
            "They move into marshy bays and then onto rocky points to spawn, and they hug cover the whole time: brush, cattails, lily pad edges, and dock pilings. Drop a jig or minnow right against it.",
        )
        PanfishPhase.SUMMER -> Pair(
            "Find the suspended schools over deeper brush and structure. Crappie hang off the bottom, not on it.",
            "In summer crappie suspend over deeper brush piles, standing timber, and structure, often halfway down. Watch your sonar and count a lure to their depth. They feed best in low light, so dawn, dusk, and night are prime.",
        )
        PanfishPhase.FALL -> Pair(
            "Fish the midlake shoals and rock piles. Fall crappie school up on that structure.",
            "As it cools, crappie pull out to midlake shoals, humps, and rock piles and school tightly, feeding up for winter. Find that structure and the school stacks on it.",
        )
        PanfishPhase.COLD -> Pair(
            "Fish deep and slow near brush and the basins. Cold crappie barely move.",
            "They school in the deeper water near brush and structure and want a very slow-falling bait right in front of them, which is why a small tungsten jig shines in cold water and through the ice.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        panfishWhen(windows, mood, lowLight = true), PANFISH_WHEN_MORE,
        howMore = "Crappie have soft, papery mouths and feed up, so a small jig or a minnow suspended under a float and held nearly still is the whole game: let them come up to it. A slow fall matters, more so in cold water. They suspend, so the number-one skill is finding the right depth and keeping the bait there.",
        whyMore = "Black crappie are low-light, suspending school feeders that live around cover. Their big eyes are made for dim water, so they feed hardest at dawn, dusk, and after dark, and a bright, high-pressure bluebird day after a front shuts them off faster than any other panfish. They hold on brush and structure and suspend off the bottom, so depth control and cover are the two things that matter.",
    )
}

// ---- Panfish (sunfish / bluegill) -----------------------------------------------------

internal fun sunfishPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = panfishPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        PanfishPhase.COLD -> "Bluegill are schooled in the deeper water and slow in the cold, taking a tiny bait worked gently near the bottom."
        PanfishPhase.SPAWN -> "Bluegill are up in the shallow, warm bays on their spawning beds, thick and easy to catch. This is prime time."
        PanfishPhase.SUMMER -> "Bluegill are schooled along the weedlines, docks, and rock piles, feeding shallow through the day and pulling a little deeper in the heat."
        PanfishPhase.FALL -> "Bluegill are still on the weedlines and edges, schooled up and feeding through the day as it cools."
    }
    val headline = core + panfishMoodClause(w, mood)

    val tacticLine = when {
        mood == Mood.TOUGH -> "Go tiny and slow: a small piece of worm on a light hook under a float, or the smallest jig, worked gently near cover."
        else -> "Fish a worm or a tiny jig under a small float near cover, or a little spinner or fly for the aggressive ones. Small hooks are the key."
    }
    val whyBrief = "Bluegill are warm-loving, shallow, daytime feeders that hold near cover, so warm shallows and small baits are the whole game."

    val (whereBrief, whereMore) = when (phase) {
        PanfishPhase.SPAWN -> Pair(
            "Fish the shallow, warm bays over the beds. Bluegill spawn in colonies you can often see.",
            "In late spring they fan out saucer-shaped beds in the warm, protected shallows, often in colonies, and you can see them on a calm day. They are aggressive on the beds, so a bait dropped near one rarely lasts long.",
        )
        PanfishPhase.SUMMER, PanfishPhase.FALL -> Pair(
            "Fish near cover: weedlines, docks, and rock piles. Bluegill school along these by day.",
            "After the spawn they school along weed edges, under docks, and around rock and wood, feeding through the day and sliding a bit deeper in the heat. Wherever there is shallow cover and shade, there are usually bluegill.",
        )
        PanfishPhase.COLD -> Pair(
            "Fish the deeper water near the bottom, slow. Cold bluegill school deep and barely move.",
            "They pull into the deeper basins and edges and want a tiny bait moved very slowly, which is why the smallest ice jigs are the tool in cold water.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        panfishWhen(windows, mood, lowLight = false), PANFISH_WHEN_MORE,
        howMore = "Bluegill have tiny mouths, so small hooks and small baits catch far more of them: a piece of worm, a small jig, or a little fly. They feed in the daytime near cover, so you do not have to wait for evening. They school by size, so if you are catching small ones, move until you find the slab school.",
        whyMore = "Sunfish and bluegill are warm-water, shallow, daytime feeders that hold near cover and school by size. Their year is simple: up in the warm shallows to spawn in late spring, then along weedlines, docks, and rock through summer and fall, and out to deeper water when it turns cold. They are the least weather-sensitive panfish and the most warmth-driven, so warm shallows and small baits put a lot of them in the bucket.",
    )
}
