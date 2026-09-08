package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.HuntMethod
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Whitetail deer, the deep content. The deer year runs on day length, so the calendar
 * sets the behavior (the rut lands the same weeks every year) and the weather sets
 * whether they move in daylight. Deer read the weather differently than fish. The cold,
 * bright day after a front that shuts fish down is a *great* deer day, so this has its
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
    method: HuntMethod? = null,
): GamePlan {
    val phase = deerPhase(date)
    val windows = windowsText(timing, Side.HUNT)
    val warm = warmForSeason(c.airF, date.monthValue)
    val rut = phase == DeerPhase.RUT

    val core = when (phase) {
        DeerPhase.EARLY -> "Deer are on a simple pattern: bedded all day, then out to food in the evening. Hunt the hot food source and the trails into it."
        DeerPhase.PRE_RUT -> "Bucks are on their feet more and starting to search for does. Hunt the travel routes and pinch points between bedding and food."
        DeerPhase.RUT -> "Bucks are cruising and chasing does and can show anywhere the does are. Hunt near doe bedding and the funnels they travel."
        DeerPhase.POST_RUT -> "The rut wore the bucks down and they're hungry again. Get back on the best food source, especially in the afternoon."
        DeerPhase.LATE -> "In the cold, deer bed close to food and move as little as they can. Set up tight to the best food for the last hours of light."
    }
    val weatherClause = when {
        w.frontIncoming -> " A cold front's moving in, the best deer-movement trigger there is. Be in the woods early and stay late."
        warm && !rut -> " But it's warm for the season, so they'll move mostly after dark, so hunt the very edges of light."
        warm && rut -> " It's warm, but the rut can override that, and a cruising buck will still move midday, so sit long."
        w.windy -> " It's windy, so they'll hold in sheltered cover, so hunt the calm, downwind side of ridges and thickets."
        else -> " Steady weather, so hunt the usual first- and last-light windows."
    }
    val headline = core + weatherClause

    val tacticBase = when (phase) {
        DeerPhase.EARLY, DeerPhase.LATE -> "Sit downwind of the food and stay dead-still, and slip in and out without being seen, heard, or smelled."
        DeerPhase.PRE_RUT -> "Set up on a travel funnel with the wind in your favor; light rattling and a grunt call can pull a curious buck in."
        DeerPhase.RUT -> "Hunt a funnel and sit all day; rattle and grunt to pull cruising bucks, and use a doe bleat to stop one in range."
        DeerPhase.POST_RUT -> "Sit on the best food and be patient; a soft grunt can still turn a buck hunting a late doe."
    }
    val tacticLine = if (w.windy) "$tacticBase Hunt the sheltered, downwind side where deer bed out of the wind."
    else "$tacticBase Play the wind so your scent blows away from where you expect them."

    val whyBrief = when {
        w.frontIncoming -> "The temperature drop before a front makes deer feed heavily and move in daylight."
        warm -> "Deer wear a heavy coat, so warm days overheat them and push their movement into the night."
        rut -> "Rut movement is driven by day length, not weather, so bucks search for does no matter the conditions."
        else -> "With calm weather, deer keep to their safe routine, feeding at first and last light."
    }

    // ---- Full-page sections ----
    val (whereBrief, whereMore) = when (phase) {
        DeerPhase.EARLY -> Pair(
            "Set up on the food. Early on, deer bed all day and walk to food in the evening.",
            "Find the food that's hot right now (acorns dropping, apples, or green fields) and sit just inside the woods on the trails to it. Evenings near the food are best; mornings, hunt closer to where they bed. As pressure builds, big bucks start moving mostly after dark.",
        )
        DeerPhase.PRE_RUT -> Pair(
            "Hunt the travel routes between bedding and food. Bucks are moving more in daylight and starting to search for does.",
            "Look for a spot where the terrain squeezes deer travel into a narrow path: a saddle in a ridge, a creek crossing, an inside corner of a field. Bucks cruise these. Fresh ground they've pawed bare and saplings with the bark rubbed off tell you one is working the area.",
        )
        DeerPhase.RUT -> Pair(
            "Hunt near the does. Bucks are chasing does to breed, so they'll turn up wherever the does are.",
            "Camp on funnels and pinch points a searching buck has to use. If it goes dead quiet mid-rut, a buck is likely holed up with a single doe, so sit tight near doe bedding and wait him out; he'll be back on his feet looking for the next one soon.",
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
            w.frontIncoming -> append("A cold front is dropping in, so get in the woods early and stay late; the first cold morning behind it is prime.")
            warm && !rut -> append("It's warm, so hunt the very first and last light hard and keep expectations honest midday.")
            rut -> append("It's the rut, so a buck can move any hour: mornings are best, but the all-day sit pays off.")
            phase == DeerPhase.LATE -> append("Cold, calm afternoons pull deer to food before dark, so the last two hours are your window.")
            else -> append("Steady weather means the usual windows carry the day: first and last light near food.")
        }
    }
    val whenMore = "A cold front, a sharp drop in temperature, is the best thing that can happen to a deer hunter: deer feed hard right before it and move in daylight in the cool air right after. Warm spells do the opposite, pushing movement into the night."

    val howBrief = tacticLine
    val howMore = buildString {
        append(
            if (w.windy) "In the wind, deer feel exposed and bed in sheltered spots, so hunt the calm, downwind side of ridges and thick cover, and let the wind cover your movement. "
            else "Above all, play the wind: set up so your scent blows away from the deer, not toward their bedding or the food. They live by their nose. ",
        )
        append(
            when {
                w.frontIncoming -> "With the front moving in, hunt between the bedding and the food, since they'll be up early to feed before the weather turns."
                warm -> "In the warmth, stay near cool, shaded bedding and catch them right at the edges of daylight."
                else -> "The biggest mistake is moving too much, too soon, so get set and out-wait them."
            },
        )
    }

    val whyMore = "Two things drive a deer's day: staying comfortable, and, in November, the urge to breed. Comfort explains the weather rules: they move when it's cool and hide when it's hot or windy. The breeding urge is set by the shortening days, which is why mid-November produces daylight movement no other time of year can, weather or not."

    // The weapon section — only when the hunter has picked a method on the plan page. It
    // makes the "how" concrete to the tool: archery is a close-range wind/scent game, a
    // rifle trades close for range, a muzzleloader is one careful late-season shot.
    val weaponSection = weaponSection(method, phase)

    val baseSections = listOf(
        PlanSection(PlanKind.WHERE, "Where", whereBrief, whereMore),
        PlanSection(PlanKind.WHEN, "When", whenBrief, whenMore),
        PlanSection(PlanKind.HOW, "How", howBrief, howMore),
        PlanSection(PlanKind.WHY, "Why", whyBrief, whyMore),
    )
    // Slot the weapon section right after How, so it reads as "how, specifically with this."
    val sections = if (weaponSection == null) baseSections else
        baseSections.subList(0, 3) + weaponSection + baseSections.subList(3, 4)

    return GamePlan(
        phaseLabel = phase.label,
        headline = headline,
        tacticLine = tacticLine,
        whyBrief = whyBrief,
        sections = sections,
    )
}

/**
 * Concrete, do-this guidance for the chosen weapon, in the father-showing-the-ropes
 * voice. Returns null for a general (method-agnostic) plan or a non-deer method.
 * Grounded in the standard consensus each method is hunted by — close-range scent/wind
 * discipline for the bow, reach and shooting lanes for the rifle, one dry late shot for
 * the smokepole.
 */
private fun weaponSection(method: HuntMethod?, phase: DeerPhase): PlanSection? = when (method) {
    HuntMethod.ARCHERY, HuntMethod.EXPANDED_ARCHERY -> {
        val expanded = method == HuntMethod.EXPANDED_ARCHERY
        PlanSection(
            PlanKind.HOW,
            if (expanded) "Expanded archery" else "Archery",
            "A close game — 20 to 30 yards. It's all wind, scent, and getting tight to where deer already want to be.",
            buildString {
                append("The bow only works up close, so everything is about the setup. Hang your stand or set your blind right on the bed-to-food trail or a funnel, close enough for a 20-to-30-yard shot, and trim two or three quiet shooting lanes before the hunt, not during it. ")
                append("Play the wind like your hunt depends on it, because it does: your scent has to blow away from the deer and their bedding. Get in clean and scent-free, and stay dead still — a deer at this range catches the smallest movement. ")
                append(
                    when (phase) {
                        DeerPhase.EARLY -> "Archery season is the early food pattern, so hunt the evening food source and the trails feeding it; that's your highest-odds sit. "
                        DeerPhase.PRE_RUT, DeerPhase.RUT -> "In the rut you can add a stand on a funnel and rattle or grunt to pull a buck into bow range. "
                        else -> "Late in the bow season, sit tight to food in the last light. "
                    },
                )
                append("Wait for a broadside or quartering-away deer, draw when its head is behind a tree, and aim a touch low on an alert one — a whitetail can drop at the sound of the string.")
                if (expanded) append(" Expanded-archery zones are the suburban areas closed to firearms; know the zone lines and the landowner-permission rules before you hunt.")
            },
        )
    }
    HuntMethod.FIREARMS -> PlanSection(
        PlanKind.HOW,
        "Firearms (rifle)",
        "With a rifle you can reach out — hunt where you can see, and let range do the work.",
        buildString {
            append("Firearms season is your chance to hunt open country: field edges, clearcuts, log landings, power-line cuts, and the funnels that connect them. Post where you can see and cover the openings a deer has to cross, and don't crowd the cover the way a bowhunter has to. ")
            append("Firearms season lands squarely in the rut in most of Maine, so an all-day sit on a pinch point near doe bedding is the play — a cruising buck can step out at any hour. ")
            append("Take a steady rest every time, know your rifle's zero and your holds out to the range you'll actually shoot, and pass the low-odds running shot for the standing, broadside one. On the cold, still mornings behind a front, a slow still-hunt through good cover can put you on a bedded buck.")
        },
    )
    HuntMethod.MUZZLELOADER -> PlanSection(
        PlanKind.HOW,
        "Muzzleloader",
        "One shot, mid-range, and it's late and cold — hunt tight to food in the last light.",
        buildString {
            append("Muzzleloader season falls in the late season, when the rut is over and deer bed close to high-calorie food and move as little as they can. Set up right on that food and the trails to it, and favor south-facing slopes that hold the afternoon warmth; the last two hours of light are the window. ")
            append("You get one shot, so make it count: keep your powder, primer, and muzzle bone-dry in damp or snowy weather, get comfortably inside 100 yards, take a solid rest, and be sure before you touch it off — a fast, clean second shot isn't coming. ")
            append("A cold, calm afternoon after a front is the best card you can be dealt this time of year.")
        },
    )
    else -> null
}
