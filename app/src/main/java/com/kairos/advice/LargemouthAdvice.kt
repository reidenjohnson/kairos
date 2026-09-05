package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Largemouth bass — the deep content. Bass are the clearest case of "the season runs
 * the fish and the weather runs the day," so the plan combines the phase (from water
 * temp + calendar) with today's [Mood] (from pressure, front, wind, sky). The card
 * fields ([GamePlan.headline], [GamePlan.tacticLine], [GamePlan.whyBrief]) fold the
 * weather in so the same September week reads differently as conditions change.
 */

private enum class BassPhase(val label: String) {
    COLD("Cold water"),
    PRESPAWN("Pre-spawn"),
    SPAWN("Spawn"),
    POSTSPAWN("Post-spawn"),
    SUMMER("Summer"),
    FALL_FEED("Fall feed-up"),
    LATE_FALL("Late fall"),
}

private fun bassPhase(waterF: Double, month: Int): BassPhase {
    val cooling = month >= 9
    return when {
        waterF < 46 -> BassPhase.COLD
        cooling && waterF < 55 -> BassPhase.LATE_FALL
        cooling && waterF < 72 -> BassPhase.FALL_FEED
        waterF < 58 -> BassPhase.PRESPAWN
        waterF < 66 -> BassPhase.SPAWN
        waterF < 72 -> BassPhase.POSTSPAWN
        else -> BassPhase.SUMMER
    }
}

internal fun largemouthPlan(
    sp: Species,
    c: Conditions,
    w: WeatherRead,
    date: LocalDate,
    timing: DayTiming?,
): GamePlan {
    val phase = bassPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    // What they're doing + where — varies by phase.
    val core = when (phase) {
        BassPhase.COLD -> "Bass are deep and barely moving in the cold, holding on the deepest spots near your summer areas."
        BassPhase.PRESPAWN -> "The year's biggest bass are feeding up on the edges of the shallow bays before they spawn."
        BassPhase.SPAWN -> "Bass are up shallow on their beds, tight against the bank in the protected pockets."
        BassPhase.POSTSPAWN -> "Bass are worn out and scattered in shallow cover near where they spawned."
        BassPhase.SUMMER -> "Bass are split — in the shade shallow early and late, out on deeper edges through midday."
        BassPhase.FALL_FEED -> "Bass are shallow in the backs of coves, chasing schools of smaller fish to fatten up for winter."
        BassPhase.LATE_FALL -> "A few big bass are holding on the sharpest drops near deep water, waiting to ambush the last prey of the year."
    }
    val moodClause = when {
        w.heavyRain -> " A downpour's muddying the water — slow down, hug cover, and throw something they can find in the murk."
        w.lightRain -> " Rain's falling — it dims the light and washes food into the shallows, so they're up and feeding. Get on it."
        mood == Mood.FEEDING -> " A front's moving in and the pressure's falling, so they're feeding hard right now — get out ahead of it."
        mood == Mood.TOUGH -> " But it's a bright, high-pressure day behind a front, so they're sluggish and pinned to cover — slow way down."
        mood == Mood.ROAMING -> " Clouds and wind have them up and hunting, so keep moving and cover water."
        else -> " No big weather push today, so lean on first and last light."
    }
    val headline = core + moodClause

    // What to throw + how — phase set, overridden to finesse on a tough day.
    val tacticLine = if (w.heavyRain) {
        "Go bold in the stained water — a dark or bright bait with a big profile or a rattle they can find by feel, worked slow and tight to cover and around any inflow or current."
    } else if (mood == Mood.TOUGH) {
        "Downsize and slow down: a small finesse worm or a light jig, crawled slow and tight to the thickest shade and cover."
    } else when (phase) {
        BassPhase.FALL_FEED, BassPhase.PRESPAWN ->
            "Throw a lipless crankbait, a spinnerbait, or a shallow crankbait and reel it steady through the shallows; when fish break the surface, walk a topwater back over them."
        BassPhase.SPAWN, BassPhase.POSTSPAWN ->
            "Pitch a soft-plastic worm or a creature bait into cover and let it sit dead-still — a wacky-rigged stick worm is the easy button."
        BassPhase.SUMMER ->
            "Early and late, walk a topwater or run a frog over the weeds; midday, drag a worm or a football jig slow along the deeper edges."
        BassPhase.COLD, BassPhase.LATE_FALL ->
            "Drag a jig or soft-plastic slow on the bottom, hop a blade bait, or twitch a jerkbait with long pauses — keep it right in their face."
    }

    val whyBrief = when {
        w.heavyRain -> "Heavy rain muddies the water and cuts visibility, so bass pull tight to cover and hunt by feel and vibration instead of sight."
        w.lightRain -> "Rain dims the light and dimples the surface, so bass drop their guard and feed shallow — and runoff washes food and oxygen into the edges."
        mood == Mood.FEEDING -> "Bass feed hard as the pressure falls ahead of a storm — it's the fast change and the weather it signals they react to, not the exact number."
        mood == Mood.TOUGH -> "After a front the bright bluebird sky is the trouble: bass aren't built for sudden bright light, so they pull off the bank, bury in cover, and barely feed for a day or two."
        mood == Mood.ROAMING -> "Wind and clouds cut the light, so bass lose their fear of being exposed and roam shallow to hunt — and the chop stacks plankton on the windward bank, pulling baitfish, and the bass, in behind it."
        else -> "Light runs a calm day — bass push shallow to feed in low light and slide to shade and depth when the sun is high."
    }

    // ---- Full-page sections ----
    val (whereBrief, whereMore) = when (phase) {
        BassPhase.COLD -> Pair(
            "Fish deep and slow. In cold water bass hardly move, so they sit in the deepest spots and won't chase.",
            "Look for the deepest water near the areas you'd fish in summer — deep points, sharp drop-offs, and channel edges. They bunch up tight down there, so once you catch one, work that exact spot hard because there are usually more.",
        )
        BassPhase.PRESPAWN -> Pair(
            "Fish the edges of the shallow bays. The biggest bass of the year are moving up to feed before they spawn.",
            "They stage on the way into the spawning coves — the last drop-off before the flat, the points leading in, and any hard bottom. The warmest water pulls them up first, so favor wind-protected north banks and dark bottoms that soak up the sun.",
        )
        BassPhase.SPAWN -> Pair(
            "Fish shallow, right against the bank. Bass are making beds to lay eggs in one to four feet of water.",
            "Look in the backs of coves and on flats protected from the wind, over hard bottom. You can often see the beds as light circles on the bottom. The big females sit near cover just off the beds; fish slow and handle them gently so they can finish spawning.",
        )
        BassPhase.POSTSPAWN -> Pair(
            "Fish shallow cover near the spawning areas. Bass are worn out and resting close to where they spawned.",
            "Check docks, points, and shallow cover between the spawning flats and deeper water. They also raid bluegill beds now, so wherever you find bluegill, bass aren't far behind. They can be moody — you may have to work for bites.",
        )
        BassPhase.SUMMER -> Pair(
            "Two options: shade or deep. Fish the shade early and late, and the deeper edges in the middle of the day.",
            "The shallow bass live in shade — under docks, fallen trees, and weed mats — and bite best at dawn and dusk. The rest hold on deeper edges and drop-offs, which is where to go when the sun is high and the shallow bite dies.",
        )
        BassPhase.FALL_FEED -> Pair(
            "Fish shallow in the backs of coves and creeks, where the bass are herding the smaller fish they're eating.",
            "The wind is your friend right now — it stacks the tiny plankton on the windward banks, the baitfish gather there to eat it, and the bass pile in behind them. Start where a cove pinches down or a creek channel swings near the bank; that's where the prey funnels and the bass gang up to ambush it.",
        )
        BassPhase.LATE_FALL -> Pair(
            "Fish the sharpest drop-offs near deep water. Fewer bass now, but the ones left are big and waiting to ambush.",
            "Look at steep banks, main-lake points, and rocky spots that fall off fast into deep water. The bass sit on these and grab the last of the prey. It's a quality-over-quantity game — fewer bites, bigger fish.",
        )
    }

    val whenBrief = buildString {
        append("Best window today is $windows. ")
        when (mood) {
            Mood.FEEDING -> append("A storm front is coming — get out now, before it hits. The hours right before bad weather are the best feeding time of the year.")
            Mood.TOUGH -> append("It's a bright, calm day after a front — a tough bite — so the fish will only really feed at first and last light.")
            Mood.ROAMING -> append("Clouds and wind stretch the good hours out, so you've got a longer window than usual to work with.")
            Mood.STEADY -> append("No big weather change today, so lean on first and last light.")
        }
    }
    val whenMore = "Bass feed hardest when a storm is on the way and the pressure is falling. Once it passes and the sky goes bright and blue, they get sluggish for a day or two. Cloud cover and a little wind stretch the good hours out; bright, calm sun shrinks them to early and late."

    val howBrief = tacticLine
    val howMore = buildString {
        when (mood) {
            Mood.TOUGH -> append("Because it's bright after a front, pick apart the thickest shade and cover — the sun drives bass tight and they won't move far to eat. ")
            Mood.ROAMING -> append("Use the wind: fish the banks it's blowing into, and let the waves hide you. It's prime time for a moving lure. ")
            Mood.STEADY -> if (w.clear) append("It's clear and calm, so the fish are spooky — use lighter line, natural colors, and longer casts. ") else Unit
            Mood.FEEDING -> append("They're aggressive, so fish fast and cover water to find the active ones. ")
        }
        append(
            if (w.clear && !w.overcast) "In the bright, clear water use natural, lifelike colors — green and shad tones."
            else "In gray or stained water go bolder — white, chartreuse, or black — so they can find it by its outline.",
        )
        append(" When you catch one, slow down and pick that spot apart — bass group up, so there are usually more.")
    }

    val whyMore = "Bass are ambush hunters that dislike bright light and lean on cover. A fast-falling barometer ahead of a storm switches on a short, hard feed — it's the rate of change and the coming weather, not the exact pressure. The bright, high-pressure bluebird day after a front does the opposite: they're light-sensitive, so they pull into cover and shade and go quiet for a day or two. Wind and clouds are a gift — they break up the light so bass feed bolder, add oxygen, and stack the plankton, and the baitfish that eat it, on the windward banks."

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
