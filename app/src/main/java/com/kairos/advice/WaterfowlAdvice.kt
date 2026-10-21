package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Waterfowl (ducks) — the weather birds. More than any game, ducks are ruled by fronts and
 * wind: the bulk of migration rides the north winds behind a cold front, wind keeps birds
 * moving and makes decoys look alive, and nasty weather packs birds into fewer sheltered
 * spots. A bright, calm, high-pressure day is the toughest. Cited in SOURCES.md.
 */

private enum class DuckPhase(val label: String) {
    EARLY("Early / local birds"),
    MIGRATION("Migration"),
    LATE("Late season"),
    OFF("Off-season"),
}

private fun duckPhase(date: LocalDate): DuckPhase = when (date.monthValue) {
    9, 10 -> DuckPhase.EARLY
    11 -> DuckPhase.MIGRATION
    12, 1 -> DuckPhase.LATE
    else -> DuckPhase.OFF
}

internal fun waterfowlPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = duckPhase(date)
    val windows = windowsText(timing, Side.HUNT)
    val calm = w.calm && w.clear && w.highPressure

    val core = when (phase) {
        DuckPhase.EARLY -> "Local ducks are patterned on their food and rest water. Scout where they want to be, then be there at first light before they move."
        DuckPhase.MIGRATION -> "New birds are riding the fronts south. Hunt fresh, unpressured water right after a cold front, when migrant flocks decoy with confidence."
        DuckPhase.LATE -> "As water freezes, ducks pile onto whatever open water is left. Find the open holes and the last food and you find the birds."
        DuckPhase.OFF -> "Outside the season, ducks are keyed to food and rest water, moving most at first and last light."
    }
    val weatherClause = when {
        w.frontIncoming -> " A front is moving in, the best duck weather there is. Birds feed hard ahead of it and new flocks pour in behind it, so hunt the feed early and stay put."
        w.windy -> " It is windy, which is a gift: birds stay on the move, decoys come alive, and rough water pushes ducks into the sheltered coves and lees. Hunt those calm pockets."
        calm -> " But it is bright, calm, and high-pressure, the toughest duck day: birds raft up and loaf. Hunt the very first light and consider moving to the birds."
        else -> " Steady weather, so hunt the first-light feed and the traffic between roost and food."
    }
    val headline = core + weatherClause

    val tacticLine = if (w.windy) {
        "Set up on the sheltered, downwind water where ducks get out of the chop, put decoys on the upwind edge with an open landing pocket downwind, and let the wind work your spread. Ducks land into the wind, so face your setup accordingly."
    } else {
        "Hunt where the birds already want to be, in a feeding or resting spot you scouted, with a spread on the upwind edge and a clear landing hole. Call sparingly on calm days and let the decoys do the work."
    }
    val whyBrief = when {
        w.frontIncoming -> "Most migration happens within a day or two of a cold front, so a front means birds feeding hard and fresh flocks arriving."
        w.windy -> "Wind keeps ducks flying and restless, makes decoys look alive, and forces birds off the rough main water into the sheltered spots you can hunt."
        calm -> "On a bright, calm, high day ducks raft up on big open water and loaf, so they fly little and decoy poorly."
        else -> "Ducks trade between roost and food at first and last light, so the traffic and the feed set the odds."
    }

    val (whereBrief, whereMore) = when (phase) {
        DuckPhase.MIGRATION, DuckPhase.LATE -> Pair(
            "Hunt fresh water after a front, and the last open water late. New birds want new, quiet spots.",
            "After a cold front, migrant flocks look for unpressured food and rest water, so a fresh spot decoys them best. As the season pushes into freeze-up, ducks concentrate on the last open water, spring holes, moving water, and warm outflows, so find open water near food and you find the birds.",
        )
        else -> Pair(
            "Hunt where the ducks already want to be. Scout the food and rest water, then set up there.",
            "Ducks pattern hard on a food source and a place to loaf. Watch where they trade at first and last light, then set up in that exact spot with the wind right. In wind, shift to the sheltered, downwind water where they escape the chop.",
        )
    }

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        buildString {
            append("Best window today is $windows, with first light the classic duck hour. ")
            when {
                w.frontIncoming -> append("A front is coming, so hunt the whole morning and the feed ahead of the weather.")
                w.windy -> append("Wind keeps birds moving all day, so you have action well past dawn.")
                calm -> append("It is bright and calm, so hunt the first minutes of light and expect it to die fast.")
                else -> append("Lean on first light and the roost-to-food trade.")
            }
        },
        whenMore = "Ducks are weather birds. The best days pair a cold front with wind: birds feed hard before it, migrate in on the north winds behind it, and stay on the move in the blow. First light is the classic window, but wind and weather can keep birds flying all day. A bright, calm, high-pressure bluebird day is the slowest, with birds rafted up and loafing.",
        howMore = "Read the wind: ducks land into it, so put your decoys on the upwind edge and leave an open landing pocket downwind of your blind, and hide where the wind will not swing birds behind you. On calm days call less and rely on a scouted spot; in wind, the noise and motion cover your calling and the birds commit harder. Above all, hunt where the birds already want to be rather than trying to pull them somewhere new.",
        whyMore = "Waterfowl live on the move, tied to food, rest water, and the weather that pushes them. Cold fronts drive the migration and touch off hard feeding, wind keeps birds flying and forces them into huntable sheltered water, and freeze-up funnels them onto the last open water. Calm, bright, high-pressure days let them raft up and do nothing, which is why the nastiest weather is the best hunting.",
    )
}
