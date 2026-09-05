package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Whitetail deer — the deep content. The deer year runs on day length, so the calendar
 * sets the behavior (the rut lands the same weeks every year) and the weather sets
 * whether they move in daylight. Deer read the weather differently than fish — the cold,
 * bright day after a front that shuts fish down is a *great* deer day — so this has its
 * own posture logic rather than the fishing [Mood].
 */

private enum class DeerPhase(val label: String) {
    EARLY("Early season"),
    PRE_RUT("Pre-rut"),
    RUT("The rut"),
    POST_RUT("Post-rut"),
    LATE("Late season"),
}

private fun deerPhase(date: LocalDate): DeerPhase {
    val m = date.monthValue
    val d = date.dayOfMonth
    return when {
        m <= 9 -> DeerPhase.EARLY
        m == 10 && d < 20 -> DeerPhase.EARLY
        m == 10 -> DeerPhase.PRE_RUT
        m == 11 && d <= 20 -> DeerPhase.RUT
        m == 11 -> DeerPhase.POST_RUT
        else -> DeerPhase.LATE
    }
}

private fun warmForSeason(airF: Double, month: Int): Boolean = when (month) {
    9 -> airF > 68
    10 -> airF > 60
    11 -> airF > 50
    else -> airF > 42
}

internal fun whitetailPlan(
    sp: Species,
    c: Conditions,
    w: WeatherRead,
    date: LocalDate,
    timing: DayTiming?,
): GamePlan {
    val phase = deerPhase(date)
    val windows = windowsText(timing, Side.HUNT)
    val warm = warmForSeason(c.airF, date.monthValue)
    val rut = phase == DeerPhase.RUT

    val core = when (phase) {
        DeerPhase.EARLY -> "Deer are on a simple pattern — bedded all day, then out to food in the evening. Hunt the hot food source and the trails into it."
        DeerPhase.PRE_RUT -> "Bucks are on their feet more and starting to search for does. Hunt the travel routes and pinch points between bedding and food."
        DeerPhase.RUT -> "Bucks are cruising and chasing does and can show anywhere the does are. Hunt near doe bedding and the funnels they travel."
        DeerPhase.POST_RUT -> "The rut wore the bucks down and they're hungry again. Get back on the best food source, especially in the afternoon."
        DeerPhase.LATE -> "In the cold, deer bed close to food and move as little as they can. Set up tight to the best food for the last hours of light."
    }
    val weatherClause = when {
        w.frontIncoming -> " A cold front's moving in — the best deer-movement trigger there is. Be in the woods early and stay late."
        warm && !rut -> " But it's warm for the season, so they'll move mostly after dark — hunt the very edges of light."
        warm && rut -> " It's warm, but the rut can override that — a cruising buck will still move midday, so sit long."
        w.windy -> " It's windy, so they'll hold in sheltered cover — hunt the calm, downwind side of ridges and thickets."
        else -> " Steady weather, so hunt the usual first- and last-light windows."
    }
    val headline = core + weatherClause

    val tacticBase = when (phase) {
        DeerPhase.EARLY, DeerPhase.LATE -> "Sit downwind of the food and stay dead-still — slip in and out without being seen, heard, or smelled."
        DeerPhase.PRE_RUT -> "Set up on a travel funnel with the wind in your favor; light rattling and a grunt call can pull a curious buck in."
        DeerPhase.RUT -> "Hunt a funnel and sit all day; rattle and grunt to pull cruising bucks, and use a doe bleat to stop one in range."
        DeerPhase.POST_RUT -> "Sit on the best food and be patient; a soft grunt can still turn a buck hunting a late doe."
    }
    val tacticLine = if (w.windy) "$tacticBase Hunt the sheltered, downwind side where deer bed out of the wind."
    else "$tacticBase Play the wind so your scent blows away from where you expect them."

    val whyBrief = when {
        w.frontIncoming -> "The temperature drop before a front makes deer feed heavily and move in daylight."
        warm -> "Deer wear a heavy coat, so warm days overheat them and push their movement into the night."
        rut -> "Rut movement is driven by day length, not weather — bucks search for does no matter the conditions."
        else -> "With calm weather, deer keep to their safe routine — feeding at first and last light."
    }

    // ---- Full-page sections ----
    val (whereBrief, whereMore) = when (phase) {
        DeerPhase.EARLY -> Pair(
            "Set up on the food. Early on, deer bed all day and walk to food in the evening.",
            "Find the food that's hot right now — acorns dropping, apples, or green fields — and sit just inside the woods on the trails to it. Evenings near the food are best; mornings, hunt closer to where they bed. As pressure builds, big bucks start moving mostly after dark.",
        )
        DeerPhase.PRE_RUT -> Pair(
            "Hunt the travel routes between bedding and food. Bucks are moving more in daylight and starting to search for does.",
            "Look for a spot where the terrain squeezes deer travel into a narrow path — a saddle in a ridge, a creek crossing, an inside corner of a field. Bucks cruise these. Fresh ground they've pawed bare and saplings with the bark rubbed off tell you one is working the area.",
        )
        DeerPhase.RUT -> Pair(
            "Hunt near the does. Bucks are chasing does to breed, so they'll turn up wherever the does are.",
            "Camp on funnels and pinch points a searching buck has to use. If it goes dead quiet mid-rut, a buck is likely holed up with a single doe — sit tight near doe bedding and wait him out; he'll be back on his feet looking for the next one soon.",
        )
        DeerPhase.POST_RUT -> Pair(
            "Go back to the food. The rut left the bucks worn down and hungry.",
            "Hunt the best remaining food hard, especially on cold afternoons when deer feed to stay warm. A smaller second rut can flare in early December, so a buck still checking doe groups is worth the sit.",
        )
        DeerPhase.LATE -> Pair(
            "Hunt tight to the best food, in the afternoon. In the cold, deer bed close to food and barely move.",
            "Set up right on a high-calorie food source and the trails to it, and favor south-facing slopes that hold the day's warmth. The last two hours of light are the highest-odds sit as deer feed up before the cold night.",
        )
    }

    val whenBrief = buildString {
        append("Best window today is $windows. ")
        when {
            w.frontIncoming -> append("A cold front is dropping in — get in the woods early and stay late; the first cold morning behind it is prime.")
            warm && !rut -> append("It's warm, so hunt the very first and last light hard and keep expectations honest midday.")
            rut -> append("It's the rut, so a buck can move any hour — mornings are best, but the all-day sit pays off.")
            phase == DeerPhase.LATE -> append("Cold, calm afternoons pull deer to food before dark — the last two hours are your window.")
            else -> append("Steady weather means the usual windows carry the day — first and last light near food.")
        }
    }
    val whenMore = "A cold front — a sharp drop in temperature — is the best thing that can happen to a deer hunter: deer feed hard right before it and move in daylight in the cool air right after. Warm spells do the opposite, pushing movement into the night."

    val howBrief = tacticLine
    val howMore = buildString {
        append(
            if (w.windy) "In the wind, deer feel exposed and bed in sheltered spots — hunt the calm, downwind side of ridges and thick cover, and let the wind cover your movement. "
            else "Above all, play the wind: set up so your scent blows away from the deer, not toward their bedding or the food. They live by their nose. ",
        )
        append(
            when {
                w.frontIncoming -> "With the front moving in, hunt between the bedding and the food — they'll be up early to feed before the weather turns."
                warm -> "In the warmth, stay near cool, shaded bedding and catch them right at the edges of daylight."
                else -> "The biggest mistake is moving too much, too soon — get set and out-wait them."
            },
        )
    }

    val whyMore = "Two things drive a deer's day: staying comfortable, and — in November — the urge to breed. Comfort explains the weather rules: they move when it's cool and hide when it's hot or windy. The breeding urge is set by the shortening days, which is why mid-November produces daylight movement no other time of year can, weather or not."

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
