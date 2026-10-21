package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Snowshoe hare and upland birds (grouse and woodcock) — the thick-cover small game. Both
 * live in dense young growth and are worked on foot, usually with dogs. Hares are a winter
 * game of thick softwoods and fresh tracking snow; grouse and woodcock are an edge-cover
 * game where damp, cool, breezy weather makes scenting best. Cited in SOURCES.md.
 */

// ---- Snowshoe hare -------------------------------------------------------------------

internal fun snowshoeHarePlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val windows = windowsText(timing, Side.HUNT)
    val freshSnow = c.airF <= 34.0 && w.raining // near/below freezing precip = snow falling
    val cold = c.airF < 30.0

    val core = "Hares are holed up in the thickest young softwoods, cedar swamps, and dense cuts. Get into that cover, because they will not be anywhere else."
    val weatherClause = when {
        freshSnow -> " Fresh snow is falling, which is prime: it lays down clean tracks to follow and quiets your steps. Cut a fresh track and stay on it."
        w.windy -> " It is windy, so hares bury in the thickest, most sheltered cover. Slow down and pick apart the densest tangles."
        cold -> " In the hard cold, hares sit tight and hold. Work slowly and be ready for a close, fast flush."
        else -> " Steady weather, so work the cover methodically and let the dogs, or fresh tracks, do the finding."
    }
    val headline = core + weatherClause

    val tacticLine = if (freshSnow || c.airF <= 32.0) {
        "Run beagles if you have them and post up on the runs for the circle, or still-hunt fresh tracks in the snow, watching for a black eye or the shape hunched in the cover."
    } else {
        "Work the thickest cover slowly with a dog, or jump-shoot by pushing the dense tangles and pausing often to make hares nervous and break."
    }
    val whyBrief = "Hares live and die by concealment in thick cover, so they hold tight and let danger pass, then flush close and fast when pressed."

    val whereBrief = "Hunt the densest young softwoods, cedar swamps, and regenerating cuts. Hares need thick cover for food and hiding, so that is the only place to look."
    val whereMore = "Look for young, dense evergreen growth: cedar swamps, spruce and fir thickets, and clearcuts grown back to a tangle. Hares browse and hide in the thick stuff and rarely leave it. Their tracks, droppings, and packed runways in the snow tell you a patch is holding rabbits, so hunt the cover with the most sign."

    val whenBrief = buildString {
        append("Best window today is $windows, but hares hold all day in the cover, so time of day matters less than the cover you pick. ")
        when {
            freshSnow -> append("Fresh tracking snow is the real trigger: get out while the tracks are clean.")
            else -> append("A bright night has hares feeding after dark and sitting tighter by day, so dig into the cover to move them.")
        }
    }
    val whenMore = "Hares are less tied to dawn and dusk than most game because they hold in cover through the day, so the biggest factor is snow. Three to five inches of fresh tracking snow turns a hard hunt into a good one. A bright, moonlit night lets them feed in the dark and sit even tighter the next day, which is the one place the moon earns weight in this app."

    val howMore = "Two ways to hunt them: dogs or tracks. Beagles push hares in big circles, so once the pack strikes, get to an opening on the run and wait, because the hare usually loops back to where it jumped. Without dogs, cut a fresh track in the snow and follow it slowly, looking for the hare hunched and still, and be ready for a fast, close shot in the tangle."
    val whyMore = "Snowshoe hares are pure prey animals that survive by hiding, turning white in winter and freezing in the thickest cover while danger passes. That is why cover is everything and why they hold tight and flush close. A bright moon lets them feed safely at night and hole up tighter by day, so dark nights and fresh snow are the hunter's friends."

    return fourSectionPlan(
        "Winter cover", headline, tacticLine, whyBrief,
        whereBrief, whereMore, whenBrief, whenMore, howMore, whyMore,
    )
}

// ---- Upland birds (grouse & woodcock) ------------------------------------------------

private enum class UplandPhase(val label: String) {
    EARLY("Early season"),
    PEAK("Peak / leaf-down"),
    LATE("Late season"),
}

private fun uplandPhase(date: LocalDate): UplandPhase {
    val m = date.monthValue
    return when {
        m <= 9 -> UplandPhase.EARLY
        m == 10 -> UplandPhase.PEAK
        else -> UplandPhase.LATE
    }
}

internal fun uplandPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = uplandPhase(date)
    val windows = windowsText(timing, Side.HUNT)
    val damp = w.raining || w.overcast
    val dry = w.clear && !w.raining

    val core = when (phase) {
        UplandPhase.EARLY -> "Birds are in the thick green cover: alder runs, aspen edges, and the brushy edges of young growth. Work that cover and the edges hard."
        UplandPhase.PEAK -> "Leaves are down and this is prime time. Work the aspen and alder edges, old orchards, and logging-road edges, and expect woodcock flights on the heels of a cold front."
        UplandPhase.LATE -> "In the cold, birds tighten to food and conifer cover. Hunt the edges near food, the softwood thickets, and sun-warmed south slopes."
    }
    val weatherClause = when {
        damp -> " Damp, cool air is ideal: it holds scent for the dogs and settles the birds into the cover. Work it slowly and let the dog use its nose."
        w.windy -> " It is windy, which makes birds jumpy and wild and hard to hear when they flush. Hunt the sheltered, downwind sides of the cover and keep close."
        dry -> " It is dry and bright, so scenting is poor and birds hold loose. Slow down, work the dampest, thickest cover, and hit the edges early and late."
        else -> " Steady weather, so work the cover and edges through the day, best at first and last light near food."
    }
    val headline = core + weatherClause

    val tacticLine = "Work the edges and young cover slowly behind a dog, and pause often, because grouse flush when you stop. Hit alder bottoms and damp thickets for woodcock, and old logging roads and orchards at first and last light for feeding grouse."
    val whyBrief = when {
        damp -> "Damp, cool air holds scent, so dogs work birds far better and the birds sit tighter in the cover."
        w.windy -> "Wind makes birds nervous and flush wild, and it hides the sound of the flush, so tight, sheltered cover is the play."
        else -> "Grouse and woodcock live in thick young cover and edges, feeding along them in the low-light hours."
    }

    val whereBrief = "Hunt the edges and young cover: alder runs, aspen stands, old orchards, and the brushy line where thick young growth meets the woods."
    val whereMore = "Grouse and woodcock are edge birds. Look where cover types meet: alder bottoms against aspen, young cuts against mature woods, brushy swamp edges, and old field corners. Woodcock want soft, damp ground they can probe for worms, so the wet alder bottoms hold them, especially when migrant flights drop in after October cold fronts. Grouse work the same edges plus food like old orchards, clover on logging roads, and fall berries."

    val whenBrief = buildString {
        append("Best window today is $windows. ")
        when {
            damp -> append("Cool, damp days let you hunt right through the middle, since scenting stays good all day.")
            dry -> append("On a dry, bright day, hit the edges hard at first and last light and slow way down midday.")
            else -> append("Birds feed along the edges at first and last light, so favor those hours near food.")
        }
    }
    val whenMore = "Moisture is the key for upland birds: a cool, damp, lightly breezy day holds scent for the dogs and settles birds into predictable cover, while a hot, dry, windy day scatters scent and makes birds flush wild. In October, cold fronts also deliver fresh woodcock flights, so the day after a north wind can fill the alders with new birds."

    val howMore = "Slow down and work with a dog if you can: cover the edges thoroughly, and pause every few steps, because a walking grouse holds until you stop and then flushes. In wind, stay in the sheltered cover and keep the dog close. Woodcock sit tight and flush straight up, so be ready in the alders; grouse are the opposite, flushing wild and putting cover between you fast."
    val whyMore = "Grouse and woodcock are edge specialists of young, thick cover. Grouse feed along the edges on buds, berries, and clover and explode into flight to survive, while woodcock probe soft, damp ground for worms and ride cold fronts south in the fall. Weather works through scent and comfort: damp, cool, breezy days concentrate birds and let dogs find them, and dry, hot, or windy days do the opposite."

    return fourSectionPlan(
        phase.label, headline, tacticLine, whyBrief,
        whereBrief, whereMore, whenBrief, whenMore, howMore, whyMore,
    )
}
