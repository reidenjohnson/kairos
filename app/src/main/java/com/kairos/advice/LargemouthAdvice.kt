package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Largemouth bass — the deep content, written plainly. Bass are the clearest case of
 * "the season runs the fish and the weather runs the day," so the plan comes from the
 * water temperature and the calendar (which phase they're in) and is then adjusted for
 * today's pressure, sky, and wind.
 *
 * Each section leads with a short, do-this sentence a beginner can act on; the longer
 * detail is there for anyone who wants the reasoning. Consensus angling knowledge —
 * guidance, not a guarantee.
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

private fun bassColorNote(w: WeatherRead): String = when {
    w.clear && !w.overcast ->
        "It's bright and clear, so use natural, lifelike colors — green, watermelon, or something that looks like a real fish. Loud colors spook them in clear water."
    w.overcast ->
        "It's gray and overcast, so bass find their food by its outline against the sky — use bolder colors like white, chartreuse (bright yellow-green), or black."
    else ->
        "Natural colors like green work in most light; brighten up a shade if the water is muddy or stained."
}

internal fun largemouthPlan(
    sp: Species,
    c: Conditions,
    w: WeatherRead,
    date: LocalDate,
    timing: DayTiming?,
): GamePlan {
    val phase = bassPhase(c.waterF, date.monthValue)
    val windows = windowsText(timing, Side.FISH)

    // WHERE
    val (whereBrief, whereMore) = when (phase) {
        BassPhase.COLD -> Pair(
            "Fish deep and slow. In cold water bass hardly move, so they sit in the deepest spots and won't chase.",
            "Look for the deepest water near the areas you'd fish in summer — deep points, sharp drop-offs, and channel edges. They bunch up tight in schools down there, so once you catch one, work that exact spot hard because there are usually more.",
        )
        BassPhase.PRESPAWN -> Pair(
            "Fish the edges of the shallow bays. The biggest bass of the year are moving up to feed before they spawn.",
            "They stage on the way into the spawning coves — the last drop-off before the flat, points leading in, and any hard bottom. The warmest water pulls them up first, so focus on wind-protected north banks and dark bottoms that soak up the sun.",
        )
        BassPhase.SPAWN -> Pair(
            "Fish shallow, right against the bank. Bass are making beds to lay eggs, so they're up in one to four feet of water.",
            "Look in the backs of coves and on flats that are protected from the wind, over hard bottom. You can often see the beds — light circles on the bottom. The big females sit near cover just off the beds; fish slow and handle them gently so they can finish spawning.",
        )
        BassPhase.POSTSPAWN -> Pair(
            "Fish shallow cover near the spawning areas. Bass are worn out and resting close to where they spawned.",
            "Check docks, points, and shallow cover between the spawning flats and deeper water. They also raid bluegill beds now, so anywhere you find bluegill, bass aren't far behind. They can be moody — you may have to work for bites.",
        )
        BassPhase.SUMMER -> Pair(
            "Two options: shade or deep. In summer heat, fish the shade early and late, and the deep spots in the middle of the day.",
            "The shallow bass live in shade — under docks, fallen trees, and mats of weeds — and bite best at dawn and dusk. The rest hold on deeper spots like weed edges and drop-offs, which is where to go when the sun is high and the shallow bite dies.",
        )
        BassPhase.FALL_FEED -> Pair(
            "Fish shallow in the backs of coves and creeks. The water is cooling, so bass follow schools of baitfish (small fish they eat) up shallow to fatten up for winter.",
            "The wind is your friend right now — it pushes the baitfish against windblown banks and points, and the bass are right underneath. Start where a cove pinches down or a creek channel swings near the bank; that's where the little fish get funneled and the bass gang up to ambush them.",
        )
        BassPhase.LATE_FALL -> Pair(
            "Fish the sharpest drop-offs near deep water. Fewer bass now, but the ones left are big and waiting to ambush.",
            "Look at steep banks, main-lake points, and rocky spots that fall off fast into deep water. The bass sit on these and grab the last baitfish of the year. It's a quality-over-quantity game — fewer bites, bigger fish.",
        )
    }

    // WHEN
    val whenBrief = buildString {
        append("Best window today is $windows. ")
        when {
            w.frontIncoming -> append("A storm front is coming — get out now, before it hits. The hours right before bad weather are the best feeding time of the year.")
            w.falling -> append("The pressure is dropping, which turns bass on — the next few hours are prime.")
            w.bluebird -> append("It's a bright, calm day after a front — a tough bite — so the fish will only really feed at first and last light.")
            phase == BassPhase.SUMMER -> append("In summer, dawn and dusk are far and away your best shot in the shallows.")
            else -> append("No big weather change today, so lean on first and last light.")
        }
    }
    val whenMore =
        "Bass feed hardest when a storm is on the way and the pressure is falling. Once the storm passes and the sky goes bright and blue (a 'bluebird' day), they get sluggish for a day or two. Cloud cover and a little wind stretch the good hours out; bright, calm sun shrinks them to early and late."

    // HOW
    val howBrief = when (phase) {
        BassPhase.COLD, BassPhase.LATE_FALL ->
            "Go slow. Drag a soft-plastic bait or a jig along the bottom, or twitch a jerkbait (a minnow-shaped lure) with long pauses. Cold bass won't chase, so keep it in front of them."
        BassPhase.PRESPAWN, BassPhase.FALL_FEED ->
            "Cover water with a lure that looks like a small fish — a shallow crankbait, spinnerbait, or lipless crankbait. When you see bass chasing bait on the surface, throw a topwater lure into the middle of it."
        BassPhase.SPAWN, BassPhase.POSTSPAWN ->
            "Go slow and quiet. Toss a soft plastic worm right into likely spots and let it sit. Bass here want an easy, close target, not a fast chase."
        BassPhase.SUMMER ->
            "Two speeds: a topwater or frog over the weeds early and late, then slow down midday with a worm or jig on the deeper spots."
    }
    val howMore = buildString {
        when {
            w.bluebird -> append("Since it's a bright day after a front, downsize and slow down: pitch a small, subtle bait into the thickest shade and cover. The sun pushes bass tight to cover and they won't move far to eat. ")
            w.windy || w.breezy -> append("Use the wind — fish the banks it's blowing into, and let the waves hide you. It's a great time for a moving lure. ")
            w.calm && w.clear -> append("It's slick and clear, so the fish are spooky: use lighter line, natural colors, longer casts, and stay quiet. ")
        }
        append(bassColorNote(w))
        append(" And remember: when you catch one, slow down and pick that spot apart — bass group up, so there are usually more right there.")
    }

    // WHY
    val whyBrief = when {
        w.frontIncoming || w.falling -> "Falling pressure before a storm makes bass feed hard, because they sense the weather about to shut things down."
        w.bluebird -> "After a front, the bright, high-pressure sky makes bass cautious and glued to cover — that's the classic 'they bit yesterday' day."
        w.overcast || w.windy -> "Clouds and wind dim the light and ripple the surface, so bass roam and hunt instead of hiding — that's why moving lures work."
        else -> "With steady weather, the daily rhythm rules: low light lets bass move up and feed, bright sun sends them to shade and deep water."
    }
    val whyMore =
        "Bass are ambush hunters that rely on cover and low light. Weather changes — mainly a falling barometer ahead of a storm — flip a short, intense feeding switch. Bright, stable, high-pressure days do the opposite and make them hunker down. Everything above is just reading which of those situations you're in today."

    val headline = when (phase) {
        BassPhase.FALL_FEED -> "Bass are shallow, chasing baitfish to fatten up for winter. Fish the backs of coves — it's a run-and-gun day."
        BassPhase.SPAWN -> "Bass are shallow on their beds. Slow down, fish close to the bank, and you can even sight-fish them."
        BassPhase.SUMMER -> "Beat the heat: fish shade early and late, and the deep spots in the middle of the day."
        BassPhase.PRESPAWN -> "The biggest bass of the year are feeding up shallow before the spawn. Prime time for a giant."
        BassPhase.COLD, BassPhase.LATE_FALL -> "Cold water means fewer bites — but bigger fish. Fish deep and slow, and be patient."
        BassPhase.POSTSPAWN -> "Bass are worn out and moody after spawning. Fish slow in shallow cover and low light."
    }

    return GamePlan(
        headline = headline,
        phaseLabel = phase.label,
        sections = listOf(
            PlanSection(PlanKind.WHERE, "Where", whereBrief, whereMore),
            PlanSection(PlanKind.WHEN, "When", whenBrief, whenMore),
            PlanSection(PlanKind.HOW, "How", howBrief, howMore),
            PlanSection(PlanKind.WHY, "Why", whyBrief, whyMore),
        ),
    )
}
