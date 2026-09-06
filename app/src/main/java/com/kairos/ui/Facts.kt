package com.kairos.ui

import java.time.LocalDate
import java.time.MonthDay

/**
 * The "facts you'd otherwise Google" — the date-based reference behind each species that
 * the score and Game Plan don't cover: the rut phases for game, the spawn windows for fish.
 * These are calendar-driven, not weather-driven: the rut runs on day length and spawns run
 * on water temperature, so they land the same stretch every year. Each species also carries
 * a link to its official Maine IF&W page. Timing traces to the sources in SOURCES.md.
 */

/** One dated fact. When [start]/[end] are set, the UI flags it as happening now. */
data class TimingFact(
    val label: String,
    val window: String,
    val detail: String,
    val start: MonthDay? = null,
    val end: MonthDay? = null,
) {
    fun isActive(today: LocalDate): Boolean {
        if (start == null || end == null) return false
        val md = MonthDay.from(today)
        return if (!start.isAfter(end)) !md.isBefore(start) && !md.isAfter(end)
        else !md.isBefore(start) || !md.isAfter(end) // window wraps the year end
    }
}

data class SpeciesFacts(
    val title: String,
    val intro: String,
    val facts: List<TimingFact>,
    val officialLabel: String,
    val officialUrl: String,
)

private fun md(m: Int, d: Int) = MonthDay.of(m, d)
private fun f(label: String, window: String, detail: String, s: MonthDay? = null, e: MonthDay? = null) =
    TimingFact(label, window, detail, s, e)

// Official Maine IF&W pages (verified).
private const val FISH_GUIDE = "https://www.maine.gov/ifw/fishing-boating/fishing/maine-fishing-guide/catch-specific-fish.html"
private const val MAMMALS = "https://www.maine.gov/ifw/fish-wildlife/wildlife/species-information/mammals/index.html"
private const val HUNT_SPECIES = "https://www11.maine.gov/ifw/hunting-trapping/hunting/species/index.html"

private val RUT_INTRO = "The rut runs on day length, so it lands the same weeks every year no matter the weather. Weather only changes whether they move in daylight."
private val SPAWN_INTRO = "Spawn timing follows water temperature, so it slides a little earlier or later with the year but always runs in the same order."

/** Fish spawn facts share the guide link and intro; only the windows differ. */
private fun fishFacts(vararg facts: TimingFact) =
    SpeciesFacts("Spawn & seasonal", SPAWN_INTRO, facts.toList(), "Maine IF&W — how to catch this fish", FISH_GUIDE)

fun factsFor(name: String): SpeciesFacts? = when (name) {
    // ---- Hunt: the rut and life cycle ----
    "Whitetail deer" -> SpeciesFacts(
        "The rut, by date", RUT_INTRO,
        listOf(
            f("Pre-rut", "Late Oct", "Bucks open scrapes, rub trees, and start cruising. Rattling and calling start to work.", md(10, 20), md(10, 31)),
            f("Seeking & chasing", "Early Nov", "Bucks are on their feet all day searching for the first does. The best daylight movement of the year.", md(11, 1), md(11, 7)),
            f("Peak breeding", "Mid-Nov", "Most does get bred. A buck locks down with a hot doe for a day or two, so it can seem to go quiet.", md(11, 8), md(11, 20)),
            f("Post-rut & second rut", "Late Nov-Dec", "Worn, hungry bucks return to food. A smaller second rut flares about a month after the peak.", md(11, 21), md(12, 20)),
        ),
        "Maine IF&W — White-tailed deer", "https://www.maine.gov/ifw/fish-wildlife/wildlife/species-information/mammals/deer.html",
    )
    "Moose" -> SpeciesFacts(
        "The rut, by date", "The moose rut is set by day length, landing the same weeks each year. Cool weather keeps bulls moving; heat shuts them down.",
        listOf(
            f("Pre-rut", "Mid-Sep", "Bulls rub, thrash brush, and open rut pits while feeding hard.", md(9, 10), md(9, 19)),
            f("Peak rut", "Late Sep-early Oct", "Bulls cruise for cows and answer calls. The prime window to call one in.", md(9, 20), md(10, 8)),
            f("Post-rut", "Mid-late Oct", "The rut winds down and bulls feed to recover.", md(10, 9), md(10, 31)),
        ),
        "Maine IF&W — Moose", "https://www.maine.gov/ifw/fish-wildlife/wildlife/species-information/mammals/moose.html",
    )
    "Elk" -> SpeciesFacts(
        "The rut, by date", "Elk are not a Maine game species, so this is general timing. The September rut is set by day length.",
        listOf(
            f("Bugling rut", "September", "Bulls bugle and hold harems of cows. Calling is deadly at first and last light.", md(9, 1), md(9, 30)),
            f("Post-rut", "October on", "Bulls drop off the cows, and the herds regroup and feed to recover.", md(10, 1), md(11, 30)),
        ),
        "Maine IF&W — Mammals", MAMMALS,
    )
    "Black bear" -> SpeciesFacts(
        "Feeding & denning", "A bear's fall is one long push to fatten up before it dens for winter (hyperphagia).",
        listOf(
            f("Fall feed-up", "Aug-Nov", "Bears feed almost around the clock: berries and bait early, then acorns and beechnuts.", md(8, 1), md(11, 15)),
            f("Denning", "Nov-Dec", "Bears den up for winter, earlier in a poor natural-food year.", md(11, 16), md(12, 31)),
            f("Cubs born", "January", "Sows give birth in the den to tiny cubs while still asleep for winter.", md(1, 1), md(1, 31)),
        ),
        "Maine IF&W — Bear", "https://www.maine.gov/ifw/hunting-trapping/hunting/species/bear/index.html",
    )
    "Snowshoe hare" -> SpeciesFacts(
        "Molt & breeding", "Hares turn white for winter camouflage and breed spring into summer.",
        listOf(
            f("White coat", "Nov-Mar", "Hares molt to white for winter. In a low-snow year the white-on-brown mismatch makes them easy for predators, and hunters, to spot.", md(11, 1), md(3, 31)),
            f("Breeding", "Mar-Aug", "Several litters through spring and summer keep the population boom-and-bust.", md(3, 1), md(8, 31)),
        ),
        "Maine IF&W — Mammals", MAMMALS,
    )
    "Upland birds" -> SpeciesFacts(
        "Broods & migration", "Grouse are year-round residents; woodcock migrate through in the fall.",
        listOf(
            f("Drumming & nesting", "Apr-Jun", "Grouse drum and nest; woodcock display at dusk over the alders.", md(4, 1), md(6, 30)),
            f("Woodcock flights", "Oct-early Nov", "Migrant woodcock pour through on north winds behind cold fronts, filling the wet alder cover.", md(10, 1), md(11, 10)),
        ),
        "Maine IF&W — Hunting species", HUNT_SPECIES,
    )
    "Waterfowl" -> SpeciesFacts(
        "Migration", "Ducks are weather birds, and the fall migration rides the cold fronts south.",
        listOf(
            f("Fall migration", "Oct-Dec", "New birds arrive within a day or two of each cold front, heaviest from late October into December.", md(10, 1), md(12, 31)),
        ),
        "Maine IF&W — Migratory game birds", "https://www.maine.gov/ifw/hunting-trapping/hunting/laws-rules/migratory-gamebirds.html",
    )
    "Wild turkey" -> SpeciesFacts(
        "Breeding & flocks", "Spring is the breeding and gobbling season; fall is flocks feeding together on the mast.",
        listOf(
            f("Spring gobbling", "Late Apr-Jun", "Toms gobble and strut for hens. The peak of the spring calling game.", md(4, 20), md(6, 6)),
            f("Fall flocks", "Sep-Nov", "Family and gobbler flocks feed together on acorns, beechnuts, and fields.", md(9, 1), md(11, 30)),
        ),
        "Maine IF&W — Wild turkey", "https://www.maine.gov/ifw/hunting-trapping/hunting/laws-rules/wild-turkey.html",
    )
    "Coyote" -> SpeciesFacts(
        "Breeding & pups", "Coyotes are around all year, but the late-winter breeding season fires them up.",
        listOf(
            f("Breeding", "Jan-Mar", "Coyotes are most vocal and most responsive to howls and challenge calls.", md(1, 15), md(3, 15)),
            f("Denning & pups", "Apr-Aug", "Adults are tied to a den feeding pups. Prey-distress calls work well, then pup-distress later in summer.", md(4, 1), md(8, 31)),
        ),
        "Maine IF&W — Furbearers & other species", "https://www.maine.gov/ifw/hunting-trapping/hunting/laws-rules/other-species.html",
    )
    // ---- Fish: the spawn ----
    "Largemouth bass" -> fishFacts(
        f("Spawn", "May-Jun, 60-65°F", "Beds in warm, wind-protected shallows. Big females sit shallow and catchable, so handle them gently.", md(5, 15), md(6, 20)),
        f("Fall feed-up", "Sep-Oct", "Bass gorge on baitfish in the shallows to fatten for winter.", md(9, 1), md(10, 31)),
    )
    "Smallmouth bass" -> fishFacts(
        f("Spawn", "Late May-Jun, 60-65°F", "Beds fanned on gravel and rock in protected areas, a little cooler than largemouth.", md(5, 20), md(6, 25)),
        f("Fall feed-up", "Sep-Oct", "Smallmouth switch from crayfish to chasing baitfish and feed hard before winter.", md(9, 1), md(10, 31)),
    )
    "Brook trout", "Landlocked salmon", "Lake trout (togue)" -> fishFacts(
        f("Ice-out", "Apr-May", "The whole lake is cold, so fish feed shallow and near the surface. The best fishing of the year.", md(4, 1), md(5, 20)),
        f("Fall spawn", "Oct-Nov", "Trout and salmon run to gravel, inlets, and shorelines; togue spawn on rocky shoals.", md(10, 1), md(11, 30)),
    )
    "Northern pike", "Chain pickerel" -> fishFacts(
        f("Spawn", "Early spring, 40s-50s°F", "Among flooded shallow vegetation right after ice-out. Pickerel are among the very first to spawn.", md(4, 1), md(5, 5)),
        f("Fall feed-up", "Sep-Nov", "A season-long feeding frenzy as they pack on weight for winter. The best big-fish window.", md(9, 1), md(11, 15)),
    )
    "Yellow perch" -> fishFacts(
        f("Spawn", "Spring, 45-55°F", "Females drape long egg ribbons over weeds and brush in the shallows.", md(4, 10), md(5, 15)),
    )
    "White perch" -> fishFacts(
        f("Spawn", "Late spring, 55-60°F", "Schools run to shallow flats and tributary mouths to spawn.", md(5, 10), md(6, 15)),
    )
    "Black crappie" -> fishFacts(
        f("Spawn", "Late May-Jun, 60-65°F", "Males fan beds on brushy and rocky cover in the shallows and guard them.", md(5, 20), md(6, 20)),
    )
    "Panfish (sunfish)" -> fishFacts(
        f("Spawn", "Late spring-summer, 68-75°F", "Colonies of saucer-shaped beds in the warm shallows, often repeating through the summer.", md(6, 1), md(7, 15)),
    )
    "Walleye" -> fishFacts(
        f("Spawn", "April, 40-50°F", "Spawn over rock and up tributaries just after ice-out.", md(4, 1), md(4, 30)),
        f("Fall run", "Oct-Nov", "Walleye follow baitfish shallow and feed hard, best in the low-light hours.", md(10, 1), md(11, 30)),
    )
    else -> null
}
