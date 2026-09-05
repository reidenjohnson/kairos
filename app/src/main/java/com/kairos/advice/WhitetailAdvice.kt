package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Whitetail deer — the deep content. The deer year is driven by day length, so the
 * calendar sets the behavior (this is why the rut lands the same weeks every year,
 * weather regardless), and the weather sets whether they move in daylight. The plan
 * reads the phase from the date and then leans on today's front, temperature, and
 * wind to tell you when and how to hunt it.
 *
 * Consensus deer-woods knowledge: photoperiod-timed rut phases (Maine peak breeding
 * mid-November), cold fronts as the great daylight-movement trigger, warm spells
 * pushing movement to the dark, and the wind as both a scent problem and a bedding
 * cue. Guidance, honestly framed — it points you at the right setup for the day.
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
        m == 12 -> DeerPhase.LATE
        else -> DeerPhase.LATE
    }
}

/** Warm-for-the-season sends movement nocturnal; the threshold slides with the calendar. */
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

    val where = when (phase) {
        DeerPhase.EARLY ->
            "Deer are on a tight bed-to-food pattern right now. Find the food that's hot — acorns and beechnuts dropping, apples, green fields, or standing ag — and set up on the trails and staging cover just inside the woods from it. " +
                "Evenings near the food, mornings back toward bedding. Bucks are still in bachelor groups and fairly predictable early, but they'll get nocturnal as the weeks pass and pressure builds."
        DeerPhase.PRE_RUT ->
            "Rubs and scrapes are showing up and bucks are on their feet more. Hunt the funnels, staging areas, and scrape lines between doe bedding and food — the pinch points a cruising buck uses to check things. " +
                "Set up where terrain squeezes their travel: saddles, benches, inside corners, and creek crossings."
        DeerPhase.RUT ->
            "Bucks are cruising and chasing does now, and they'll show up anywhere the does are. Hunt near doe bedding and the food that feeds it, and camp on funnels and pinch points where a buck has to pass looking for the next hot doe. " +
                "If it feels dead midday during peak breeding, that's lockdown — a buck is tucked with a doe. Hunt the fringes of doe bedding and wait him out; the next receptive doe puts him back on his feet."
        DeerPhase.POST_RUT ->
            "The rut's worn them down and they're hungry — it's back to food. Hunt the best remaining food source hard, especially on cold afternoons. " +
                "A minor second rut can flare in early December as unbred does cycle, so a buck still checking doe groups is worth the sit."
        DeerPhase.LATE ->
            "Survival mode: deer bed close to the best food and burn as little energy as possible. Hunt the food-to-bed connection, south-facing thermal cover on cold days, and set up tight to a high-calorie food source. " +
                "Afternoons over food are the highest-odds sit of the day."
    }

    val whenBody = buildString {
        append("Best windows today: $windows. ")
        when {
            w.frontIncoming ->
                append("A cold front is dropping in, and that's the single best daylight-movement trigger there is. Deer feed hard ahead of it and again right after — the first cold, clear morning behind a front is prime; be in the tree early and stay late.")
            warm && phase != DeerPhase.RUT ->
                append("It's warm for the season, so expect most movement to happen after dark — hunt the very first and last light hard and keep expectations honest through the middle.")
            warm && phase == DeerPhase.RUT ->
                append("It's warm, which normally kills movement, but the rut can override the thermometer — a cruising buck will still travel midday. Sit longer than feels reasonable.")
            phase == DeerPhase.RUT ->
                append("It's the rut, so movement can happen any hour — mornings are best, but the all-day sit is worth it. Bucks cover ground looking for does when the woods seem empty.")
            phase == DeerPhase.LATE ->
                append("Cold, calm afternoons pull deer to food before dark — the last two hours of light are your window.")
            else ->
                append("Stable weather means the classic light windows carry the day — first and last light near food and cover.")
        }
    }

    val how = buildString {
        // Movement style + setup.
        when (phase) {
            DeerPhase.EARLY, DeerPhase.LATE ->
                append("This is a stand-hunting game: get in tight to food and travel without being seen, winded, or heard, and let them come. Access is everything — slip in and out without blowing out the bedding. ")
            DeerPhase.PRE_RUT ->
                append("Hunt aggressive but smart — a rub line or scrape funnel with the wind in your favor. Light rattling and a grunt can pull a curious buck this time of year; keep it subtle. ")
            DeerPhase.RUT ->
                append("Be aggressive: sit longer, hunt funnels all day, and use the calls — rattling and grunting draw cruising bucks, and a doe bleat can turn one. This is the two weeks to burn vacation days. ")
            DeerPhase.POST_RUT ->
                append("Back to a patient food-source sit; a light grunt or bleat can still work on a buck hunting a late doe, but don't overdo it. ")
        }
        // Wind — scent and bedding both.
        when {
            w.windy ->
                append("It's windy, so deer will be edgy and bedded in the lee — hunt the sheltered side of ridges, thick leeward cover, and protected hollows where they feel safe, and use the wind and its noise to cover your movement. ")
            else ->
                append("Play the wind above all: set up so your scent blows away from where you expect deer, not toward the bedding or the food. ")
        }
        // Weather-driven urgency.
        if (w.frontIncoming) {
            append("With the front coming, hunt the transition between bedding and food — they'll be up and moving early to beat the weather.")
        } else if (warm) {
            append("In the warmth, stay downwind of shaded, cool bedding and catch them at the edges of daylight.")
        } else {
            append("Slow down, stay put, and out-sit them — the biggest mistake is moving too much and too soon.")
        }
    }

    val why = when {
        w.frontIncoming ->
            "The pressure change and the temperature drop ahead of a front cue deer to feed heavily before it hits — and the cool, comfortable air behind it gets them up and moving in daylight. It's the closest thing to a sure bet in deer hunting."
        warm ->
            "Deer wear a winter coat by fall, so warm weather overheats them fast — they shift feeding to the cool of night and bed in shade through the day. Only the rut's breeding drive reliably overrides it."
        phase == DeerPhase.RUT ->
            "Rut movement is driven by day length, not weather — bucks are compelled to search for receptive does regardless of conditions, which is why midday sits and all-day funnels produce now when they'd be a waste any other time."
        else ->
            "With settled weather, deer fall back on their safe daily rhythm — feeding at the edges of light and bedding through the day — so hunting the food-to-bed connection at dawn and dusk is the percentage play."
    }

    val headline = when (phase) {
        DeerPhase.EARLY ->
            "Early season — deer are locked on food in a tight bed-to-feed pattern. Hunt the hot food source, evenings first."
        DeerPhase.PRE_RUT ->
            "Pre-rut — scrapes are opening and bucks are on their feet. Hunt the funnels between bedding and food."
        DeerPhase.RUT ->
            "The rut is on — bucks are cruising for does and can move any hour. Sit long, hunt funnels, use the calls."
        DeerPhase.POST_RUT ->
            "Post-rut — worn, hungry bucks are back on food. Hunt the best feed hard, especially cold afternoons."
        DeerPhase.LATE ->
            "Late season — it's all about food and warmth. Set up tight to a high-calorie food source for the afternoon."
    }

    return GamePlan(
        headline = headline,
        phaseLabel = phase.label,
        sections = listOf(
            PlanSection(PlanKind.WHERE, "Where to set up", where),
            PlanSection(PlanKind.WHEN, "When to hunt", whenBody),
            PlanSection(PlanKind.HOW, "How to hunt it", how),
            PlanSection(PlanKind.WHY, "Why", why),
        ),
    )
}
