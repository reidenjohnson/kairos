package com.kairos.engine.bucks

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Turns a [BuckReport] into the plan for that buck: when, under what conditions, where, which
 * winds, his rhythm, what data is missing, and which mentor rules back (or push against) it.
 * Plan lines quote his numbers; mentor advice is linked by rule id and labeled as mentor advice.
 */
object BuckPlanner {
    fun plan(r: BuckReport, cams: Map<String, CameraWx>, zone: ZoneId, now: Long): Plan {
        val n = r.visits.size
        val legal = r.visits.filter { it.f?.legal == true }
        val confidence = when {
            n == 0 -> "No visits yet"
            n < 5 -> "Early read: $n visit${if (n == 1) "" else "s"}. Treat every pattern below as a lead, not a rule."
            n < 15 -> "Building: $n visits. Strong marks are real; leaning ones need more photos."
            else -> "Solid sample: $n visits."
        }
        val best = r.cameras.firstOrNull()
        val bestName = best?.camera?.name
        val strong = r.findings.filter { it.bin.verdict == Verdict.STRONG && it.factorKey != "light" && it.factorKey != "phase" }
        val leaning = r.findings.filter { it.bin.verdict == Verdict.LEANING && it.factorKey != "light" && it.factorKey != "phase" }
        val avoids = r.findings.filter { it.bin.verdict == Verdict.AVOIDS && it.factorKey != "phase" }

        // ---- When
        val whenLines = ArrayList<String>()
        val window = timeWindow(legal)
        if (n > 0) {
            whenLines += "${legal.size} of $n visits in legal shooting light (30 min before sunrise to 30 min after sunset)."
            if (window != null) whenLines += window.text
            if (legal.isEmpty()) {
                closestToLight(r, zone)?.let { whenLines += it }
            }
            r.light?.bins?.filter { it.verdict == Verdict.STRONG || it.verdict == Verdict.LEANING }?.forEach {
                whenLines += "${it.label}: ${it.visits} of ${r.light.n} visits vs ${(it.baseShare * 100).roundToInt()}% of hours (${it.verdict.label.lowercase()})."
            }
        }

        // ---- Conditions
        val cond = (strong + leaning).map { "${it.bin.verdict.label}: ${it.factorTitle.lowercase()}, ${it.bin.label.lowercase()}. ${it.text}" }
        val avoid = avoids.map { "${it.factorTitle}: ${it.bin.label.lowercase()}. ${it.text}" }

        // ---- Where
        val where = ArrayList<String>()
        r.cameras.forEach { c ->
            where += "${c.camera.name}: ${c.visits} visit${if (c.visits == 1) "" else "s"}, ${c.legalVisits} in shooting light, last ${fmtAgo(c.daysSinceLast)}."
        }
        r.moves.forEach { m ->
            val from = cams[m.fromId]?.cam?.name ?: "?"
            val to = cams[m.toId]?.cam?.name ?: "?"
            val geo = if (m.distanceM != null && m.bearingDeg != null) " (${yards(m.distanceM)} ${compass8(m.bearingDeg)})" else ""
            where += "Moves $from to $to$geo: ${m.count}x, median ${fmtMinutes(m.medianGapMin)} between photos. Hunt the line between them, closer to whichever end he hits in light."
        }

        // ---- Wind for a stand at each camera
        val wind = ArrayList<String>()
        r.cameras.forEach { c ->
            val a = c.approachDeg ?: return@forEach
            val safe = safeWinds(a)
            val bad = safeWinds((a + 180) % 360)
            wind += "${c.camera.name}: he comes from the ${compass8(a)}. Winds from $safe carry your scent away from his approach. Skip $bad winds: they blow it at him."
        }
        if (r.windVsTravel.values.sum() >= 2) {
            wind += "How he walks the wind: " + r.windVsTravel.entries.joinToString(", ") { "${it.key.lowercase()} ${it.value}" } + "."
        }

        // ---- Rhythm
        val rhythm = ArrayList<String>()
        r.cameras.forEach { c ->
            val g = c.medianGapDays ?: return@forEach
            rhythm += "${c.camera.name}: back every ${g.r1()} days (median). Last seen ${fmtAgo(c.daysSinceLast)}" +
                if (c.due) ", so he's due." else "."
        }
        if (r.weeks.size >= 2) {
            val last = r.weeks.last()
            val prev = r.weeks.dropLast(1)
            val prevLegal = prev.sumOf { it.legalVisits }.toDouble() / prev.sumOf { it.visits }.coerceAtLeast(1)
            val nowLegal = last.legalVisits.toDouble() / last.visits.coerceAtLeast(1)
            if (last.visits >= 2) rhythm += "Daylight share: ${(nowLegal * 100).roundToInt()}% this week vs ${(prevLegal * 100).roundToInt()}% before."
        }

        // ---- Gaps
        val gaps = ArrayList<String>()
        val noWx = r.visits.count { it.f == null }
        if (noWx > 0) gaps += "$noWx visit${if (noWx == 1) " has" else "s have"} no weather yet (camera location missing or weather not downloaded)."
        r.cameras.filter { it.camera.lat == null }.forEach { gaps += "Set ${it.camera.name}'s location: weather, sun times and travel lines need it." }
        if (n in 1..9) gaps += "Patterns firm up around 10-15 visits; with $n, only big, repeated differences can show as Strong."
        if (r.testsRun > 0) {
            val chance = (r.testsRun * 0.05).roundToInt()
            gaps += "${r.testsRun} condition checks were run; by luck alone about ${chance.coerceAtLeast(0)} could come up Strong. Trust repeats over one-offs."
        }

        // ---- Scouting: where his bed and food likely are (herd habits + his travel directions)
        val scout = ArrayList<String>()
        for (c in r.cameras) {
            val cam = cams[c.camera.id]?.cam ?: continue
            val v = r.visits.filter { it.cameraId == cam.id }
            fun dir(lights: Set<Light>) = circularMean(v.filter { it.f?.light in lights }.mapNotNull { BuckInsights.travelBearing(it, cam) })
            val toBed = dir(setOf(Light.DAWN, Light.MORNING))
            val toFood = dir(setOf(Light.EVENING, Light.AFTERNOON))
            val night = v.count { it.f?.light == Light.NIGHT }
            if (toBed != null) scout += "${cam.name}: at dawn he walks ${compass8(toBed)}. Deer head back to bed off food at first light, so his bed is likely ${compass8(toBed)} of this camera, in the thickest cover (mature bucks bed farthest back from food)."
            if (toFood != null) scout += "${cam.name}: in the evening he walks ${compass8(toFood)}. Food is likely that way; his bed is behind him to the ${compass8((toFood + 180) % 360)}. Staging cover between is the evening sit."
        }
        if (scout.isEmpty() && n > 0) {
            val nightCams = r.visits.filter { it.f?.light == Light.NIGHT }.mapNotNull { cams[it.cameraId]?.cam?.name }.distinct()
            if (nightCams.isNotEmpty()) scout += "He's been at ${nightCams.joinToString(" and ")} after dark. At night deer are mostly on or near food, so look for what he's eating within a few hundred yards of ${if (nightCams.size == 1) "it" else "them"}."
            scout += "His daylight bed is likely in the thickest cover nearby: mature bucks bed farthest back from food, and outside the rut move only ~200-300 yards in daylight."
        }

        // ---- Headline
        val headline = when {
            n == 0 -> "No visits sorted to this buck yet."
            legal.isEmpty() -> "Night-only so far, at ${r.cameras.map { it.camera.name }.ifEmpty { listOf("your cameras") }.joinToString(" and ")}. Hunt the edges of light on the winds he moves on."
            else -> buildString {
                append("Hunt ${bestName ?: "his best camera"}")
                if (window != null) append(", ${window.light.label.lowercase()}")
                val top = (strong + leaning).take(2).map { it.bin.label.lowercase() }
                if (top.isNotEmpty()) append(", when it's ${top.joinToString(" and ")}")
                append('.')
            }
        }

        val hold = n > 0 && legal.isEmpty()
        val camsSeen = r.cameras.map { it.camera.name }

        // Pattern: 5+ visits AND either a Strong condition or most visits in one camera + light period.
        val cells = r.visits.groupingBy { "${it.cameraId}|${it.f?.light}" }.eachCount()
        val topShare = if (n == 0) 0.0 else (cells.values.maxOrNull() ?: 0).toDouble() / n
        val patternReady = n >= 5 && (strong.isNotEmpty() || topShare >= 0.6)
        val times = r.visits.joinToString(", ") { clock(it.start, zone) }
        val pattern = when {
            n == 0 -> "No visits yet."
            patternReady -> "Pattern forming: $n visits, ${(topShare * 100).roundToInt()}% at the same camera and time of day" + if (strong.isNotEmpty()) ", ${strong.size} strong condition${if (strong.size == 1) "" else "s"}." else "."
            else -> "No pattern yet: $n visit${if (n == 1) "" else "s"} at ${camsSeen.size} camera${if (camsSeen.size == 1) "" else "s"} ($times). A pattern needs about 5+ visits that repeat."
        }
        val patient = if (!patternReady && n > 0) Step(
            "Wait for the pattern",
            "Stay out of his core until he gives you a reason: night-only photos mean he's using the ground, not that he's huntable. Leave the cameras where they are and check them no more than about every 14 days (or use cell cameras), then go when he shows in daylight regularly.",
            listOf("m-wth-wait-for-daylight-pattern-01", "m-scouting-limit-camera-checks-01"),
        ) else null
        val aggressive = if (!patternReady && n > 0) Step(
            "Or push it",
            "A photo means he's in that area far more often than the camera shows (thermal-drone research). If you go now, go on a major weather day (a day ${Factors.units.tempDeltaNum(15.0)}${Factors.units.tempUnit}+ colder than the one before), only on a wind that keeps your scent off where he's been, with a clean way in and out, and consider a couple of stand options there.",
            listOf("m-scouting-one-photo-means-more-01", "m-wth-wait-for-daylight-pattern-01", "m-wth-big-temp-drop-01"),
        ) else null

        val actions = ArrayList<Step>()
        if (hold) {
            actions += Step(
                "Sit the edge of light on his winds",
                "All $n of his visits are after dark" + (closestToLight(r, zone)?.let { " (${it.replaceFirstChar { c -> c.lowercase() }})" } ?: "") + ". A photo means he's around far more than the camera shows (thermal-drone research), so time in the stand is how you learn him. Pick a sit below where the wind matches one he's moved on, set up so it carries your scent away from where he's been, and go in and out clean.",
                listOf("m-scouting-one-photo-means-more-01", "m-wind-dont-force-wrong-wind-01"),
            )
            actions += Step(
                "Find his food",
                "At night deer are mostly on or near food (Auburn and Penn State GPS research). Walk a few hundred yards around ${camsSeen.joinToString(" and ")} at midday and look for fresh feeding sign.",
                listOf("m-scouting-find-bed-and-food-01"),
            )
            actions += Step(
                "Find his daylight cover",
                "Mature bucks bed farthest back from food, in the thickest cover, and move only about 200-300 yards in daylight outside the rut. Hang a camera on the edge of it.",
                listOf("m-where_to_hunt-bedding-layers-from-food-01", "m-where_to_hunt-small-daylight-core-01"),
            )
        } else if (n > 0) {
            window?.let { w -> actions += Step("Sit ${bestName ?: "his best camera"} in the ${w.light.label.lowercase()}", w.text.substringAfter(": ")) }
            (strong + leaning).firstOrNull()?.let { actions += Step("Pick the day by his weather", "He leans to ${it.bin.label.lowercase()} (${it.factorTitle.lowercase()}). ${it.text}") }
            r.cameras.firstOrNull { it.due }?.let { actions += Step("${it.camera.name}: he's due", "Back every ${it.medianGapDays?.r1()} days (median); last seen ${fmtAgo(it.daysSinceLast)}.", listOf("m-wth-dont-chase-fresh-photo-01")) }
        }
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        val daysToRut = (Factors.rut.rutStart.atYear(today.year).toEpochDay() - today.toEpochDay()).toInt()
        if (daysToRut in 1..35) actions += Step(
            "The rut is $daysToRut days out",
            "${Factors.rut.label(SeasonPhase.RUT).substringAfter("Rut ").trim('(', ')')}. Buck daylight movement climbs past 40% then (MSU GPS study of 40+ bucks): that's when he's likeliest to step into shooting light.",
            listOf("m-wth-rut-go-regardless-01"),
        )

        return Plan(
            headline = headline,
            confidence = confidence,
            bestCameraId = best?.camera?.id,
            when_ = whenLines,
            conditions = cond,
            avoid = avoid,
            where = where,
            wind = wind,
            rhythm = rhythm,
            gaps = gaps,
            scout = scout,
            mentors = mentors(r, legal.size),
            window = window,
            actions = actions,
            hold = hold,
            pattern = pattern,
            patternReady = patternReady,
            patient = patient,
            aggressive = aggressive,
        )
    }

    /** For a night-only buck: his visit nearest to shooting light, as clock times. */
    private fun closestToLight(r: BuckReport, zone: ZoneId): String? {
        val v = r.visits.filter { it.f?.legal == false && it.f.minsToSunset != null && it.f.minsFromSunrise != null }
        // Distance to legal light either side: after evening light ends, or before morning light starts.
        val best = v.minByOrNull { minOf(-it.f!!.minsToSunset!! - 30, -it.f.minsFromSunrise!! - 30).let { d -> if (d < 0) Int.MAX_VALUE else d } } ?: return null
        val f = best.f!!
        val after = -f.minsToSunset!! - 30
        val before = -f.minsFromSunrise!! - 30
        val endLight = best.start + f.minsToSunset * 60L + 30 * 60L
        val startLight = best.start - f.minsFromSunrise * 60L - 30 * 60L
        return if (after in 0..(12 * 60) && (before < 0 || after <= before)) {
            "Earliest evening visit ${clock(best.start, zone)}; shooting light ended ${clock(endLight, zone)}"
        } else {
            "Latest morning visit ${clock(best.start, zone)}; shooting light started ${clock(startLight, zone)}"
        }
    }

    /** His typical shooting-light window, from the legal visits' minutes to sunset / from sunrise. */
    fun timeWindow(legal: List<Visit>): TimeWindow? {
        if (legal.isEmpty()) return null
        val evening = legal.filter { it.f!!.light == Light.EVENING || it.f.light == Light.AFTERNOON }
        val morning = legal.filter { it.f!!.light == Light.DAWN || it.f.light == Light.MORNING }
        return if (evening.size >= morning.size && evening.isNotEmpty()) {
            val m = evening.mapNotNull { it.f?.minsToSunset?.toDouble() }.sorted()
            val lo = m.first().roundToInt(); val hi = m.last().roundToInt()
            TimeWindow(Light.EVENING, "Evenings: ${evening.size} legal visits from ${beforeAfter(hi, "sunset")} to ${beforeAfter(lo, "sunset")} (median ${beforeAfter(m.median()!!.roundToInt(), "sunset")}). Be settled before the earliest.")
        } else if (morning.isNotEmpty()) {
            val m = morning.mapNotNull { it.f?.minsFromSunrise?.toDouble() }.sorted()
            TimeWindow(Light.DAWN, "Mornings: ${morning.size} legal visits from ${afterBefore(m.first().roundToInt(), "sunrise")} to ${afterBefore(m.last().roundToInt(), "sunrise")} (median ${afterBefore(m.median()!!.roundToInt(), "sunrise")}). Be in before first light.")
        } else {
            TimeWindow(Light.MIDDAY, "Midday: ${legal.size} legal visits in the middle of the day. Plan an all-day sit.")
        }
    }

    private fun beforeAfter(minsToSunset: Int, what: String) =
        if (minsToSunset >= 0) "$minsToSunset min before $what" else "${-minsToSunset} min after $what"

    private fun afterBefore(minsFrom: Int, what: String) =
        if (minsFrom >= 0) "$minsFrom min after $what" else "${-minsFrom} min before $what"

    /** Winds whose FROM direction is within 67.5° of his approach: they push scent away from him. */
    fun safeWinds(approachDeg: Double): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names.indices.filter { angleBetween(it * 45.0, approachDeg) <= 67.5 }.joinToString("/") { names[it] }
    }

    private fun mentors(r: BuckReport, legalCount: Int): List<MentorLink> {
        val out = ArrayList<MentorLink>()
        val u = Factors.units
        // By bin index (see Factors): labels change with the user's units.
        fun has(key: String, vararg bins: Int, verdicts: Set<Verdict> = setOf(Verdict.STRONG, Verdict.LEANING)) =
            r.findings.any { it.factorKey == key && it.bin.verdict in verdicts && it.bin.index in bins }
        val n = r.visits.size
        out += MentorLink("m-scouting-target-one-buck-01", "You're patterning one buck; this is the approach.")
        if (n >= 3 && legalCount == 0) {
            out += MentorLink("m-wth-wait-for-daylight-pattern-01", "All $n of his visits are outside shooting light.")
            out += MentorLink("m-scouting-night-only-not-nocturnal-01", "Night-only on your cameras doesn't mean he never moves in daylight.")
            out += MentorLink("m-scouting-old-buck-stays-in-core-01", "Night photos can mean he lives elsewhere and visits after dark.")
        } else if (n >= 4 && legalCount * 2 < n) {
            out += MentorLink("m-scouting-day-vs-night-photos-01", "$legalCount of $n visits in shooting light.")
        }
        if (has("front", FrontState.AFTER_COLD_FRONT.ordinal)) {
            out += MentorLink("m-wth-cold-front-trifecta-01", "His visits lean to the day and a half after cold fronts.")
            out += MentorLink("m-wth-front-day-after-morning-01", "Same: after-front movement.")
        }
        if (has("temp_swing", 0, 1)) out += MentorLink("m-wth-big-temp-drop-01", "His visits lean to days colder than the day before.")
        if (has("wind_swing", 0, 1)) out += MentorLink("m-wth-wind-drop-01", "His visits lean to days the wind dropped.")
        if (has("rain", RainState.RAINING.ordinal, RainState.JUST_ENDED.ordinal)) {
            out += MentorLink("m-wth-light-rain-go-01", "He's shown in or right after rain.")
            out += MentorLink("m-wth-be-there-when-rain-stops-01", "Same: rain timing.")
        }
        if (has("temp_normal", 3, 4, verdicts = setOf(Verdict.AVOIDS)) ||
            has("temp_normal", 0, 1)
        ) out += MentorLink("m-wth-warm-weather-hurts-01", "His data ties to cooler-than-normal days.")
        if (has("temp_band", 1, 2)) out += MentorLink("m-wth-evening-temp-band-01", "His visits sit in the ${band(u)} band.")
        if (has("wind_dir", 2, verdicts = setOf(Verdict.AVOIDS))) out += MentorLink("m-wth-east-wind-slow-01", "He rarely shows on an east wind.")
        if (r.findings.any { it.factorKey == "moon" }) out += MentorLink("m-wth-moon-phase-ignore-01", "His data shows a moon lean; the mentors say the moon doesn't matter. Weigh it low until it repeats.", counterpoint = true)
        if (r.findings.any { it.factorKey == "pressure_trend" }) out += MentorLink("m-wth-pressure-alone-no-01", "His data shows a pressure lean; research says pressure alone does little. Read it as part of a front.", counterpoint = true)
        if (r.cameras.any { it.due }) out += MentorLink("m-wth-dont-chase-fresh-photo-01", "Hunt the spot he's due at, not the one he just hit.")
        if (r.cameras.any { c -> c.medianGapDays != null && c.daysSinceLast >= 7 && c.daysSinceLast > 2 * c.medianGapDays }) {
            out += MentorLink("m-scouting-missing-buck-not-dead-01", "He's gone past twice his usual gap.")
        }
        if (r.cameras.any { it.approachDeg != null }) out += MentorLink("m-wind-old-buck-uses-wind-01", "His approach direction is known; set up off it.")
        out += MentorLink("m-wind-dont-force-wrong-wind-01", "Only sit his stand on the winds listed above.")
        out += MentorLink("m-wth-stand-burns-with-use-01", "Each sit costs the stand; save it for his conditions.")
        if (timeWindow(r.visits.filter { it.f?.legal == true })?.light == Light.EVENING) {
            out += MentorLink("m-wth-stand-by-time-of-day-01", "He's an evening buck on camera.")
        }
        val phase = r.visits.lastOrNull()?.f?.phase
        if (phase == SeasonPhase.EARLY || phase == SeasonPhase.PRE_RUT) out += MentorLink("m-wth-target-buck-outside-rut-01", "Before the rut is the best time to kill a specific buck.")
        if (phase == SeasonPhase.RUT) out += MentorLink("m-wth-rut-go-regardless-01", "It's the rut: patterns loosen, time in the stand wins.")
        return out.distinctBy { it.ruleId }
    }

    private fun yards(m: Double): String = if (m < 1600) "${(m * 1.09361).roundToInt()} yd" else "${(m / 1609.34).r1()} mi"
    fun fmtMinutes(m: Int): String = if (m < 90) "$m min" else "${(m / 60.0).r1()} h"
    private fun fmtAgo(days: Int) = when (days) { 0 -> "today"; 1 -> "yesterday"; else -> "$days days ago" }

    fun clock(t: Long, zone: ZoneId): String = Factors.units.clock(t, zone)

    /** The 32-55 °F evening band (Mississippi study) in the user's units. */
    internal fun band(u: UnitFmt) = "${u.tempNum(32.0)}-${u.tempNum(55.0)} ${u.tempUnit}"
}

/** How a weather call is backed: measured (research, with or without hunters) or hunters only. */
enum class Basis(val label: String) { RESEARCH("Research"), HUNTERS("Hunters") }

/**
 * A mentor rule that fired for a forecast window. [agree] = how many people back it (merged
 * rules, counted by person; checked against the shipped rules by RuleLinksTest).
 */
data class MentorHit(val ruleId: String, val label: String, val plus: Boolean, val basis: Basis, val agree: Int, val caveat: String? = null)

/**
 * The mentors' weather calls, each applied at the threshold its source states (rules in
 * notes/rules/merged; audited 2026-10-07). Weighted by consensus, per Reiden: "If EVERYONE is
 * saying go go go, that's good enough." 3+ people = 3 points, 2 = 2, one source = 1 (still taken
 * into account). Research vs hunters and any caveat are shown, not used to drop a call.
 */
object MentorWeather {
    fun hits(h: HourFeatures, u: UnitFmt = Factors.units): List<MentorHit> = buildList {
        fun deg(d: Double) = "${u.tempDeltaNum(d)}${u.tempUnit}"
        if (h.front == FrontState.AFTER_COLD_FRONT) add(MentorHit("m-wth-cold-front-trifecta-01", "After a cold front", true, Basis.HUNTERS, 3, "Research measured a small edge (about 10% to 11% odds, MSU)"))
        h.tempDay24?.let { d ->
            if (d <= -15) add(MentorHit("m-wth-big-temp-drop-01", "Last 24 h ${deg(-d)} colder than the day before", true, Basis.RESEARCH, 3))
            else if (d <= -10) add(MentorHit("m-wth-big-temp-drop-01", "Last 24 h ${deg(-d)} colder than the day before", true, Basis.RESEARCH, 3, "MSU: the effect starts past about ${deg(10.0)} and is clear at ${deg(15.0)}"))
            if (d >= 10) add(MentorHit("m-wth-warm-weather-hurts-01", "Last 24 h ${deg(d)} warmer than the day before", false, Basis.RESEARCH, 3, if (h.phase == SeasonPhase.RUT) "In the rut the sources split: MSU says the rut outweighs weather" else null))
        }
        h.windDay24?.let { if (it <= -7) add(MentorHit("m-wth-wind-drop-01", "Wind down ${u.speed(-it)} from the day before", true, Basis.RESEARCH, 2)) }
        if (h.light == Light.EVENING || h.light == Light.AFTERNOON) h.tempF?.let {
            if (it in 32.0..55.0) add(MentorHit("m-wth-evening-temp-band-01", "Evening in the ${BuckPlanner.band(u)} band", true, Basis.RESEARCH, 1, "Mississippi deer; the study says northern deer likely have a different band"))
        }
        val pr = h.precipIn ?: 0.0
        when {
            pr > 0.10 -> add(MentorHit("m-wth-light-rain-go-01", "Moderate or heavy rain (over ${u.rain(0.10)}/hr)", false, Basis.HUNTERS, 3, "Research says rain isn't a reason to stay home"))
            pr >= 0.01 -> add(MentorHit("m-wth-light-rain-go-01", "Light rain", true, Basis.RESEARCH, 3, "Penn State via Whitetail Properties; no numbers given"))
            h.rain == RainState.JUST_ENDED -> add(MentorHit("m-wth-be-there-when-rain-stops-01", "Rain just ended", true, Basis.HUNTERS, 2))
        }
        h.windDir?.let { dir -> if ((h.windMph ?: 0.0) >= 3 && compass8(dir) == "E") add(MentorHit("m-wth-east-wind-slow-01", "East wind", false, Basis.HUNTERS, 1, "One hunter's saying; never tested")) }
    }

    fun points(hit: MentorHit) = hit.agree.coerceIn(1, 3) * (if (hit.plus) 1 else -1)
}

