package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Coyote — the caller's quarry. Coyotes are hunted by calling: a prey-in-distress or coyote
 * vocal pulls them in. They move most in cold weather, at first and last light and after dark,
 * and calling works best when a light wind carries the sound and your scent is controlled. A
 * bright, dead-calm high, and a stiff wind, both hurt. Cited in SOURCES.md.
 */

private enum class CoyotePhase(val label: String) {
    BREEDING("Breeding season"),
    PUPS("Denning / pups"),
    DISPERSAL("Fall dispersal"),
}

private fun coyotePhase(date: LocalDate): CoyotePhase = when (date.monthValue) {
    1, 2, 3 -> CoyotePhase.BREEDING
    4, 5, 6, 7, 8 -> CoyotePhase.PUPS
    else -> CoyotePhase.DISPERSAL
}

internal fun coyotePlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = coyotePhase(date)
    val windows = windowsText(timing, Side.HUNT)
    val cold = c.airF < 32.0
    val stiffWind = w.windy

    val core = when (phase) {
        CoyotePhase.BREEDING -> "It is the breeding season, the best time of year. Coyotes are on the move and fired up, so coyote howls and challenge calls pull responses, day or night."
        CoyotePhase.PUPS -> "Adults are tied to a den feeding pups, so they answer prey-in-distress calls hard, and later pup-distress calls, near cover and edges."
        CoyotePhase.DISPERSAL -> "Young coyotes are dispersing and naive, so distress calls work well. Winter is coming on and they are hunting hard as food gets short."
    }
    val weatherClause = when {
        w.frontIncoming || cold -> " Cold, post-front conditions are prime: coyotes move and hunt more in the cold, and calls carry far in the crisp air. Get out and run stands."
        stiffWind -> " But it is windy, which scatters your call and makes coyotes cautious. Set up with the wind steady in your face, hunt sheltered ground, and expect closer, more careful responses."
        w.calm -> " It is calm, so your calls carry a long way, though you have little wind to hide your scent. Watch the downwind side hard, because a coyote will try to circle it."
        else -> " Steady weather, so run stands at first and last light with the wind in your favor."
    }
    val headline = core + weatherClause

    val tacticLine = "Set up downwind of good cover with a clear view, especially of the downwind side, and call in sets of fifteen to twenty minutes: prey-in-distress to start, coyote vocals in the breeding season. Then move a half-mile and do it again."
    val whyBrief = when {
        w.frontIncoming || cold -> "Coyotes move and hunt more in the cold, and sound carries farther in crisp, dense air, so a cold snap after a front is prime calling."
        stiffWind -> "Strong wind scatters your calling and swirls scent, so coyotes hang up or circle wide, though a light breeze actually helps carry the call and control your scent."
        else -> "Coyotes hunt mostly at first and last light and after dark, and they come to a call that promises an easy meal or a rival to run off."
    }

    val whereBrief = "Set up downwind of cover with a good field of view: field edges, brushy draws, and the openings coyotes cross, with the wind in your face and the downwind side watched."
    val whereMore = "Coyotes travel and hunt the edges: field and woodlot edges, brushy draws, logging roads, and openings near cover. Pick a stand where you can see, especially downwind, because a called coyote almost always tries to circle to smell what it is hearing before it commits. Set up with the wind in your face and a bit of a backdrop to break your outline, and keep the sun at your back when you can."

    val whenBrief = buildString {
        append("Best window today is $windows, and first light, last light, and after dark are best. ")
        when {
            w.frontIncoming || cold -> append("A cold snap after a front has them moving and hunting, so run as many stands as you can.")
            stiffWind -> append("Wind shortens the effective range of your call, so move closer to cover between stands and keep sets tight.")
            else -> append("Run stands through the low-light hours and cover ground between them.")
        }
    }
    val whenMore = "Coyotes are most active and callable in the cold and the low light: dawn, dusk, and after dark, and all winter as food gets scarce and, in late winter, the breeding season fires them up. Cold, calm-to-light-wind, post-front conditions are ideal, with sound carrying far in the crisp air. A stiff wind or a warm spell both cut your odds, and night hunting (with the required permit) can be the most productive of all."

    val howMore = "Calling is a run-and-gun game: set up quietly downwind of cover, start with prey-in-distress sounds, and give each stand fifteen to twenty minutes before moving a half-mile to the next. In the breeding season, coyote howls and challenge calls draw territorial responses. The one rule that never changes is the wind, because a coyote lives by its nose and will circle to check your scent, so watch the downwind side and set up so it cannot get there without showing itself."
    val whyMore = "Coyotes are hunted by calling, and everything follows from that plus their nose. They come to a call that offers a meal or a rival, hardest in the cold when they move and hunt more and food is short, and hottest in the late-winter breeding season. Wind is the constant: a light breeze carries the call and controls your scent, but too much scatters both, and a called coyote will always try to swing downwind, so the setup lives and dies on the wind."

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief,
        whereBrief, whereMore, whenBrief, whenMore, howMore, whyMore,
    )
}
