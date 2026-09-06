package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Black bear — the food-driven feeder. A bear's fall is one long push to pack on fat before
 * denning (hyperphagia), so the plan is about the food: bait and berries early, hard mast
 * (acorns, beechnuts) later. Bears are crepuscular and shy of heat and human scent, so cool,
 * damp evenings are prime and the evening sit is the money hour. Cited in SOURCES.md.
 */

private enum class BearPhase(val label: String) {
    BAIT("Bait season"),
    MAST("Natural food / mast"),
    OFF("Off-season"),
}

private fun bearPhase(date: LocalDate): BearPhase = when (date.monthValue) {
    8, 9 -> BearPhase.BAIT
    10, 11 -> BearPhase.MAST
    else -> BearPhase.OFF
}

internal fun blackBearPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = bearPhase(date)
    val warm = c.airF > 65.0
    val windows = windowsText(timing, Side.HUNT)

    val core = when (phase) {
        BearPhase.BAIT -> "Bears are hitting bait and natural berries hard, feeding up for winter. Sit a fresh, active bait downwind for the evening."
        BearPhase.MAST -> "Bears are on the hard mast now, cleaning up acorns and beechnuts. Hunt the feeding ridges and any oaks and beech dropping nuts."
        BearPhase.OFF -> "Outside the season, bears are on a food-first routine, feeding in the cool low-light hours near the best food."
    }
    val weatherClause = when {
        w.frontIncoming || w.raining -> " Cool, damp weather has them moving in daylight, so this is a strong sit. Be settled in early."
        warm -> " But it is warm, so they will wait for the cool of last light. Save your effort for the final hour and keep dead-still."
        w.windy -> " It is windy, which makes bears nervous and swirls your scent. Pick a stand with a rock-steady wind and hunt the calmer pockets."
        else -> " Steady weather, so the last two hours of light are your window."
    }
    val headline = core + weatherClause

    val tacticLine = when (phase) {
        BearPhase.BAIT -> "Sit the bait from mid-afternoon into last light with the wind in your face, and stay silent and still. Bears often come in the final minutes, so do not move early."
        BearPhase.MAST, BearPhase.OFF -> "Find the food that is dropping now, acorns, beechnuts, or standing crops, and sit or slow still-hunt downwind of it in the evening."
    }
    val whyBrief = when {
        w.frontIncoming || w.raining -> "Cool, damp weather lets bears move in daylight without overheating, so they hit food earlier in the evening."
        warm -> "Bears carry heavy fur and overheat, so on warm days they hold in shade and come to food only in the cool of last light."
        else -> "Bears are shy, scent-driven, crepuscular feeders, so they come to food in the low-light hours and after dark, keyed to their nose."
    }

    val (whereBrief, whereMore) = when (phase) {
        BearPhase.BAIT -> Pair(
            "Hunt over an active bait. A bait that is being hit hard is the highest-odds bear setup there is.",
            "Keep the bait fresh and read the sign: a torn-up, freshly-hit bait means a bear on a schedule. Set the stand for the prevailing wind so your scent never crosses where the bear approaches, usually meaning it comes in downwind of the bait but upwind of you.",
        )
        BearPhase.MAST -> Pair(
            "Hunt the natural food. When the mast drops, bears leave bait for acorns, beechnuts, and standing crops.",
            "Find the oaks and beech that are actually dropping nuts, or standing corn and old orchards, and look for fresh scat and torn logs. Bears feed almost around the clock in the fall push to fatten up, so the best food is the best spot.",
        )
        BearPhase.OFF -> Pair(
            "Hunt the best food near cover. Bears organize their whole day around the richest food source.",
            "Wherever the calories are, from berry patches to crops to mast, bears will be nearby, feeding in the cool hours and bedding thick by day. Set up downwind of that food for the low-light window.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        buildString {
            append("Best window today is $windows, and the last two hours are the money. ")
            when {
                w.frontIncoming || w.raining -> append("Cool, damp weather pulls bears to food earlier, so a longer evening sit pays off.")
                warm -> append("It is warm, so expect the bear in the final minutes of light and do not give up early.")
                else -> append("Bears come late, so the closer to dark, the better your odds.")
            }
        },
        whenMore = "Bears are crepuscular and lean toward the evening, especially on bait, and the last minutes of legal light are often when a mature bear finally commits. Cool, damp, post-front weather moves them earlier; heat holds them off until dark. Through the fall they feed almost constantly to pack on fat, so the pressure to eat is always working in your favor.",
        howMore = "Wind is everything with bears: they live by their nose, so a stand is only as good as its wind. Get in early, sit still, and stay quiet, because bears circle downwind and bust anything that is off. On natural food, slow down and read fresh sign to find which trees or fields the bears are actually working right now.",
        whyMore = "A black bear's fall is one long feeding push to store fat before it dens, so food rules everything: bait and berries early, hard mast later. They are heat-shy and extremely scent-driven, so they feed in the cool low-light hours and after dark and give any human scent a wide, downwind berth. Play the wind, hunt the best food, and hunt the last hour of light, and the odds tilt your way.",
    )
}
