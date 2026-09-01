package com.kairos.data

import com.kairos.engine.Conditions
import com.kairos.engine.SEBAGO_WATER_F
import com.kairos.engine.moonInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** A place to forecast for. Shared by the phone (:app) and the Wear tile (:wear). */
data class Place(val lat: Double, val lon: Double, val label: String)

/** Sebago Lake, southern Maine — the default location used until device location is available. */
object Location {
    const val LAT = 43.85
    const val LON = -70.56
    const val LABEL = "Sebago Lake, ME"
    val SEBAGO = Place(LAT, LON, LABEL)
}

private const val HPA_TO_INHG = 0.02953

/** Everything a forecast screen needs: the engine input plus a display summary. */
data class Forecast(
    val conditions: Conditions,
    val placeLabel: String,  // e.g. "Sebago Lake, ME" or "Naples, Maine"
    val dateLabel: String,   // e.g. "2026-09-01"
    val airF: Double,
    val waterF: Double,
    val windMph: Double,
    val cloudPct: Double,
    val pressureInHg: Double,
    val pressureTrendInHg: Double,
    val tempDropNext24hF: Double,
    val moonName: String,
) {
    val trendWord: String
        get() = when {
            pressureTrendInHg < -0.01 -> "falling"
            pressureTrendInHg > 0.01 -> "rising"
            else -> "steady"
        }
}

/**
 * Pulls live weather from Open-Meteo (free, no API key) and turns it into a
 * [Conditions] for the engine. This is the app-layer port of forecast.py's
 * fetch_weather / pressure_trend_inhg / temp_drop_next_24h; the scoring math
 * itself lives in the pure [com.kairos.engine] package.
 */
object WeatherRepository {

    private fun buildUrl(place: Place): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${place.lat}&longitude=${place.lon}" +
            "&hourly=temperature_2m,surface_pressure" +
            "&current=temperature_2m,surface_pressure,wind_speed_10m,cloud_cover" +
            "&timezone=auto&past_days=1&forecast_days=2" +
            "&temperature_unit=fahrenheit&wind_speed_unit=mph"

    /**
     * Fetch + parse + score-ready for [place] (defaults to Sebago). BLOCKING —
     * does network I/O, so call it off the main thread (the phone wraps it in
     * Dispatchers.IO; the Wear tile runs it on a background executor). Throws on
     * network/parse failure. `timezone=auto` makes Open-Meteo return times local
     * to the given coordinates, so date/month come out right anywhere.
     */
    fun fetch(place: Place = Location.SEBAGO): Forecast {
        val json = httpGet(buildUrl(place))
        return parse(JSONObject(json), place.label)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("Open-Meteo returned HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Visible for testing: turn an Open-Meteo response into a [Forecast]. */
    internal fun parse(root: JSONObject, placeLabel: String): Forecast {
        val current = root.getJSONObject("current")
        val airF = current.getDouble("temperature_2m")
        val pressureInHg = current.getDouble("surface_pressure") * HPA_TO_INHG
        val windMph = current.getDouble("wind_speed_10m")
        val cloudPct = current.getDouble("cloud_cover")
        val currentTime = current.getString("time") // local to Location.TZ

        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val pressures = hourly.getJSONArray("surface_pressure")
        val temps = hourly.getJSONArray("temperature_2m")

        // Index of the current hour within the hourly arrays (tz-consistent).
        val nowHour = currentTime.take(13) + ":00"
        var i = 0
        while (i < times.length() && times.getString(i) != nowHour) i++
        if (i >= times.length()) i = times.length() / 2 // fallback, mirrors reference

        // Pressure trend over ~6h (inHg, negative = falling).
        val i6 = maxOf(0, i - 6)
        val pressureTrendInHg = (pressures.getDouble(i) - pressures.getDouble(i6)) * HPA_TO_INHG

        // Coldest drop coming over the next 24h (°F, positive = front incoming).
        val end = minOf(i + 24, temps.length())
        var coldest = temps.getDouble(i)
        for (k in i until end) coldest = minOf(coldest, temps.getDouble(k))
        val tempDropNext24hF = temps.getDouble(i) - coldest

        val date = LocalDate.parse(currentTime.take(10))
        val waterF = SEBAGO_WATER_F.getValue(date.monthValue).toDouble()
        val moon = moonInfo(date)

        val conditions = Conditions(
            airF = airF,
            waterF = waterF,
            windMph = windMph,
            cloudPct = cloudPct,
            pressureInHg = pressureInHg,
            pressureTrendInHg = pressureTrendInHg,
            tempDropNext24hF = tempDropNext24hF,
            moonIllum = moon.illum,
        )

        return Forecast(
            conditions = conditions,
            placeLabel = placeLabel,
            dateLabel = date.toString(),
            airF = airF,
            waterF = waterF,
            windMph = windMph,
            cloudPct = cloudPct,
            pressureInHg = pressureInHg,
            pressureTrendInHg = pressureTrendInHg,
            tempDropNext24hF = tempDropNext24hF,
            moonName = moon.phaseName,
        )
    }
}
