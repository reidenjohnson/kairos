package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Wild turkey — the eyes-and-ears bird. Turkeys roost in trees, fly down at first light, and
 * live by sharp eyes and sharp hearing, which is why wind is their biggest enemy for a hunter:
 * it hides your calls, spooks nervous birds, and shuts gobblers down. Spring is a calling game
 * for gobbling toms; fall is about finding and scattering flocks. Cited in SOURCES.md.
 */

private enum class TurkeyPhase(val label: String) {
    SPRING("Spring gobbler"),
    FALL("Fall flock"),
    OFF("Off-season"),
}

private fun turkeyPhase(date: LocalDate): TurkeyPhase = when (date.monthValue) {
    4, 5, 6 -> TurkeyPhase.SPRING
    9, 10, 11 -> TurkeyPhase.FALL
    else -> TurkeyPhase.OFF
}

internal fun wildTurkeyPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = turkeyPhase(date)
    val windows = windowsText(timing, Side.HUNT)

    val core = when (phase) {
        TurkeyPhase.SPRING -> "Gobblers are fired up and looking for hens. Roost a bird the evening before, set up close at first light, and call to pull him in."
        TurkeyPhase.FALL -> "Turkeys are in flocks on the fall food. Find a flock, scatter it, then sit and call the birds back as they try to regroup."
        TurkeyPhase.OFF -> "Outside the season, turkeys are flocked up on the best food, roosting at night and feeding through the day."
    }
    val weatherClause = when {
        w.windy -> " But it is windy, the turkey hunter's worst enemy: birds cannot hear your calls, everything looks like danger, and toms often stop gobbling. Move in tight, use loud box or slate calls, and hunt sheltered hollows and field edges out of the wind."
        w.heavyRain -> " A hard rain has birds sitting tight or standing in the open fields where they can see. Glass the open ground and be patient."
        w.calm && !w.raining -> " It is calm, which is perfect: your calls carry, birds are relaxed, and a gobbler will hear you from a long way off."
        else -> " Fair weather, so work the roost and the field edges at first light and stay mobile."
    }
    val headline = core + weatherClause

    val tacticLine = when (phase) {
        TurkeyPhase.SPRING -> "Set up as close to the roosted gobbler as you dare before fly-down, with a hen decoy out front, and start soft with tree yelps, then match his energy. Locate silent birds with an owl or crow call to make one shock-gobble."
        TurkeyPhase.FALL, TurkeyPhase.OFF -> "Find a flock on the food, scatter it well in all directions, then sit right there, wait a few minutes, and call the birds back with kee-kees and lost yelps as they try to regroup."
    }
    val whyBrief = when {
        w.windy -> "Turkeys rely on sharp eyes and ears, so wind blinds and deafens them, makes them jumpy, and shuts down gobbling."
        w.calm -> "On a calm day your calls carry far and birds are relaxed, so a gobbler answers and commits from a distance."
        else -> "Turkeys roost at night and feed by day, flying down at first light, so the morning near the roost is the prime window."
    }

    val whereBrief = when (phase) {
        TurkeyPhase.SPRING -> "Hunt near the roost and the fields and openings where hens feed. Gobblers go where the hens are."
        else -> "Find the flock on the fall food: oak ridges, fields, and old orchards. Then break it up and call it back."
    }
    val whereMore = when (phase) {
        TurkeyPhase.SPRING ->
            "Put a bird to bed the evening before by listening for them fly up to roost, then slip in close before dawn. Gobblers follow hens, so set up between the roost and the fields, openings, and ridges where hens go to feed, and let the tom come looking. In wind, drop into sheltered hollows and the lee of ridges where birds gather and can hear you."
        else ->
            "Fall turkeys are all about food and flocks: find them on acorns and beechnuts, in fields, and around old orchards, and pattern where a family or gobbler flock feeds and roosts. Once you locate a flock, the scatter is the setup, so get close before you bust them."
    }

    val whenBrief = buildString {
        append("Best window today is $windows, and the first hour off the roost is prime. ")
        when {
            w.windy -> append("Wind cuts the morning short and quiets the gobbling, so hunt tight and be ready to move on birds you spot.")
            w.calm -> append("Calm air carries your calls, so a gobbler can answer from far off, all morning.")
            else -> append("Work the roost at fly-down, then the field edges as birds feed.")
        }
    }
    val whenMore = "Turkeys are day birds that roost at night, so the action starts at fly-down and the first hour is the best of the day, with a second push as they feed. Wind is the great spoiler: it hides your calls, unnerves the birds, and often stops gobbling, so calm mornings far outproduce windy ones. Steady rain pushes birds into open fields where they can see, so glass the openings when it pours."

    val howMore = "Match your calling to the day: on calm mornings a gobbler hears soft yelps from far off, so start quiet and read him; in wind, switch to loud box and slate calls that cut, and close the distance because he cannot hear you. In fall, the scatter is everything, run at the flock and break it up in every direction, then sit right at the break and call the pieces back. Woodsmanship beats calling, so put yourself where the birds already want to be."
    val whyMore = "A wild turkey's whole defense is its eyes and ears, and that one fact runs the hunt. Wind blinds and deafens them and stops gobblers from gobbling, so calm days are far better. In spring the breeding drive has toms answering hen calls, so setup and calling near the roost is the game. In fall it is about the flock and the food: find them, scatter them, and call them back to the break."

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief,
        whereBrief, whereMore, whenBrief, whenMore, howMore, whyMore,
    )
}
