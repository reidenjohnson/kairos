package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Moose and elk — the big rut-driven cervids. Like deer, their year runs on day length,
 * so the rut lands the same weeks every year and calling is the tool of the season. Unlike
 * deer, they are huge and overheat badly, so warm weather shuts them down hard and cool,
 * damp weather gets them on their feet. Grounded in the consensus cited in SOURCES.md.
 * (Elk are not a Maine game species; the plan is honest western-elk guidance for the score.)
 */

private fun bigGameWarm(airF: Double): Boolean = airF > 55.0

/** Cool/damp gets these heat-sensitive animals moving; warm pins them to shade and night. */
private fun bigGameWeatherClause(w: WeatherRead, warm: Boolean, rut: Boolean): String = when {
    w.frontIncoming || w.raining -> " Cool, damp weather has them up and moving, exactly what you want. Get out early, cover ground, and call."
    warm && rut -> " It is warm, and they overheat easily, so hunt the cool edges of the day near shade and water. The rut can still pull a bull in, so keep calling."
    warm -> " But it is warm, and these animals overheat easily, so they will bed in shade near water and move mostly after dark. Hunt first and last light near cool, wet ground."
    w.windy -> " It is windy, which makes them edgy and hard to hear. Still-hunt slowly and use the wind to slip in quiet."
    else -> " Steady weather, so hunt first and last light when they move to feed."
}

// ---- Moose ---------------------------------------------------------------------------

private enum class MoosePhase(val label: String) {
    PRE_RUT("Pre-rut"),
    RUT("The rut"),
    POST_RUT("Post-rut"),
    OFF("Off-season"),
}

private fun moosePhase(date: LocalDate): MoosePhase {
    val m = date.monthValue
    val d = date.dayOfMonth
    return when {
        m == 9 && d < 20 -> MoosePhase.PRE_RUT
        (m == 9 && d >= 20) || (m == 10 && d <= 15) -> MoosePhase.RUT
        m == 10 -> MoosePhase.POST_RUT
        m in 8..11 -> MoosePhase.PRE_RUT
        else -> MoosePhase.OFF
    }
}

internal fun moosePlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = moosePhase(date)
    val warm = bigGameWarm(c.airF)
    val rut = phase == MoosePhase.RUT
    val windows = windowsText(timing, Side.HUNT)

    val core = when (phase) {
        MoosePhase.PRE_RUT -> "Bulls are feeding hard and starting to work rubs and wallows near wetlands. Hunt the bogs, cuts, and pond edges where they feed."
        MoosePhase.RUT -> "The rut is on. Bulls are cruising for cows and will come to a call, so cow-call and rake brush near the wetlands and cuts they travel."
        MoosePhase.POST_RUT -> "The rut is winding down and bulls are feeding to recover. Get back on the best feed near cover: bogs, regenerating cuts, and pond edges."
        MoosePhase.OFF -> "Outside the season, moose are on a feed-and-rest routine near wetlands and young growth, moving in the cool low-light hours."
    }
    val headline = core + bigGameWeatherClause(w, warm, rut)

    val tacticLine = when (phase) {
        MoosePhase.RUT -> "Cow-call and imitate a bull raking brush near feeding wetlands, then be ready, because a bull can come in silent. Glass the cuts and bogs early and late between calling sets."
        MoosePhase.PRE_RUT, MoosePhase.POST_RUT, MoosePhase.OFF -> "Glass the bogs, cuts, and pond edges at first and last light, and still-hunt the wind through the feeding areas. A cow call can still pull a curious bull."
    }
    val whyBrief = when {
        w.frontIncoming || w.raining -> "Moose carry no way to shed heat well, so the cool, damp air after a front is when they move and feed in daylight."
        warm -> "Moose overheat easily, so warm days push them into shade and water and move their feeding into the night."
        rut -> "Rut movement is set by day length, so bulls cruise and answer calls in late September and early October no matter the weather."
        else -> "Moose are big and heat-limited, so they feed in the cool low-light hours and bed through the warm middle of the day."
    }

    val (whereBrief, whereMore) = when (phase) {
        MoosePhase.RUT -> Pair(
            "Hunt near the wetlands and cuts where cows feed. Rutting bulls travel to find them.",
            "Set up where bulls cruise between feeding areas: bog edges, stream bottoms, and the fringes of regenerating cuts. Fresh rubs, torn-up saplings, and the sour smell of a wallow tell you a bull is working the area. Call from these spots and be patient.",
        )
        else -> Pair(
            "Hunt the feed near cover: bogs, beaver flowages, pond edges, and young regenerating cuts.",
            "Moose eat aquatic plants and the fresh browse in cuts and burns, so the best spots pair that food with nearby cover to bed in out of the heat. Glass big openings at first and last light and slip along the wind through the thick stuff in between.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        buildString {
            append("Best window today is $windows. ")
            when {
                w.frontIncoming || w.raining -> append("Cool, damp air after a front is the best moose weather there is, so hunt it hard, early and late.")
                warm -> append("It is warm, so the very first and last light near shade and water are your only real windows.")
                rut -> append("It is the rut, so a bull can move any hour and a call can bring one in, but dawn and dusk are still best.")
                else -> append("Steady weather means the usual dawn and dusk feeding windows carry the day.")
            }
        },
        whenMore = "Temperature runs a moose's day more than anything: they are built for cold and overheat fast, so a cool, damp, post-front morning is prime and a warm spell pushes everything into the dark. The rut in late September and early October adds daylight movement and makes calling work, set by day length rather than the weather.",
        howMore = "Calling is the signature moose tactic in the rut: soft cow calls to pull a bull, plus raking a shoulder blade or paddle in the brush to sound like a rival. Between sets, glass the big openings and still-hunt into the wind. These are huge, sharp-nosed animals, so wind and patience matter more than covering miles.",
        whyMore = "A moose's life is ruled by two things: it cannot shed heat, and in early fall it wants to breed. Heat explains the weather rules, cool and damp move them, warm pins them to shade and water and the night. The breeding urge, set by shortening days, adds daylight cruising and makes cow calls and brush-raking work through late September and early October regardless of conditions.",
    )
}

// ---- Elk -----------------------------------------------------------------------------

private fun elkPhase(date: LocalDate): MoosePhase = when (date.monthValue) {
    9 -> MoosePhase.RUT
    in 10..10 -> MoosePhase.POST_RUT
    8, 11 -> MoosePhase.PRE_RUT
    else -> MoosePhase.OFF
}

internal fun elkPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = elkPhase(date)
    val warm = bigGameWarm(c.airF)
    val rut = phase == MoosePhase.RUT
    val windows = windowsText(timing, Side.HUNT)

    val core = when (phase) {
        MoosePhase.RUT -> "The September rut is on. Bulls are bugling and holding cows, so locate a screaming bull at first light and close the distance with calls."
        MoosePhase.PRE_RUT -> "Elk are on a feed-and-bed pattern, herded up in the high country. Hunt the transitions between night feed and day beds."
        MoosePhase.POST_RUT, MoosePhase.OFF -> "The rut is over and elk are feeding to recover, grouped up and moving to timber and cooler slopes as it warms."
    }
    val headline = core + bigGameWeatherClause(w, warm, rut)

    val tacticLine = when (phase) {
        MoosePhase.RUT -> "Locate a bugling bull at daylight, get in tight and downwind, then bugle or cow-call to bring him in. Set up close before you call so he does not hang up."
        else -> "Glass the feeding meadows and parks at first and last light, then still-hunt the timber edges into the wind toward the day beds."
    }
    val whyBrief = when {
        rut -> "The elk rut is set by day length, so bulls bugle and answer calls through September whatever the weather does."
        warm -> "Elk are heat-sensitive, so warm days push them into cool, shaded north-facing timber and move their feeding into the night."
        else -> "Elk feed in the open in the cool low-light hours and bed in cover through the day, so dawn and dusk are the windows."
    }

    val (whereBrief, whereMore) = when (phase) {
        MoosePhase.RUT -> Pair(
            "Hunt near the cows. Bugling bulls hold their harems in the timber and feed the edges.",
            "Find the elk and you find the bull: work the meadows and parks they feed at night and the timber they bed in by day, and listen for bugles at first and last light. Get in close and downwind before you call so the bull commits.",
        )
        else -> Pair(
            "Hunt the feed-to-bed transitions: meadows and parks at the edges of dark, north-facing timber by day.",
            "Elk feed in the open in the cool hours and retreat to shaded, north-facing timber and higher benches to bed. Catch them at the transition at first and last light, and hunt cooler, higher ground as the weather warms.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        buildString {
            append("Best window today is $windows. ")
            when {
                rut -> append("It is the rut, so first light with a bugling bull is the magic hour. Cool, damp weather keeps them going longer.")
                warm -> append("It is warm, so hunt the very first and last light and the cool, shaded timber.")
                else -> append("Steady weather means the usual dawn and dusk feeding windows carry the day.")
            }
        },
        whenMore = "The September rut is the whole show for elk: day length has bulls bugling and vulnerable to calls at first and last light. Temperature still matters, cool and damp keeps them moving and vocal, while a warm spell pushes them into shaded timber and the night.",
        howMore = "In the rut, calling is king: locate a bull with a bugle, close the gap, and pull him with a bugle or cow calls from tight and downwind. Out of the rut, it is a spot-and-stalk game, glassing feed at the edges of light and still-hunting the wind toward the beds. Elk have superb noses, so the wind decides everything.",
        whyMore = "Elk live by heat and the rut. They are heat-sensitive herd animals that feed the cool openings and bed the shaded timber, so cool, damp weather keeps them active and warm weather pushes them high and nocturnal. The September rut, driven by day length, is when bulls bugle and calling works, the one window that overrides a cautious routine.",
    )
}
