package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Smallmouth bass — deep content. Smallmouth are the rock-and-crayfish cousin of the
 * largemouth: they relate to hard bottom (rock, gravel, boulders, reefs) instead of
 * weeds and wood, run a little cooler, and love wind and current. The plan combines the
 * season phase (water temp + calendar) with today's [Mood] (pressure, front, wind, sky).
 * Grounded in the smallmouth-behavior consensus cited in SOURCES.md.
 */

private enum class SmallmouthPhase(val label: String) {
    COLD("Cold water"),
    PRESPAWN("Pre-spawn"),
    SPAWN("Spawn"),
    POSTSPAWN("Post-spawn"),
    SUMMER("Summer"),
    FALL_FEED("Fall feed-up"),
    LATE_FALL("Late fall"),
}

private fun smallmouthPhase(waterF: Double, month: Int): SmallmouthPhase {
    val cooling = month >= 9
    return when {
        waterF < 45 -> SmallmouthPhase.COLD
        cooling && waterF < 52 -> SmallmouthPhase.LATE_FALL
        cooling && waterF < 70 -> SmallmouthPhase.FALL_FEED
        waterF < 56 -> SmallmouthPhase.PRESPAWN
        waterF < 65 -> SmallmouthPhase.SPAWN
        waterF < 70 -> SmallmouthPhase.POSTSPAWN
        else -> SmallmouthPhase.SUMMER
    }
}

internal fun smallmouthPlan(
    sp: Species,
    c: Conditions,
    w: WeatherRead,
    date: LocalDate,
    timing: DayTiming?,
): GamePlan {
    val phase = smallmouthPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        SmallmouthPhase.COLD -> "Smallmouth are deep and barely moving in the cold, hugging rock on the deepest structure near their winter holes."
        SmallmouthPhase.PRESPAWN -> "This is the year's best big-fish window. Smallmouth are staging on rock and gravel just outside the spawning flats, feeding up hard before they bed."
        SmallmouthPhase.SPAWN -> "Smallmouth are up shallow on rock and gravel, guarding beds in the wind-protected pockets."
        SmallmouthPhase.POSTSPAWN -> "Smallmouth are worn out and scattered around the rocky areas where they spawned."
        SmallmouthPhase.SUMMER -> "Smallmouth are locked onto main-lake rock: humps, points, and reefs, eating crayfish and baitfish. They pull shallow in low light and hold deeper through the day."
        SmallmouthPhase.FALL_FEED -> "Smallmouth are chasing schools of baitfish toward the shallows, feeding hard to fatten up for winter."
        SmallmouthPhase.LATE_FALL -> "A few big smallmouth are holding on deep rock near the main lake, waiting to ambush the last baitfish of the year."
    }
    val moodClause = when {
        w.heavyRain -> " A downpour is muddying the water, so slow down, hug the rock, and throw something they can find in the murk."
        w.lightRain -> " Rain is falling. It dims the light and washes food in, so they are up and feeding. Get on it."
        mood == Mood.FEEDING -> " A front is moving in and the pressure is dropping, so they are feeding hard right now. Get out ahead of it."
        mood == Mood.TOUGH -> " But it is bright and high-pressure behind a front, so they are sluggish and pinned to the rock. Slow way down."
        mood == Mood.ROAMING -> " There is wind and cloud, which smallmouth love, so they are up and hunting. Fish the windblown rock and cover water."
        else -> " No big weather push today, so lean on first and last light."
    }
    val headline = core + moodClause

    val tacticLine = if (w.heavyRain) {
        "Go bold on the rock in the stained water: a dark or bright crankbait or a bladed jig with some thump, worked slow along the rock and near any inflow or current."
    } else if (mood == Mood.TOUGH) {
        "Downsize on the rock: a drop-shot, a small tube, or a hair jig on light line, crawled slow across the deepest hard bottom."
    } else when (phase) {
        SmallmouthPhase.FALL_FEED, SmallmouthPhase.PRESPAWN ->
            "Cover water with a jerkbait, a crawfish or fire-tiger crankbait, or a spinnerbait, and reel it steady across the rock. Walk a topwater back over any fish that break the surface."
        SmallmouthPhase.SPAWN, SmallmouthPhase.POSTSPAWN ->
            "Work a tube, a Ned rig, or a drop-shot slow over the gravel and rock. A wacky-rigged stick worm is the easy button around the beds."
        SmallmouthPhase.SUMMER ->
            "Early and late, walk a topwater over the rock. Midday, drag a tube, drop-shot, or football jig slow along the deep humps and points."
        SmallmouthPhase.COLD, SmallmouthPhase.LATE_FALL ->
            "Hop a blade bait or hair jig on deep rock, or twitch a jerkbait with long pauses, keeping it right in their face."
    }

    val whyBrief = when {
        w.heavyRain -> "Muddy water cuts visibility, so smallmouth pull tight to rock and hunt by feel and vibration instead of sight."
        w.lightRain -> "Rain dims the light and dimples the surface, so smallmouth drop their guard and feed, and runoff washes food and oxygen onto the rock."
        mood == Mood.FEEDING -> "Smallmouth feed hard as the pressure falls ahead of a storm. It is the fast change and the weather it signals they react to, not the exact number."
        mood == Mood.TOUGH -> "After a front the bright bluebird sky is the trouble. Smallmouth are light-sensitive, so they slide deeper onto the rock and barely feed for a day or two."
        mood == Mood.ROAMING -> "Wind and cloud cut the light and stack baitfish on the windblown rock, and smallmouth are a wind fish above all: the chop is when they roam and feed boldest."
        else -> "Light runs a calm day. Smallmouth push onto shallow rock to feed in low light and slide deeper when the sun is high."
    }

    val (whereBrief, whereMore) = when (phase) {
        SmallmouthPhase.COLD -> Pair(
            "Fish deep rock and slow. In the cold, smallmouth barely move, so they stack on the deepest hard bottom and will not chase.",
            "Look for the deepest rock near your summer spots: deep points, sharp rock drops, and boulder piles. They bunch up tight, so once you catch one, pick that exact spot apart.",
        )
        SmallmouthPhase.PRESPAWN -> Pair(
            "Fish rock and gravel just outside the spawning flats. The biggest smallmouth of the year are staging to feed before the spawn.",
            "They hold on the last hard-bottom structure before the spawning pockets: secondary points, gravel flats, and rock transitions. The warmest water pulls them up first, so favor wind-protected areas and dark rock that soaks up the sun.",
        )
        SmallmouthPhase.SPAWN -> Pair(
            "Fish shallow rock and gravel. Smallmouth are making beds in one to six feet of water in the protected pockets.",
            "Look on gravel and rock flats out of the wind, often near a bit of cover or a lone boulder. You can often see the beds. Handle the fish gently so they can finish spawning.",
        )
        SmallmouthPhase.POSTSPAWN -> Pair(
            "Fish the rocky areas near the beds. Smallmouth are worn out and resting close to where they spawned.",
            "Check rock points, chunk rock, and the first drop off the spawning flats. They can be moody right after spawning, so you may have to work for bites before they group back up.",
        )
        SmallmouthPhase.SUMMER -> Pair(
            "Fish main-lake rock: humps, points, and reefs. Shallow rock is best early and late, deeper rock through the day.",
            "Summer smallmouth are predictable and locked on structure. Fish shallow rock at dawn and dusk, then move out to deep humps, reefs, and offshore rock when the sun climbs. Crayfish and schools of young baitfish are the food, so match them.",
        )
        SmallmouthPhase.FALL_FEED -> Pair(
            "Fish the shallower rock and flats where baitfish gather. Smallmouth are herding bait to fatten up.",
            "Wind is your friend now: it stacks plankton and baitfish on the windblown rock, and the smallmouth pile in behind. Start where rock meets a drop or a point pinches the bait, and cover water until you find the school.",
        )
        SmallmouthPhase.LATE_FALL -> Pair(
            "Fish the deep main-lake rock near the last baitfish. Fewer fish now, but the ones left are big.",
            "Look at steep rock, main-lake points, and deep boulder piles next to deep water. The remaining smallmouth sit on these and grab the last of the bait. It is a quality-over-quantity game.",
        )
    }

    val whenBrief = buildString {
        append("Best window today is $windows. ")
        when (mood) {
            Mood.FEEDING -> append("A front is coming, so get out now before it hits. The hours right before bad weather are the best feeding of the stretch.")
            Mood.TOUGH -> append("It is bright and high-pressure after a front, a tough bite, so the fish will really only feed at first and last light.")
            Mood.ROAMING -> append("Wind and cloud stretch the good hours out, so you have a longer window than usual to work with.")
            Mood.STEADY -> append("No big weather change today, so lean on first and last light.")
        }
    }
    val whenMore = "Smallmouth feed hardest when a storm is on the way and the pressure is falling, and they slow down for a day or two once it passes and the sky goes bright and blue. More than most fish, wind is the key: a good chop on the rock breaks up the light and turns them on, while dead-calm bright days are the toughest."

    val howBrief = tacticLine
    val howMore = buildString {
        when (mood) {
            Mood.TOUGH -> append("Because it is bright after a front, downsize and fish the deepest rock. A drop-shot or a light tube crawled slow is your best bet when they will not chase. ")
            Mood.ROAMING -> append("Use the wind: fish the rock it is blowing into and let the chop hide you. It is prime time for a moving lure like a jerkbait or a crankbait. ")
            Mood.STEADY -> if (w.clear) append("It is clear and calm, so the fish are spooky. Use lighter line, natural colors, and longer casts. ") else Unit
            Mood.FEEDING -> append("They are aggressive, so fish fast and cover water to find the active ones. ")
        }
        append(
            if (w.clear && !w.overcast) "In bright, clear water use natural colors: brown and green-pumpkin for crayfish, shad and perch tones for baitfish."
            else "In gray or stained water go bolder, so they can find it by its outline and vibration.",
        )
        append(" Smallmouth pull harder than anything their size, so keep a bend in the rod and slow down where you get bit, because they group up on the rock.")
    }

    val whyMore = "Smallmouth are hard-bottom ambush hunters built around rock and crayfish, and they run a bit cooler and love current and wind more than largemouth. A fast-falling barometer ahead of a storm switches on a short, hard feed. The bright, high-pressure bluebird day after a front does the opposite, pushing them deeper onto the rock and quiet for a day or two. Wind is the single best condition for them: it breaks up the light so they feed boldly and stacks baitfish on the windblown rock where the smallmouth wait."

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
