package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Northern pike and chain pickerel — the weed-ambush predators. Both hang in and along
 * vegetation and hard cover, waiting to intercept prey, and both feed hardest on a
 * falling barometer and again in the fall as they bulk up for winter. Pike run cooler and
 * bigger and will pull to deeper cover in summer heat; pickerel are smaller, more cold- and
 * heat-tolerant, and stay in the shallow weeds. Grounded in the consensus cited in SOURCES.md.
 */

private enum class PredatorPhase(val label: String) {
    COLD("Cold water"),
    SPRING("Spring / post-spawn"),
    SUMMER("Summer"),
    FALL("Fall feed-up"),
}

private fun predatorPhase(waterF: Double, month: Int): PredatorPhase {
    val cooling = month >= 9
    return when {
        waterF < 40 -> PredatorPhase.COLD
        cooling && waterF < 64 -> PredatorPhase.FALL
        waterF >= 68 -> PredatorPhase.SUMMER
        else -> PredatorPhase.SPRING
    }
}

private fun predatorMoodClause(w: WeatherRead, mood: Mood): String = when {
    w.heavyRain -> " A downpour is muddying the water, so slow down, work tight to cover, and throw something with flash and thump they can find."
    w.lightRain -> " Rain is falling, which dims the light and gets them hunting. Get on it."
    mood == Mood.FEEDING -> " A front is moving in and the pressure is dropping, so they are feeding hard right now. Get out ahead of it."
    mood == Mood.TOUGH -> " But it is a bright, high-pressure bluebird day, so they are tight to cover and slow. Fish right in the weeds."
    mood == Mood.ROAMING -> " Wind and cloud have them roaming the edges and hunting, so cover water."
    else -> " No big weather push today, so lean on first and last light."
}

private fun predatorWhen(windows: String, mood: Mood): String = buildString {
    append("Best window today is $windows. ")
    when (mood) {
        Mood.FEEDING -> append("A front is coming, so get out ahead of it for the hardest feed of the stretch.")
        Mood.TOUGH -> append("Bright day after a front, so fish tight to cover and lean on first and last light.")
        Mood.ROAMING -> append("Wind and cloud stretch the good hours out, so you have a longer window to work the edges.")
        Mood.STEADY -> append("No big weather change, so lean on first and last light.")
    }
}

private const val PREDATOR_WHEN_MORE =
    "These are ambush hunters, so they feed in bursts. A falling barometer ahead of a storm sets off the best of it, and fall is a season-long feeding frenzy as they pack on weight for winter. A bright, high-pressure day after a front pins them into the thickest cover and slows the bite to first and last light."

internal fun pikePlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = predatorPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        PredatorPhase.COLD -> "Pike are slow but catchable in the cold, holding on deep weed edges and near timber next to the shallow flats."
        PredatorPhase.SPRING -> "Pike are in the shallow bays and weedy flats after the spawn, hungry and hunting the warm water."
        PredatorPhase.SUMMER -> "Pike are on the weedlines and points. The big ones pull to deeper, cooler cover through the heat; smaller ones stay in the shallow weeds."
        PredatorPhase.FALL -> "Pike are on a fall feeding frenzy, packing on weight for winter. This is the best time of year for a big one."
    }
    val headline = core + predatorMoodClause(w, mood)

    val tacticLine = when {
        w.heavyRain || mood == Mood.TOUGH ->
            "Slow down and work tight to the weeds: a spinnerbait or a soft jerkbait crawled along the edge, or a spoon fluttered beside cover."
        phase == PredatorPhase.FALL ->
            "Throw bigger now: a large spoon, a glide bait, a big swimbait, or a soft jerkbait worked along the weed edges and drops where they ambush."
        phase == PredatorPhase.SUMMER ->
            "Cover the weedlines with a weedless spoon, a spinnerbait, or a topwater over the shallow weeds, and try the deeper weed edges for the big ones midday."
        else ->
            "Fan-cast the shallow weeds and bays with a spoon, a spinnerbait, or a jerkbait, reeled steady so it flashes."
    }
    val whyBrief = when {
        mood == Mood.FEEDING -> "Pike feed hard as the pressure falls ahead of a storm, ambushing from the cover they wait in."
        mood == Mood.TOUGH -> "A bright bluebird day after a front pins pike into the thickest weeds, so you have to put the lure right on them."
        phase == PredatorPhase.SUMMER -> "Pike need cooler water than bass, so in the heat the big fish slide to deeper weed edges and cooler cover."
        else -> "Pike are sight-hunting ambush predators, so they hold in cover and crush anything that flashes past within range."
    }

    val (whereBrief, whereMore) = when (phase) {
        PredatorPhase.SPRING -> Pair(
            "Fish the shallow, weedy bays. After spawning, pike hunt the first warm water of the year.",
            "Look in the backs of bays, over flooded and emerging weeds, and along the first weedlines. The warmest, weediest shallows hold the most active fish this time of year.",
        )
        PredatorPhase.SUMMER -> Pair(
            "Fish the weedlines and points, shallow and deep. Small pike stay in the shallow weeds; big ones pull to cooler, deeper cover.",
            "Work the outside weed edges, points, and drop-offs. Through the heat of summer the bigger pike want cooler water, so the deeper weed edges and rock next to deep water hold the better fish, while numbers stay in the shallow salad.",
        )
        PredatorPhase.FALL -> Pair(
            "Fish the weed edges and drops near deep water, where pike ambush baitfish as they feed up.",
            "Fall is the trophy window. Pike stack on the sharpest weed edges, points, and drops next to deep water and eat big to prepare for winter. Bigger baitfish are on the menu, so bigger lures earn bigger fish.",
        )
        PredatorPhase.COLD -> Pair(
            "Fish deep weed edges and timber near the flats. In the cold, pike slow down but keep hunting.",
            "They hold on deep weed edges in eight to fifteen feet, along creek channels, and near submerged timber next to shallow flats, waiting for an easy meal to pass. Slow everything down.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        predatorWhen(windows, mood), PREDATOR_WHEN_MORE,
        howMore = "Pike want to see it move: something with flash and vibration, worked steady past the cover they hide in. Use a wire or heavy leader, because their teeth cut light line. Bigger baits sort out bigger pike, especially in fall. When it is bright after a front, slow down and put the lure right in the thickest weeds.",
        whyMore = "Northern pike are cool-water ambush predators built around weeds and cover. They wait in the salad and crush prey that passes, so cover and edges are everything. A falling barometer switches on a hard feed, and fall is a season-long binge as they fatten for winter, which is when the biggest fish of the year are caught. Summer heat is the one thing that scatters them: the big pike need cooler water, so they slide to deeper edges while the small ones stay shallow.",
    )
}

internal fun pickerelPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = predatorPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        PredatorPhase.COLD -> "Pickerel are still biting in the cold, holding on deep weed edges and timber next to the flats. They are one of the best cold-water and ice targets."
        PredatorPhase.SPRING -> "Pickerel are aggressive in the shallow weeds after their early spawn, slashing at anything that moves through the vegetation."
        PredatorPhase.SUMMER -> "Pickerel are locked in the shallow weeds, lily pads, and pad edges, ambushing everything that swims by."
        PredatorPhase.FALL -> "Pickerel are feeding up in the cooling weeds, aggressive and easy to find along the vegetation."
    }
    val headline = core + predatorMoodClause(w, mood)

    val tacticLine = when {
        w.heavyRain || mood == Mood.TOUGH ->
            "Work tight to the weeds: a weedless spoon or a soft jerkbait crawled along the pad edges and holes in the cover."
        phase == PredatorPhase.SUMMER ->
            "Buzz a weedless spoon, a spinnerbait, or a topwater frog across the pads and weed tops, and slow-roll a soft jerkbait along the edges."
        else ->
            "Fan-cast the weeds with an inline spinner, a small spoon, or a spinnerbait, reeled steady so it flashes through the vegetation."
    }
    val whyBrief = when {
        mood == Mood.FEEDING -> "Pickerel feed hard as pressure falls, striking from the weed cover they live in."
        phase == PredatorPhase.COLD -> "Pickerel tolerate cold better than most, so they keep hunting the weed edges when other fish quit, which makes them a great cold-water target."
        else -> "Pickerel are aggressive weed ambushers, so they slash at anything with flash that passes their cover."
    }

    val (whereBrief, whereMore) = when (phase) {
        PredatorPhase.SUMMER, PredatorPhase.SPRING -> Pair(
            "Fish the shallow weeds, pads, and grass. Pickerel live in the vegetation and ambush from it.",
            "Look wherever there is thick shallow cover: lily pads, milfoil, grass flats, and pad edges. Pickerel hold in and against it and dart out to strike, so put the lure right along the cover.",
        )
        PredatorPhase.FALL, PredatorPhase.COLD -> Pair(
            "Fish the deeper weed edges and timber near the flats. Pickerel keep hunting the vegetation as it cools.",
            "As the shallow weeds die back, pickerel drop to the deeper weed edges, creek channels, and timber next to the flats. They stay catchable through cold water and ice, so work those edges slowly.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        predatorWhen(windows, mood), PREDATOR_WHEN_MORE,
        howMore = "Pickerel want flash and movement through the weeds, and they are not shy: an inline spinner, a small spoon, or a weedless spoon over the pads is classic. Use a short leader, because they have teeth. They stay aggressive in cold water, so do not write them off when everything else shuts down.",
        whyMore = "Chain pickerel are the shallow-weed cousin of the pike: aggressive ambush predators that live in the vegetation and slash at prey that passes. They are more tolerant of both heat and cold than pike, so they stay in the shallow weeds most of the year and keep biting into cold water and through the ice. A falling barometer adds a feed, but really they are willing customers any time you put flash through their cover.",
    )
}
