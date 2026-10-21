package com.kairos.engine.bucks

import kotlin.math.roundToInt

/**
 * A sit as a short yes/no checklist, and the share that line up as a percent. Each check is one
 * thing his photos, his own pick or the mentors back; all weigh the same, so the percent reads
 * plainly ("5 of 6 line up"). It is how well the sit lines up, NOT the odds of seeing a deer:
 * nothing measured gives those odds.
 *
 * [met] null = doesn't apply (e.g. his wind isn't set): left out of the percent.
 */
data class Check(val label: String, val met: Boolean?, val detail: String)

object SitChecks {
    /** "Good" = at least this share lines up (3 of 4, 4 of 5). His pick (2026-10-14): easy to see from 75%. */
    const val GOOD_PCT = 75
    /** "Fair" = at least this share (2 of 4); shown in Amber. */
    const val FAIR_PCT = 50

    fun percent(checks: List<Check>): Int {
        val used = checks.filter { it.met != null }
        if (used.isEmpty()) return 0
        return (100.0 * used.count { it.met == true } / used.size).roundToInt()
    }

    fun good(checks: List<Check>) = percent(checks) >= GOOD_PCT

    /**
     * Do deer favor this wind at a camera? More of the camera's visits came on it than the share
     * of the time it blew while the camera was watching, and at least 2 visits (one is chance).
     * A wind that blows half the time will catch half the visits by itself; that isn't a pattern.
     */
    fun favors(visitsOn: Int, visits: Int, hoursOn: Int, hours: Int): Boolean {
        if (visitsOn < 2 || visits == 0 || hours == 0) return false
        return visitsOn.toDouble() / visits > hoursOn.toDouble() / hours
    }
}
