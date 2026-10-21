package com.kairos.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Maine hunting & fishing seasons — data-driven so the statewide-simplified view
 * here can grow WMD/water detail later without a rewrite (see HANDOFF §9).
 *
 * EVERY date comes from an official Maine IF&W (maine.gov) source and is cited
 * per species via [SpeciesSeasons.sourceLabel]/[sourceUrl]. Dates are the
 * 2026-27 license year. Do NOT edit a date without updating its source. Windows
 * that are set by a separate framework (migratory birds) or vary by water
 * (fishing special regs) say so in [SpeciesSeasons.disclaimer].
 */

/** One dated season window for a species. [note] carries WMD/zone/permit caveats. */
data class SeasonWindow(
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
    val note: String = "",
)

/** All season windows for one app species, with its official citation. */
data class SpeciesSeasons(
    val speciesName: String,
    val side: Side,
    val windows: List<SeasonWindow>,
    val sourceLabel: String,
    val sourceUrl: String,
    /** True when Maine has no open season for this species (e.g. elk). */
    val noOpenSeason: Boolean = false,
    /** Extra honesty line: zone dependence, special regs, permit-only, etc. */
    val disclaimer: String = "",
)

enum class SeasonStatusKind { OPEN, UPCOMING, CLOSED, NONE }

/** Where [today] falls relative to a species' windows. */
data class SeasonStatus(
    val kind: SeasonStatusKind,
    val activeWindow: SeasonWindow? = null,
    val nextWindow: SeasonWindow? = null,
    val daysUntilNext: Int? = null,
) {
    /** A short plain-English line for the UI. */
    fun headline(): String = when (kind) {
        SeasonStatusKind.NONE -> "No open season in Maine"
        SeasonStatusKind.OPEN ->
            "Open now — ${activeWindow!!.label} through ${monthDay(activeWindow.end)}"
        SeasonStatusKind.UPCOMING -> {
            val d = daysUntilNext!!
            val whenWord = if (d == 0) "today" else if (d == 1) "tomorrow" else "in $d days"
            "${nextWindow!!.label} opens $whenWord (${monthDay(nextWindow.start)})"
        }
        SeasonStatusKind.CLOSED -> "Closed for the season"
    }
}

/** Compute [today]'s status against a species' windows. */
fun seasonStatus(s: SpeciesSeasons, today: LocalDate): SeasonStatus {
    if (s.noOpenSeason) return SeasonStatus(SeasonStatusKind.NONE)
    // Windows can overlap (e.g. expanded archery spans firearms). Surface the one
    // that started most recently as the "current" marquee season.
    val active = s.windows
        .filter { !today.isBefore(it.start) && !today.isAfter(it.end) }
        .maxByOrNull { it.start }
    if (active != null) return SeasonStatus(SeasonStatusKind.OPEN, activeWindow = active)
    val next = s.windows.filter { it.start.isAfter(today) }.minByOrNull { it.start }
    if (next != null) {
        val days = ChronoUnit.DAYS.between(today, next.start).toInt()
        return SeasonStatus(SeasonStatusKind.UPCOMING, nextWindow = next, daysUntilNext = days)
    }
    return SeasonStatus(SeasonStatusKind.CLOSED)
}

private fun monthDay(d: LocalDate): String {
    val m = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )[d.monthValue - 1]
    return "$m ${d.dayOfMonth}"
}

private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

private const val HUNT_SRC = "Maine IF&W — 2026-27 Hunting Seasons"
private const val HUNT_URL =
    "https://www.maine.gov/ifw/docs/26-MDIFW-6-Hunting-Season-2026-27.pdf"
private const val WATERFOWL_SRC = "Maine IF&W — 2026-27 Migratory Game Bird Seasons"
private const val WATERFOWL_URL =
    "https://www.maine.gov/ifw/hunting-trapping/hunting/laws-rules/migratory-gamebirds.html"

/**
 * The season table — hunting only. Species names match [SPECIES] so the UI can link a
 * score row to its seasons. Fishing is deliberately excluded: South-Zone waters are open
 * all year under general law, so a season adds nothing and the real limits are water-by-
 * water special regs we don't reproduce.
 */
val MAINE_SEASONS: List<SpeciesSeasons> = listOf(
    SpeciesSeasons(
        "Whitetail deer", Side.HUNT,
        listOf(
            SeasonWindow("Expanded archery", d(2026, 9, 12), d(2026, 12, 12), "Designated areas only"),
            SeasonWindow("Regular archery", d(2026, 10, 3), d(2026, 10, 30), "All WMDs"),
            SeasonWindow("Youth deer day", d(2026, 10, 23), d(2026, 10, 24)),
            SeasonWindow("Residents-only day", d(2026, 10, 31), d(2026, 10, 31)),
            SeasonWindow("Firearms", d(2026, 11, 2), d(2026, 11, 28), "All WMDs"),
            SeasonWindow("Muzzleloader (statewide)", d(2026, 11, 30), d(2026, 12, 5)),
            SeasonWindow("Muzzleloader (WMDs 12-18, 20-29)", d(2026, 12, 7), d(2026, 12, 12)),
        ),
        HUNT_SRC, HUNT_URL,
        disclaimer = "Hunting is prohibited on Sundays. Some windows are WMD-specific.",
    ),
    SpeciesSeasons(
        "Moose", Side.HUNT,
        listOf(
            SeasonWindow("Bull-only", d(2026, 9, 28), d(2026, 10, 3), "WMDs 1-7, 10-13, 15, 18, 19, 27, 28"),
            SeasonWindow("Bull-only", d(2026, 10, 12), d(2026, 10, 17), "WMDs 1-15, 17-19, 27, 28"),
            SeasonWindow("Antlerless-only", d(2026, 10, 26), d(2026, 10, 31), "WMDs 1-6, 8"),
        ),
        HUNT_SRC, HUNT_URL,
        disclaimer = "By permit only (lottery; apply Apr-May). Windows vary by WMD.",
    ),
    SpeciesSeasons(
        "Elk", Side.HUNT,
        emptyList(),
        HUNT_SRC, HUNT_URL,
        noOpenSeason = true,
        disclaimer = "Elk are not a Maine game species — included for scoring only.",
    ),
    SpeciesSeasons(
        "Black bear", Side.HUNT,
        listOf(
            SeasonWindow("General season", d(2026, 8, 31), d(2026, 11, 28), "All WMDs"),
            SeasonWindow("Hunting with bait", d(2026, 8, 31), d(2026, 9, 26), "Bait may be placed Aug 1"),
            SeasonWindow("Bear trapping", d(2026, 9, 1), d(2026, 10, 31)),
            SeasonWindow("Hunting with dogs", d(2026, 9, 14), d(2026, 10, 30)),
        ),
        HUNT_SRC, HUNT_URL,
        disclaimer = "2 bears/year (1 by hunting, 1 by trapping).",
    ),
    SpeciesSeasons(
        "Snowshoe hare", Side.HUNT,
        listOf(
            SeasonWindow("Open season", d(2026, 9, 26), d(2027, 3, 31), "All WMDs"),
        ),
        HUNT_SRC, HUNT_URL,
        disclaimer = "Daily bag 4. Vinalhaven Island closes Feb 27, 2027.",
    ),
    SpeciesSeasons(
        "Upland birds", Side.HUNT,
        listOf(
            SeasonWindow("Ruffed grouse", d(2026, 9, 26), d(2026, 12, 31), "All WMDs"),
            SeasonWindow("Woodcock", d(2026, 9, 26), d(2026, 11, 17), "Migratory — federal framework"),
        ),
        HUNT_SRC, HUNT_URL,
        disclaimer = "Grouse daily bag 4; woodcock daily bag 3.",
    ),
    SpeciesSeasons(
        "Waterfowl", Side.HUNT,
        listOf(
            SeasonWindow("Regular duck (South Zone)", d(2026, 10, 1), d(2026, 10, 10)),
            SeasonWindow("Regular duck (South Zone)", d(2026, 10, 29), d(2026, 12, 26)),
        ),
        WATERFOWL_SRC, WATERFOWL_URL,
        disclaimer = "Dates shown are the South Zone (Sebago). North & Coastal zones differ — " +
            "set yearly by the federal migratory-bird framework. Federal + state duck stamps required.",
    ),
    SpeciesSeasons(
        "Wild turkey", Side.HUNT,
        listOf(
            SeasonWindow("Fall (either-sex)", d(2026, 9, 14), d(2026, 11, 7), "Bag limit varies by WMD"),
            SeasonWindow("Fall youth day", d(2026, 9, 12), d(2026, 9, 12)),
        ),
        HUNT_SRC, HUNT_URL,
        disclaimer = "Fall 2026 dates shown. The spring (bearded-bird) season runs early May to " +
            "early June — 2027 dates are set by IF&W; verify before you go. Bag limits vary by WMD.",
    ),
    SpeciesSeasons(
        "Coyote", Side.HUNT,
        listOf(SeasonWindow("Daytime hunting (no closed season)", d(2026, 1, 1), d(2026, 12, 31))),
        HUNT_SRC, HUNT_URL,
        disclaimer = "No closed season and no bag limit for daytime coyote hunting. Night hunting " +
            "(Dec 16-Aug 31) needs a coyote night-hunting permit. No hunting on Sundays.",
    ),
    // Fishing is intentionally left out: Maine's South-Zone lakes/ponds are open all year
    // under general law, so a "season" adds nothing — real limits are water-by-water special
    // regs we don't reproduce. The Seasons tab is hunting-only.
)

/** Look up the seasons for an app species by name, or null if not tabled (all fish). */
fun seasonsFor(speciesName: String): SpeciesSeasons? =
    MAINE_SEASONS.firstOrNull { it.speciesName == speciesName }
