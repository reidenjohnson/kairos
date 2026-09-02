package com.kairos.data

import android.content.Context
import com.kairos.engine.SpeciesScore
import org.json.JSONObject
import java.time.LocalDate

/**
 * The app's own record of how conditions actually scored, day by day — the
 * "actual" line on the Trends chart (see HANDOFF §9 item 3). No manual input:
 * every successful live forecast records that day's best score per species. If
 * the app is checked several times a day we keep the day's peak, so the record
 * is "the best it got today," matching how the outlook reports each day's best.
 *
 * Stored as JSON in SharedPreferences: { "2026-09-02": { "Walleye": 81, ... } }.
 * Capped to the most recent [MAX_DAYS] days so it never grows without bound.
 */
object ScoreHistory {
    private const val PREFS = "kairos_history"
    private const val KEY = "daily_best"
    private const val MAX_DAYS = 120

    /** One species' recorded best score on one day. */
    data class Point(val date: LocalDate, val percent: Int)

    /** Merge today's [scores] in, keeping the max already seen for [date]. */
    fun record(context: Context, date: LocalDate, scores: List<SpeciesScore>) {
        val root = readRoot(context)
        val day = root.optJSONObject(date.toString()) ?: JSONObject()
        for (s in scores) {
            val prev = day.optInt(s.species.name, Int.MIN_VALUE)
            if (s.percent > prev) day.put(s.species.name, s.percent)
        }
        root.put(date.toString(), day)
        pruneOldest(root)
        prefs(context).edit().putString(KEY, root.toString()).apply()
    }

    /** Recorded best scores for [speciesName], oldest-first. */
    fun history(context: Context, speciesName: String): List<Point> {
        val root = readRoot(context)
        val out = ArrayList<Point>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val dateStr = keys.next()
            val day = root.optJSONObject(dateStr) ?: continue
            if (day.has(speciesName)) {
                runCatching { LocalDate.parse(dateStr) }.getOrNull()?.let {
                    out += Point(it, day.getInt(speciesName))
                }
            }
        }
        return out.sortedBy { it.date }
    }

    private fun readRoot(context: Context): JSONObject {
        val s = prefs(context).getString(KEY, null) ?: return JSONObject()
        return runCatching { JSONObject(s) }.getOrDefault(JSONObject())
    }

    private fun pruneOldest(root: JSONObject) {
        val dates = ArrayList<String>()
        val keys = root.keys()
        while (keys.hasNext()) dates += keys.next()
        if (dates.size <= MAX_DAYS) return
        dates.sort() // ISO dates sort chronologically
        for (i in 0 until dates.size - MAX_DAYS) root.remove(dates[i])
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
