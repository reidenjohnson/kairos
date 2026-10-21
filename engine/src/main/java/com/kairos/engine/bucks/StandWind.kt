package com.kairos.engine.bucks

import kotlin.math.roundToInt

/**
 * "Your wind" at a stand: the 16-point compass sectors he picked as the winds he hunts it on
 * (the ones that carry his scent away from where deer come from; he knows the ground, so he
 * picks). A sector is 22.5° wide, centered on its point: N = 348.75°..11.25°.
 *
 * Sits on his wind rank up; a real wind off it (3 mph and up) ranks the sit last. Calm and
 * split-model hours are neither: nobody knows where the scent goes.
 */
object StandWind {
    const val SECTORS = 16
    const val SECTOR_DEG = 360.0 / SECTORS
    /** Points a sit earns on his wind: the same as a buck's own wind ([HuntWindows.HIS_WIND_PTS]). */
    const val PTS = 3

    val NAMES = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    fun sectorOf(dir: Double): Int = (((dir / SECTOR_DEG).roundToInt() % SECTORS) + SECTORS) % SECTORS

    /** true = his wind, false = a real wind off it, null = nothing set, calm, or no direction. */
    fun fits(sectors: Set<Int>, dir: Double?, mph: Double?): Boolean? {
        if (sectors.isEmpty() || dir == null || mph == null || mph < HisWinds.CALM_MPH) return null
        return sectorOf(dir) in sectors
    }

    /** "N–NNW", or "NW, S–SSW" for separate runs. Runs read clockwise. */
    fun label(sectors: Set<Int>): String {
        if (sectors.isEmpty()) return ""
        if (sectors.size == SECTORS) return "Any wind"
        // Start each run at a sector whose counter-clockwise neighbor is off.
        val starts = sectors.filter { (it + SECTORS - 1) % SECTORS !in sectors }.sorted()
        return starts.joinToString(", ") { s ->
            var e = s
            while ((e + 1) % SECTORS in sectors) e = (e + 1) % SECTORS
            if (e == s) NAMES[s] else "${NAMES[s]}–${NAMES[e]}"
        }
    }

    /**
     * What his photos say: the run of up to 3 neighboring sectors (67.5°) that caught the most deer
     * visits with a real wind, edges with no visits trimmed. Daylight visits only when there are at
     * least [MIN_DAYLIGHT] of them (those are the ones he can hunt), else every visit. A suggestion
     * from where deer came, not a scent check: he still decides which way his scent should go.
     */
    data class Suggestion(val sectors: Set<Int>, val hits: Int, val of: Int, val daylightOnly: Boolean, val calm: Int)

    const val MIN_DAYLIGHT = 3

    fun suggest(visits: List<DeerVisit>): Suggestion? {
        val day = visits.filter { it.legal }
        val daylightOnly = day.count { it.dir != null } >= MIN_DAYLIGHT
        val use = if (daylightOnly) day else visits
        val windy = use.mapNotNull { it.dir }
        if (windy.isEmpty()) return null
        val count = IntArray(SECTORS).also { c -> windy.forEach { c[sectorOf(it)]++ } }
        fun window(s: Int) = (0 until 3).map { (s + it) % SECTORS }
        // Most visits; on a tie, the window whose middle sector has the most (the tighter peak).
        val best = (0 until SECTORS).maxWithOrNull(compareBy<Int> { s -> window(s).sumOf { count[it] } }.thenBy { s -> count[(s + 1) % SECTORS] }.thenBy { -it })!!
        val run = window(best).toMutableList()
        while (run.isNotEmpty() && count[run.first()] == 0) run.removeAt(0)
        while (run.isNotEmpty() && count[run.last()] == 0) run.removeAt(run.size - 1)
        if (run.isEmpty()) return null
        return Suggestion(run.toSet(), run.sumOf { count[it] }, windy.size, daylightOnly, use.count { it.dir == null })
    }
}
