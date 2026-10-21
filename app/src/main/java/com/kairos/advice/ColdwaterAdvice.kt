package com.kairos.advice

import com.kairos.data.DayTiming
import com.kairos.engine.Conditions
import com.kairos.engine.Side
import com.kairos.engine.Species
import java.time.LocalDate

/**
 * Coldwater fish — brook trout, landlocked salmon, and lake trout (togue). These three
 * share one master driver: water temperature. They thrive in cold, oxygen-rich water and
 * shut down when the surface warms, so the season phase (from the water-temp proxy +
 * calendar) matters more than the day's weather. Spring (ice-out) and fall are the best
 * windows; summer sends them deep to find cold water. Weather [Mood] still tilts the day.
 * Grounded in the Maine coldwater consensus cited in SOURCES.md.
 */

private enum class ColdPhaseKind(val label: String) {
    COLD("Cold water"),
    SPRING("Spring / ice-out"),
    SUMMER("Summer"),
    FALL("Fall"),
}

private fun coldPhase(waterF: Double, month: Int): ColdPhaseKind {
    val cooling = month >= 9
    return when {
        waterF < 40 -> ColdPhaseKind.COLD
        cooling && waterF < 62 -> ColdPhaseKind.FALL
        waterF >= 62 -> ColdPhaseKind.SUMMER
        else -> ColdPhaseKind.SPRING
    }
}

/** Weather tilt for coldwater fish. They are less weather-driven than bass, but a front
 * still adds a feed and a bright bluebird day still slows things. */
private fun coldMoodClause(w: WeatherRead, mood: Mood): String = when {
    w.raining -> " Rain is dimming the light, which nudges them to feed. Get on it."
    mood == Mood.FEEDING -> " A front is moving in and the pressure is dropping, so add a short, hard feed on top. Get out ahead of it."
    mood == Mood.TOUGH -> " But it is a bright, high-pressure bluebird day, so expect them a touch deeper and slower."
    mood == Mood.ROAMING -> " Wind and cloud dim the light and ripple the surface, so they will roam and feed a little bolder."
    else -> " No big weather push, so lean hard on first and last light."
}

private fun coldWhenBrief(windows: String, mood: Mood, low: Boolean): String = buildString {
    append("Best window today is $windows. ")
    when {
        mood == Mood.FEEDING -> append("A front is coming, so get out ahead of it for the best feed of the stretch.")
        mood == Mood.TOUGH -> append("Bright day after a front, so fish the low-light edges and expect them deeper.")
        low -> append("Dawn, dusk, cloud, and a ripple on the water are your best odds with this one.")
        else -> append("Lean on first and last light.")
    }
}

private const val COLD_WHEN_MORE =
    "For coldwater fish the calendar and the thermometer matter more than the barometer. Spring and fall, when the whole water column is cold, are the best fishing of the year. In summer they are pinned to the cold, deep water and only the low-light hours produce up top. A front still adds a feed and a bright bluebird day still slows the bite, but temperature sets the table."

// ---- Brook trout ---------------------------------------------------------------------

internal fun brookTroutPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = coldPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        ColdPhaseKind.COLD -> "Brook trout are sluggish under the cold, holding near the bottom of the deeper parts of the pond."
        ColdPhaseKind.SPRING -> "This is prime time. Brook trout are up near the surface and close to shore in the cold, oxygen-rich water, feeding aggressively."
        ColdPhaseKind.SUMMER -> "The surface is too warm now, so brook trout have pulled down to the cold water. Spring holes, deep basins, and cold inlets are the only places they will be."
        ColdPhaseKind.FALL -> "The pond is cooling and brook trout are back up shallow and near the inlets, feeding hard and staging to spawn over gravel and spring seeps."
    }
    val headline = core + coldMoodClause(w, mood)

    val tacticLine = when (phase) {
        ColdPhaseKind.SPRING, ColdPhaseKind.FALL ->
            "Fish near shore and the surface: a small spinner, a copper spoon, a smelt or minnow streamer, or a worm under a float. Trolling a spoon or streamer just under the surface covers water fast."
        ColdPhaseKind.SUMMER ->
            "Find the cold water and get down to it. Fish deep near spring holes and cold inlets, and work first and last light when a few slide up."
        ColdPhaseKind.COLD ->
            "Fish slow and deep near the bottom in the deepest water, a small jig or bait barely moving."
    }
    val whyBrief = when {
        mood == Mood.FEEDING -> "A dropping barometer adds a short feed, but for brook trout cold, oxygen-rich water is what really turns them on."
        mood == Mood.TOUGH -> "Brook trout are light-sensitive and heat-sensitive, so a bright bluebird day pushes them deeper and slower."
        phase == ColdPhaseKind.SUMMER -> "Brook trout are the most heat-sensitive of the trout: water past the mid-60s stresses them, so they abandon the warm surface for cold springs and depths."
        else -> "Brook trout feed best in cold water and low light, which is why spring, fall, dawn, and dusk are their windows."
    }

    val (whereBrief, whereMore) = when (phase) {
        ColdPhaseKind.SPRING, ColdPhaseKind.FALL -> Pair(
            "Fish near shore and the surface. In the cold water brook trout are shallow and hungry, close to inlets and rocky shorelines.",
            "Cold, oxygen-rich water lets them use the whole pond, so they hunt the shallows and the top few feet. Favor inlets, spring seeps, rocky points, and any moving water, especially in fall when they gather there to spawn.",
        )
        ColdPhaseKind.SUMMER -> Pair(
            "Find the cold water. In summer brook trout crowd into spring holes, deep basins, and cold inlets and leave the warm surface.",
            "The rest of the pond is too warm and low on oxygen, so they pack into the few cold spots: underwater springs, the deepest cool water, and the mouths of cold streams. Find the cold and you find the fish.",
        )
        ColdPhaseKind.COLD -> Pair(
            "Fish deep and slow. Under the cold, brook trout hold near the bottom of the deeper water and barely move.",
            "They sit near the bottom of the deeper basins and will not chase far, so keep the presentation slow and right in front of them.",
        )
    }

    return coldwaterGamePlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        coldWhenBrief(windows, mood, low = true),
        howMore = "Brook trout are not fussy when the water is cold: small, natural, and steady beats big and fast. Match a smelt or minnow with a streamer or spoon, or fall back to a worm. In summer, water temperature is everything, so put your time in on the cold spots and skip the warm shallows entirely.",
        whyMore = "Brook trout are the coldwater canary: they want water in the 50s and start to suffer past the mid-60s. That single fact runs their year. They spread out and feed shallow whenever the whole water column is cold (spring and fall), then retreat to springs and depths through summer, and they spawn over gravel and seeps in the fall. Weather tilts a given day, but cold water and low light are what put them on the feed.",
    )
}

// ---- Landlocked salmon ---------------------------------------------------------------

internal fun landlockedSalmonPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = coldPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        ColdPhaseKind.COLD -> "Salmon are deep and slow in the coldest water, holding where the smelt hold."
        ColdPhaseKind.SPRING -> "Ice is off and salmon are chasing smelt near the surface and close to shore. This is the best window of the year."
        ColdPhaseKind.SUMMER -> "The surface is too warm, so salmon have followed the smelt down to the thermocline, holding roughly thirty to fifty feet down."
        ColdPhaseKind.FALL -> "Cooling water has salmon back up near the surface and staging off the tributary mouths before their spawning run."
    }
    val headline = core + coldMoodClause(w, mood)

    val tacticLine = when (phase) {
        ColdPhaseKind.SPRING, ColdPhaseKind.FALL ->
            "Troll a smelt-imitating streamer like a Grey Ghost, a thin spoon, or a stickbait in the top ten feet, or cast from shore. Cover water, because salmon are fast and roam."
        ColdPhaseKind.SUMMER ->
            "Go deep to the thermocline with lead-core line or a downrigger, trolling copper, gold, or silver smelt imitations at thirty to fifty feet."
        ColdPhaseKind.COLD ->
            "Troll or jig deep and slow near the smelt schools, keeping it in the strike zone."
    }
    val whyBrief = when {
        mood == Mood.FEEDING -> "A dropping barometer adds a feed, but salmon really key on cold water and the smelt they chase."
        phase == ColdPhaseKind.SUMMER -> "Salmon need cold water, so when the surface warms they follow the smelt down to the thermocline and stay there."
        else -> "Salmon feed in the top of the water column whenever it is cold, which is why ice-out and fall near the surface are best."
    }

    val (whereBrief, whereMore) = when (phase) {
        ColdPhaseKind.SPRING, ColdPhaseKind.FALL -> Pair(
            "Fish the top ten feet and near shore. In cold water salmon run high and roam, following smelt.",
            "At ice-out and again in the fall the surface is cold, so salmon feed up high and near shore, chasing smelt. In fall they also stack off the mouths of tributaries before running up to spawn, so work those areas.",
        )
        ColdPhaseKind.SUMMER -> Pair(
            "Fish the thermocline, roughly thirty to fifty feet down. Salmon have followed the smelt into the cold layer.",
            "As the surface warms, both the smelt and the salmon drop to the thermocline, the band where cold water meets warm. A depth finder to locate that layer and the bait schools is the whole game in summer.",
        )
        ColdPhaseKind.COLD -> Pair(
            "Fish deep near the smelt. In the coldest water salmon hold with the bait and feed slowly.",
            "They stay near the smelt schools in the deeper water and feed in short windows, so find the bait and keep your lure near it.",
        )
    }

    return coldwaterGamePlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        coldWhenBrief(windows, mood, low = true),
        howMore = "Salmon eat smelt, so smelt is the whole idea: a slim silver streamer or spoon that matches one, trolled fast enough to look alive. Up high in spring and fall, deep on lead core or a downrigger in summer. They are fast, aggressive fish, so cover water and do not be shy with trolling speed.",
        whyMore = "Landlocked salmon are smelt-chasers that need cold water. Their year tracks the smelt and the thermometer: at ice-out both are near the surface and salmon feed up high and close to shore, the best fishing of the year. As summer warms the surface, smelt and salmon drop to the thermocline and you have to go down to them. In fall they rise again and stage off tributary mouths to spawn. Weather nudges a day, but temperature and bait set the pattern.",
    )
}

// ---- Lake trout (togue) --------------------------------------------------------------

internal fun lakeTroutPlan(sp: Species, c: Conditions, w: WeatherRead, date: LocalDate, timing: DayTiming?): GamePlan {
    val phase = coldPhase(c.waterF, date.monthValue)
    val mood = w.mood()
    val windows = windowsText(timing, Side.FISH)

    val core = when (phase) {
        ColdPhaseKind.COLD -> "Togue are holding near the bottom on deep structure, biting a slow presentation right in their face."
        ColdPhaseKind.SPRING -> "Just after ice-out, togue are shallow chasing baitfish where the water is warmest. They will take a bait trolled up high."
        ColdPhaseKind.SUMMER -> "Togue have gone deep to the cold water, holding on or near the bottom below forty-five feet, often much deeper, on rock structure."
        ColdPhaseKind.FALL -> "Cooling water is pulling togue back shallower onto rocky reefs and shoals to spawn, in twenty to sixty feet."
    }
    val headline = core + coldMoodClause(w, mood)

    val tacticLine = when (phase) {
        ColdPhaseKind.SPRING ->
            "Flat-line troll shad-style crankbaits or thick spoons up high, around two miles an hour, over the warmer shallow water."
        ColdPhaseKind.SUMMER ->
            "Get to the bottom on deep structure. Vertical-jig a spoon or tube like you would through the ice on points, reefs, and rock piles, or troll deep with a downrigger or lead core."
        ColdPhaseKind.FALL ->
            "Fish rocky reefs, shoals, and humps in twenty to sixty feet. Jig or troll the structure where they stage to spawn."
        ColdPhaseKind.COLD ->
            "Vertical-jig a spoon or tube slow on the deepest rock structure, letting it sit near the bottom."
    }
    val whyBrief = when {
        phase == ColdPhaseKind.SUMMER -> "Togue want water near fifty degrees, so in summer they are deeper than any other fish, glued to the cold bottom."
        phase == ColdPhaseKind.SPRING -> "Right after ice-out the whole lake is cold, so togue can feed shallow where the baitfish and the first warmth are."
        else -> "Togue live near the cold bottom and move shallower only when the whole water column cools, in spring and fall."
    }

    val (whereBrief, whereMore) = when (phase) {
        ColdPhaseKind.SPRING -> Pair(
            "Fish shallow, up high. Just after ice-out togue chase baitfish in the warmer shallow water.",
            "With the lake in the low 40s, togue and their baitfish gather where the water is a few degrees warmer and the food chain is active, often up near the surface and along shallow shorelines. This is the one time you catch them shallow.",
        )
        ColdPhaseKind.SUMMER -> Pair(
            "Fish deep on rock. In summer togue hold on the bottom below forty-five feet, sometimes past a hundred, on hard structure.",
            "They want water near fifty degrees, which in summer means deep. Find solid, distinct bottom: long underwater points, boulder reefs, sunken shoals, and isolated rock piles, and fish right on it.",
        )
        ColdPhaseKind.FALL -> Pair(
            "Fish rocky reefs and shoals in twenty to sixty feet. Cooling water pulls togue up to spawn on rock.",
            "As the lake cools, togue move onto rocky humps, reefs, and shoals to spawn. Focus on that hard, rocky structure in the twenty-to-sixty-foot range where they stage.",
        )
        ColdPhaseKind.COLD -> Pair(
            "Fish the deepest rock structure. Togue hold near the bottom and feed on anything that passes close.",
            "They sit on deep points, reefs, and rock piles and will not move far, so put the lure on the bottom and work it slow and vertical.",
        )
    }

    return coldwaterGamePlan(
        phase.label, headline, tacticLine, whyBrief, whereBrief, whereMore,
        coldWhenBrief(windows, mood, low = true),
        howMore = "Togue pull hard and hit a slow presentation near the bottom. In summer, jigging a heavy spoon or tube vertically on deep rock is the same game as ice fishing and often out-produces trolling. In spring, a flat-lined crankbait or spoon up high does it. A good sonar to find both the fish and the hard bottom is worth more than any single lure.",
        whyMore = "Lake trout are the deepest, coldest coldwater fish, keyed to water near fifty degrees on hard bottom. That drives everything: shallow and catchable for a short window at ice-out, then deep on rock all summer as they chase the cold, then back onto rocky reefs and shoals to spawn in the fall. They are structure fish first, so finding the right rock in the right temperature band beats any lure choice.",
    )
}

/** Shared assembly for the coldwater plans, keeping their Where/How/Why species-specific. */
private fun coldwaterGamePlan(
    phaseLabel: String,
    headline: String,
    tacticLine: String,
    whyBrief: String,
    whereBrief: String,
    whereMore: String,
    whenBrief: String,
    howMore: String,
    whyMore: String,
): GamePlan = GamePlan(
    phaseLabel = phaseLabel,
    headline = headline,
    tacticLine = tacticLine,
    whyBrief = whyBrief,
    sections = listOf(
        PlanSection(PlanKind.WHERE, "Where", whereBrief, whereMore),
        PlanSection(PlanKind.WHEN, "When", whenBrief, COLD_WHEN_MORE),
        PlanSection(PlanKind.HOW, "How", tacticLine, howMore),
        PlanSection(PlanKind.WHY, "Why", whyBrief, whyMore),
    ),
)
