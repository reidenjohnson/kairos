package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Whitetail deer — the deep content, written plainly. The deer year runs on day
 * length, so the calendar sets the behavior (the rut lands the same weeks every year),
 * and the weather sets whether they move in daylight. Each section leads with a short,
 * do-this sentence; the longer detail is there if you want the reasoning.
 *
 * Consensus deer-woods knowledge — guidance, not a guarantee.
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

    val (whereBrief, whereMore) = when (phase) {
        DeerPhase.EARLY -> Pair(
            "Set up on the food. Early in the season deer follow a simple pattern — bed all day, then walk to food in the evening.",
            "Find the food that's hot right now: acorns dropping, apples, or green fields. Sit just inside the woods on the trails leading to it. Evenings near the food are best; mornings, hunt closer to where they sleep (their bedding). As the weeks pass and they get pressured, big bucks start moving mostly at night.",
        )
        DeerPhase.PRE_RUT -> Pair(
            "Hunt the travel routes between bedding and food. Bucks are starting to move more in daylight and check for the first does.",
            "Look for a 'funnel' — a narrow strip of cover that squeezes deer travel, like a saddle in a ridge, a creek crossing, or a fence gap. Bucks cruise these looking for does. Fresh scrapes (bare dirt they paw up) and rubs (bark rubbed off saplings) tell you one is working the area.",
        )
        DeerPhase.RUT -> Pair(
            "Hunt near the does. Bucks are chasing does to breed now, so they'll show up wherever the does are — bedding areas and the food nearby.",
            "Camp on funnels and pinch points where a searching buck has to pass. If it goes quiet in the middle of the rut, a buck is probably tucked away with a doe ('lockdown') — sit tight near doe bedding and wait; he'll be back on his feet when she's done and he goes looking for the next one.",
        )
        DeerPhase.POST_RUT -> Pair(
            "Go back to the food. The rut wore the bucks down and now they're hungry and rebuilding.",
            "Hunt the best remaining food source hard, especially on cold afternoons when deer feed to stay warm. There's also a smaller 'second rut' in early December when leftover does come into heat, so a buck still checking doe groups is worth the wait.",
        )
        DeerPhase.LATE -> Pair(
            "Hunt tight to the best food, in the afternoon. In the cold, deer bed close to food and move as little as possible.",
            "Set up right on a high-calorie food source and the trails to it, and favor south-facing slopes that hold the day's warmth. The last two hours of daylight are the highest-odds sit of the day, as deer feed up before the cold night.",
        )
    }

    val whenBrief = buildString {
        append("Best window today is $windows. ")
        when {
            w.frontIncoming -> append("A cold front is coming in — that's the best deer-movement trigger there is. Get in the woods early and stay late; the first cold morning behind it is gold.")
            warm && phase != DeerPhase.RUT -> append("It's warm for the season, so deer will mostly move after dark — hunt the very first and last light and keep expectations honest midday.")
            phase == DeerPhase.RUT -> append("It's the rut, so a buck can move at any hour — mornings are best, but sitting all day is worth it.")
            phase == DeerPhase.LATE -> append("Cold, calm afternoons pull deer to food before dark — the last two hours of light are your window.")
            else -> append("Steady weather means the usual windows carry the day — first and last light near food.")
        }
    }
    val whenMore =
        "A cold front — a sharp drop in temperature — is the single best thing that can happen to a deer hunter. Deer feed heavily right before it arrives and get up and move in daylight in the cool air right after. Warm spells do the opposite: deer wear a heavy coat, so they overheat and shift their movement to the cool of night."

    val howBrief = when (phase) {
        DeerPhase.EARLY, DeerPhase.LATE ->
            "Sit still and stay hidden. Slip in and out without deer seeing, hearing, or smelling you, and let them come to the food. Don't over-hunt a spot."
        DeerPhase.PRE_RUT ->
            "Hunt a funnel with the wind in your favor. You can lightly rattle antlers together or blow a grunt call to pull a curious buck in."
        DeerPhase.RUT ->
            "Be aggressive — sit longer, hunt funnels all day, and use calls. Rattling and grunting pull in cruising bucks. This is the best two weeks of the year."
        DeerPhase.POST_RUT ->
            "Sit patiently on food. A soft grunt can still work on a buck hunting a late doe, but keep it low-key."
    }
    val howMore = buildString {
        append(
            if (w.windy) "It's windy, so deer feel exposed and will bed in sheltered spots out of the wind — hunt the calm, downwind side of ridges and thick cover. "
            else "Above all, play the wind: set up so your scent blows away from where you expect the deer, not toward their bedding or the food. Deer live by their nose. ",
        )
        append(
            when {
                w.frontIncoming -> "With the front moving in, hunt between the bedding and the food — deer will be up early to feed before the weather turns."
                warm -> "In the warmth, stay near cool, shaded bedding and catch them right at the edges of daylight."
                else -> "The most common mistake is moving too much and too soon — get set and out-wait them."
            },
        )
    }

    val whyBrief = when {
        w.frontIncoming -> "The temperature drop before a front makes deer feed heavily, and the cool air behind it gets them moving in daylight."
        warm -> "Deer wear a winter coat, so warm days overheat them — they feed at night and rest in the shade until dark."
        phase == DeerPhase.RUT -> "Rut movement is driven by day length, not weather — bucks are compelled to search for does no matter the conditions, so all-day sits pay off now."
        else -> "With calm weather, deer keep to their safe routine — feeding at first and last light and bedding through the day."
    }
    val whyMore =
        "Two things drive a deer's day: comfort and, in November, the urge to breed. Comfort explains the weather rules — they move when it's cool and hide when it's hot or windy. The breeding urge (the rut) is set by the shortening days, which is why mid-November produces daylight movement that no other time of year does, weather or not."

    val headline = when (phase) {
        DeerPhase.EARLY -> "Early season — deer are on a simple bed-to-food pattern. Hunt the best food source, evenings first."
        DeerPhase.PRE_RUT -> "Pre-rut — bucks are starting to move in daylight. Hunt the travel routes between bedding and food."
        DeerPhase.RUT -> "The rut is on — bucks are chasing does and can move any hour. Sit long, hunt funnels, use your calls."
        DeerPhase.POST_RUT -> "Post-rut — tired, hungry bucks are back on the food. Hunt the best feed, especially cold afternoons."
        DeerPhase.LATE -> "Late season — it's all about food and warmth. Set up tight to the best food for the afternoon sit."
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
