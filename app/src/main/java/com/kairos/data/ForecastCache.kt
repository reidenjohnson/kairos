package com.kairos.data

import android.content.Context
import com.kairos.engine.Conditions
import org.json.JSONObject

/**
 * Persists the last successful [Forecast] so the app still shows something
 * useful with no signal — the whole point of using it in the woods. Stored as
 * JSON in SharedPreferences; small and synchronous, which is fine for one row.
 */
object ForecastCache {
    private const val PREFS = "kairos_cache"
    private const val KEY = "last_forecast"

    /** A cached forecast plus when it was saved (epoch millis). */
    data class Cached(val forecast: Forecast, val savedAtMillis: Long)

    fun save(context: Context, f: Forecast) {
        val c = f.conditions
        val o = JSONObject()
            .put("savedAt", System.currentTimeMillis())
            .put("placeLabel", f.placeLabel)
            .put("dateLabel", f.dateLabel)
            .put("airF", f.airF)
            .put("waterF", f.waterF)
            .put("windMph", f.windMph)
            .put("cloudPct", f.cloudPct)
            .put("pressureInHg", f.pressureInHg)
            .put("pressureTrendInHg", f.pressureTrendInHg)
            .put("tempDropNext24hF", f.tempDropNext24hF)
            .put("moonName", f.moonName)
            .put("moonIllum", c.moonIllum)
        prefs(context).edit().putString(KEY, o.toString()).apply()
    }

    fun load(context: Context): Cached? {
        val s = prefs(context).getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(s)
            val conditions = Conditions(
                airF = o.getDouble("airF"),
                waterF = o.getDouble("waterF"),
                windMph = o.getDouble("windMph"),
                cloudPct = o.getDouble("cloudPct"),
                pressureInHg = o.getDouble("pressureInHg"),
                pressureTrendInHg = o.getDouble("pressureTrendInHg"),
                tempDropNext24hF = o.getDouble("tempDropNext24hF"),
                moonIllum = o.getDouble("moonIllum"),
            )
            val forecast = Forecast(
                conditions = conditions,
                placeLabel = o.getString("placeLabel"),
                dateLabel = o.getString("dateLabel"),
                airF = o.getDouble("airF"),
                waterF = o.getDouble("waterF"),
                windMph = o.getDouble("windMph"),
                cloudPct = o.getDouble("cloudPct"),
                pressureInHg = o.getDouble("pressureInHg"),
                pressureTrendInHg = o.getDouble("pressureTrendInHg"),
                tempDropNext24hF = o.getDouble("tempDropNext24hF"),
                moonName = o.getString("moonName"),
            )
            Cached(forecast, o.getLong("savedAt"))
        } catch (e: Exception) {
            null
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
