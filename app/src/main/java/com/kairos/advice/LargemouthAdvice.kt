package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Largemouth bass — the deep content. Bass are the clearest case of "the season
 * runs the fish and the weather runs the day," so the plan is built from the water
 * temperature and calendar (which phase they're in) and then modulated by today's
 * pressure, sky, and wind.
 *
 * The knowledge here is the consensus you'd hear from any serious bass angler or
 * read across In-Fisherman / Bassmaster / the better YouTube teachers (BassFishingHQ,
 * TylersReelFishing): where fish sit by season, how a cold front pins them, why wind
 * and clouds turn on moving baits, and how light dictates color. It's guidance, not
 * a guarantee — but it's the real reasoning, not weather trivia.
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

/**
 * Phase from water temp, disambiguated by month so a rising 55° in April (pre-spawn)
 * isn't confused with a falling 55° in October (fall feed-up). Warming vs cooling is
 * the whole story in the shoulder seasons.
 */
private fun bassPhase(waterF: Double, month: Int): BassPhase {
    val cooling = month >= 9 // Sep+ the lake is dropping
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

/** Color guidance from the light — the one rule every teacher agrees on. */
private fun colorNote(w: WeatherRead): String = when {
    w.clear && !w.overcast ->
        "Bright, clear water: go natural and translucent — green-pumpkin, watermelon, and shad/bluegill patterns that imitate, not announce."
    w.overcast ->
        "Low, gray light: bass hunt by silhouette, so bolder pays — white or chartreuse moving baits, and black or black-blue for anything slow."
    else ->
        "Mixed light: shad and green-pumpkin cover most of it; brighten a shade if the water's stained."
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

    // WHERE — season sets the neighborhood; wind and sun move them within it.
    val where = when (phase) {
        BassPhase.COLD ->
            "They've pulled out to the deepest wintering spots — main-lake points, channel edges, and steep breaks near deep water. " +
                "Think vertical: the school stacks tight, so once you find one you've usually found a pile."
        BassPhase.PRESPAWN ->
            "Big females are staging on the migration routes into the spawning bays — secondary points, the first drop off flat pockets, and any hard bottom near the shallows. " +
                "The warmest water wins: dark bottoms and wind-protected north banks warm first and pull fish up."
        BassPhase.SPAWN ->
            "They're shallow and locked to the bank — hard-bottom pockets, the backs of coves, and flats protected from the wind, one to four feet down. " +
                "Look for beds and cruisers; the biggest females sit near cover just off the beds."
        BassPhase.POSTSPAWN ->
            "Fish are recovering and strung out on the first structure between the spawning flats and their summer haunts — points, docks, and shallow cover near the bluegill beds they'll raid."
        BassPhase.SUMMER ->
            "Two crowds: shallow fish living in the shade — docks, laydowns, matted grass — and deeper fish on offshore structure like weed edges, ledges, and humps. " +
                "Midday belongs to the deep and shady fish; the shallow bite is a low-light game."
        BassPhase.FALL_FEED ->
            "Follow the bait. As the water cools they push into the backs of creeks and coves chasing shad and baitfish, and they'll be shallow and roaming. " +
                "The wind is your friend now — it stacks bait on windblown banks and points, and the bass are right under it."
        BassPhase.LATE_FALL ->
            "The crowd has thinned but the ones left are big. They sit on the sharpest structure near deep water — bluff ends, main-lake points, rock — waiting to ambush the last of the bait."
    }

    // WHEN — light windows, shifted by the weather.
    val whenBody = buildString {
        append("Best windows today: $windows. ")
        when {
            w.frontIncoming ->
                append("A front is moving in — get out ahead of it. The falling-pressure hours before the change are the year's best feeding window; fish hard right up until the sky turns.")
            w.falling ->
                append("Pressure is dropping, which flips the feed switch on. Don't wait for evening — the next several hours are live.")
            w.bluebird ->
                append("This is a bluebird post-front day — the toughest hand in bass fishing. The bite compresses into the very first and last light, so be there for it and grind the middle.")
            phase == BassPhase.SUMMER ->
                append("In summer heat the shallow fish only really commit at dawn and dusk; midday, go deep or go to shade.")
            else ->
                append("With no strong weather trigger, lean on the light — first and last light do the heavy lifting.")
        }
    }

    // HOW — presentation family + speed + color, by phase and weather.
    val how = buildString {
        when (phase) {
            BassPhase.COLD ->
                append("Slow way down. A jig or a soft-plastic dragged on bottom, a blade bait, or a suspending jerkbait worked with long pauses — cold fish won't chase, so make it easy and keep it in the strike zone. ")
            BassPhase.PRESPAWN ->
                append("This is a power window. A lipless crank or a squarebill ripped through the staging areas, a slow-rolled spinnerbait, or a jig on the drops — big pre-spawn females want a meal, not a snack. ")
            BassPhase.SPAWN ->
                append("Slow and deliberate to fish you can see: a wacky-rigged stick worm, a creature bait, or a craw pitched to the bed and left to sit. Sight-fish where you can, and handle spawners with care. ")
            BassPhase.POSTSPAWN ->
                append("They're finicky — a weightless stick worm, a shaky head, or a topwater walking bait over the flats early. Bluegill and shad-spawn imitators shine now. ")
            BassPhase.SUMMER ->
                append("Two speeds: a topwater, buzzbait, or frog over grass in the low-light hours, then slow down midday with a Texas-rigged worm, a deep crank, or a jig on the offshore structure. ")
            BassPhase.FALL_FEED ->
                append("Cover water and match the bait. Power-fish moving baits — a squarebill, spinnerbait, lipless, or chatterbait — plus topwater for the schoolers busting shad. When you find them they're often grouped, so slow down and pick the spot apart. ")
            BassPhase.LATE_FALL ->
                append("Fewer, bigger bites. A suspending jerkbait with long pauses, a slow-rolled spinnerbait, or a football jig on the rock — quality over quantity. ")
        }
        when {
            w.bluebird ->
                append("Because it's a high, sunny post-front day, downsize and slow down: pitch a finesse jig, dropshot, or shaky head into the thickest shade and cover — the sun drives them tight and they won't move far for it. ")
            w.windy || w.breezy ->
                append("Use the wind — fish the blown-in banks and points, and let the chop hide your approach; it's prime time for a moving bait. ")
            w.calm && w.clear ->
                append("Slick and clear, so finesse it: lighter line, natural colors, longer casts, and stay off them. ")
        }
        append(colorNote(w))
    }

    // WHY — tie it back to the mechanism, honestly.
    val why = when {
        w.frontIncoming || w.falling ->
            "Falling pressure and an approaching front trigger heavy feeding — bass sense the change and load up before the weather shuts them down for a day or two after."
        w.bluebird ->
            "After a front, the high, bright, stable sky pins bass tight to cover and kills their willingness to chase — the classic 'they were biting yesterday' day. Slower and smaller is the answer, not faster."
        w.overcast || w.windy ->
            "Clouds and wind cut the light and break up the surface, so bass roam and hunt instead of hiding — that's why moving baits shine and you can cover water."
        else ->
            "With stable weather, the daily rhythm rules: low light lets them push shallow and feed, bright midday sun sends them to shade and depth."
    }

    val headline = when (phase) {
        BassPhase.FALL_FEED ->
            "Fall feed-up is on — cooling water has bass chasing bait shallow to fatten for winter. This is a run-and-gun day."
        BassPhase.SPAWN ->
            "They're on or near the beds — shallow, protective, and catchable if you slow down and look."
        BassPhase.SUMMER ->
            "Summer split-shift: a shade-and-depth game by day, a shallow low-light bite at the edges."
        BassPhase.PRESPAWN ->
            "Pre-spawn — the biggest females of the year are feeding up shallow. The heavyweight window."
        BassPhase.COLD, BassPhase.LATE_FALL ->
            "Cold water — fewer bites, but the ones you get run big. Patience and a slow bait."
        BassPhase.POSTSPAWN ->
            "Post-spawn recovery — fish are scattered and moody; finesse and low light win."
    }

    return GamePlan(
        headline = headline,
        phaseLabel = phase.label,
        sections = listOf(
            PlanSection(PlanKind.WHERE, "Where they are", where),
            PlanSection(PlanKind.WHEN, "When to go", whenBody),
            PlanSection(PlanKind.HOW, "How to work it", how),
            PlanSection(PlanKind.WHY, "Why", why),
        ),
    )
}
