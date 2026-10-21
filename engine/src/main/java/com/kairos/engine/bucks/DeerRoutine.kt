package com.kairos.engine.bucks

/**
 * What whitetails are usually doing at each part of the day, from GPS-collar research and the
 * mentor consensus, so a 3 AM photo can be read ("probably on or near food; his morning photos
 * point toward his bed"). These are the herd's habits, NOT proof about one buck: every line the
 * app shows from here is labeled "research" or "mentors" and cites its source.
 *
 * External sources were read 2026-10-07; mentor rules are in notes/rules/merged.
 */
data class RoutineSource(val id: String, val title: String, val url: String?, val note: String)

object RoutineSources {
    val PSU = RoutineSource(
        "psu", "Penn State Deer-Forest Study: \"Wandering in the Moonlight\"",
        "https://www.deer.psu.edu/?p=4378",
        "GPS-collared adult does, Susquehannock, Bald Eagle and Rothrock state forests (PA): 120-140 m/hr at dawn and dusk, " +
            "most active within 2 h of sunrise or sunset, about 60 m/hr at night, under 50 m/hr in the day.",
    )
    val MSU_BUCKS = RoutineSource(
        "msu", "MSU Extension: research sheds light on the best times to hunt bucks (Castle & Demarais)",
        "https://accessibility.extension.msstate.edu/news/extension-outdoors/2020/research-sheds-light-best-times-hunt-bucks",
        "40+ GPS-collared bucks aged 2+ in Mississippi, a fix every 15 min for two seasons: about one-third of buck movement in daylight " +
            "in Oct-Nov, over 40% in the rut; ~1,500 yd a day pre-rut, 2,000+ in the rut; a buck may use 10+ bedding areas a month.",
    )
    val AUBURN = RoutineSource(
        "auburn", "National Deer Association: \"There Is No Such Thing as a Nocturnal Buck\"",
        "https://deerassociation.com/there-is-no-such-thing-as-a-nocturnal-buck/",
        "Cites an Auburn University GPS-collar study: bucks and does moved in unhunted cover in the day and fed in hunted food plots at night.",
    )
    val QDMA_AGE = RoutineSource(
        "qdma", "QDMA / Alabama Division of Wildlife & Freshwater Fisheries: Field-Judging Live White-tailed Bucks",
        "https://www.outdooralabama.com/sites/default/files/Hunting/Hunter%20Ed/FieldJudgingLiveDeer.pdf",
        "Body traits by age class (1.5 to 5.5+); antler points are not a reliable age sign in good habitat (yearlings can carry 6-10 points); " +
            "yearlings rarely spread over 14 in; ear tips are ~13-14 in apart on 1.5-2.5 bucks, ~15 in on 3.5+; 1.5s often enter feeding areas earlier " +
            "and travel with doe groups; 4.5s are more nocturnal.",
    )
    val all = listOf(PSU, MSU_BUCKS, AUBURN, QDMA_AGE)
}

/** One part of the day: what deer are usually doing then, where a photo then most likely sits, and what to do with it. */
data class RoutineRole(
    val light: Light,
    val doing: String,
    val likelyFromTo: String,
    val use: String,
    val sources: List<String>,   // RoutineSource ids
    val mentorRules: List<String>,
)

object DeerRoutine {
    fun role(light: Light): RoutineRole = when (light) {
        Light.EVENING -> RoutineRole(
            light,
            doing = "Peak movement: the 2 hours around sunset are the busiest of the day (120-140 m/hr).",
            likelyFromTo = "Most likely out of bed and heading toward food; mature bucks hang back in staging cover off the food until near dark.",
            use = "His travel direction here points back to his bed (behind him) and ahead to food. Hunt between, closer to the bed, on the staging edge.",
            sources = listOf("psu"),
            mentorRules = listOf("m-stand_placement-evening-near-bedding-staging-01", "m-stand_placement-staging-cover-off-field-edge-01", "m-stand_placement-on-the-way-food-not-destination-01"),
        )
        Light.NIGHT -> RoutineRole(
            light,
            doing = "Lower but steady movement (~60 m/hr). Deer feed in bouts, roughly every 5 hours, and bed down near food in between.",
            likelyFromTo = "Most likely on or near food, or walking between food sources. Night photos say where he eats, not where he lives in daylight.",
            use = "Look for the food he's on near this camera. To find his bed, use his dawn photos: the direction he walks at first light points to it.",
            sources = listOf("psu", "auburn"),
            mentorRules = listOf("m-wth-feeding-cycle-01", "m-where_to_hunt-daylight-moves-in-cover-01", "m-scouting-night-only-not-nocturnal-01"),
        )
        Light.DAWN -> RoutineRole(
            light,
            doing = "Second movement peak: within 2 hours of sunrise (120-140 m/hr).",
            likelyFromTo = "Most likely coming off food and heading back to bed.",
            use = "The way he's walking points toward his bedding. Mature bucks bed farthest back from food; look for the thickest cover that way.",
            sources = listOf("psu"),
            mentorRules = listOf("m-scouting-check-cam-history-01", "m-where_to_hunt-bedding-layers-from-food-01", "m-scouting-rub-direction-01"),
        )
        Light.MORNING, Light.MIDDAY, Light.AFTERNOON -> RoutineRole(
            light,
            doing = "Lowest movement of the day (<50 m/hr). Mostly bedded; short stand-up bouts to stretch, browse and re-bed.",
            likelyFromTo = "Most likely in or right beside his bedding cover. A daylight photo here means this camera is inside his daylight core.",
            use = "Gold: hunt this spot in daylight, but don't push into his bed. Mature bucks move only ~200-300 yards in daylight outside the rut.",
            sources = listOf("psu", "msu"),
            mentorRules = listOf("m-where_to_hunt-small-daylight-core-01", "m-where_to_hunt-hunt-the-route-not-the-bed-01"),
        )
    }

    /** The day as a whole, for the buck page reference card. */
    val daySummary = listOf(
        "Deer are crepuscular: two peaks, the 2 hours around sunrise and sunset (Penn State GPS).",
        "Bucks: about 1/3 of movement in daylight in Oct-Nov, over 40% in the rut (MSU, 40+ bucks).",
        "Deer move in cover by day and feed in food plots at night (Auburn GPS, via NDA).",
        "A buck may use 10+ different beds a month (MSU), so hunt his route, not one bed.",
    )
}

// ---------------------------------------------------------------------------------------------

/** Field-judging age questions (QDMA / Alabama DWFF). Answer yes / no / can't tell from the photos. */
enum class AgeQ(val text: String, val hint: String) {
    DOE_LIKE("Does he look like a doe with antlers?", "Thin neck, a clear line where neck meets shoulders, little muscle, long-legged look."),
    WITH_DOES("Is he usually with does and fawns, or first into the food?", "Yearlings often enter feeding areas earlier and travel with doe family groups."),
    DEEP_CHEST("Is his chest deeper than his hindquarters?", "The 'racehorse' look: big front end, tight waist. If his back end looks bigger, he's young."),
    NECK_BLENDS("Does his neck blend into his shoulders with no clear line?", "Rut swelling makes this clearer later; before the rut, judge the shoulders and chest."),
    BELLY("Is his belly as deep as his chest, or sagging (potbelly)?", "A deep, sagging belly and a sway back come with 5.5+."),
    WIDE("Are his antlers wider than his ears?", "Ear tips in a relaxed pose are ~13-14 in apart (15 in on 3.5+). Yearlings rarely spread past 14 in."),
}

enum class AgeAnswer { YES, NO, UNSURE }

data class AgeEstimate(val label: String, val low: Double, val high: Double, val why: List<String>, val confident: Boolean)

object AgeQuiz {
    /**
     * Body-first, as the QDMA guide says (points are not a reliable age sign). Returns a range.
     * Unanswered or "can't tell" questions are skipped; too few answers = a wide range.
     */
    fun estimate(a: Map<AgeQ, AgeAnswer>): AgeEstimate? {
        fun yes(q: AgeQ) = a[q] == AgeAnswer.YES
        fun no(q: AgeQ) = a[q] == AgeAnswer.NO
        val answered = a.values.count { it != AgeAnswer.UNSURE }
        if (answered == 0) return null
        val why = ArrayList<String>()
        val (lo, hi) = when {
            yes(AgeQ.NECK_BLENDS) && yes(AgeQ.BELLY) -> { why += "Neck blends into shoulders and a deep/sagging belly: 5.5+ traits."; 5.5 to 7.5 }
            yes(AgeQ.NECK_BLENDS) -> { why += "Neck blends into shoulders, front end one mass: 4.5+ trait."; 4.5 to 5.5 }
            yes(AgeQ.DEEP_CHEST) -> { why += "Chest deeper than hindquarters (racehorse look): 3.5 trait."; 3.5 to 4.5 }
            yes(AgeQ.DOE_LIKE) -> { why += "Doe-like body with antlers: yearling (1.5) trait."; 1.5 to 1.5 }
            no(AgeQ.DOE_LIKE) && no(AgeQ.DEEP_CHEST) -> { why += "Past the yearling look but no deep chest yet: 2.5 trait (thin waist and shoulders)."; 2.5 to 2.5 }
            no(AgeQ.DOE_LIKE) -> { why += "Not doe-like: older than a yearling."; 2.5 to 4.5 }
            else -> 1.5 to 4.5
        }
        var low = lo; var high = hi
        if (yes(AgeQ.WITH_DOES) && low <= 2.5) { why += "Travels with does / first to food: points to a yearling."; high = minOf(high, 2.5) }
        if (yes(AgeQ.WIDE)) { why += "Spread wider than his ears: yearlings rarely pass 14 in."; low = maxOf(low, 2.5); if (high < low) high = low }
        val label = when {
            high <= 1.5 -> "1.5 (yearling)"
            low == high -> "${fmt(low)}"
            high >= 7.5 -> "${fmt(low)}+"
            else -> "${fmt(low)} to ${fmt(high)}"
        } + " years"
        return AgeEstimate(label, low, high, why, confident = answered >= 3)
    }

    /** What the age class changes in how you hunt him (sourced). */
    fun tactics(e: AgeEstimate): List<Pair<String, String?>> = when {
        e.high <= 2.5 -> listOf(
            "Young bucks move earlier and travel with doe groups: expect him on the open edges and doe trails, in daylight more often." to "qdma",
            "Mature bucks avoid the open-edge trails the does and young bucks use; if you're after an older buck, don't build a plan off him." to "m-stand_placement-staging-cover-off-field-edge-01",
        )
        e.low >= 4.5 -> listOf(
            "4.5+ bucks are more nocturnal (QDMA): expect more night photos and a smaller daylight window." to "qdma",
            "Hunt his small daylight core: outside the rut, ~200-300 yards from bed to food." to "m-where_to_hunt-small-daylight-core-01",
            "He beds farthest back from food, past the does and young bucks." to "m-where_to_hunt-bedding-layers-from-food-01",
            "He uses the wind on his way in; set up off his approach." to "m-wind-old-buck-uses-wind-01",
            "Stay out of his core until he shows in daylight." to "m-wth-wait-for-daylight-pattern-01",
        )
        else -> listOf(
            "Middle-aged bucks (2.5-3.5) still move in daylight more than old ones; staging cover off food is the evening play." to "m-stand_placement-evening-near-bedding-staging-01",
            "Hunt on the way to food, not on it." to "m-stand_placement-on-the-way-food-not-destination-01",
        )
    }

    private fun fmt(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else x.toString()
}
