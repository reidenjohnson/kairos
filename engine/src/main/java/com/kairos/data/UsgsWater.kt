package com.kairos.data

import com.kairos.engine.WaterTempReading
import com.kairos.engine.WaterTempTier
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real measured water temperature from USGS gauges — the top honest tier below a
 * user's own thermometer reading. Uses the USGS Water Services "instantaneous
 * values" API (waterservices.usgs.gov, free, no key), parameter 00010 = water
 * temperature (°C). Many Maine lakes and rivers carry a continuous sensor.
 *
 * We query a bounding box around the location, then keep the NEAREST gauge whose
 * latest reading is both recent and within range, and disclose its age + distance
 * so the number is honest. Returns null when no gauge covers the water (the caller
 * then falls to satellite/estimate). BLOCKING — call off the main thread.
 */
object UsgsWater {

    // A gauge only counts if it's recent enough and close enough to be about THIS
    // water. Beyond these it's more misleading than the labeled estimate.
    private const val MAX_AGE_HOURS = 12L
    private const val MAX_MILES = 30.0

    // Bounding-box half-size (degrees) to search around the point (~±0.4° ≈ 25 mi).
    private const val BOX_DEG = 0.4

    fun fetch(place: Place): WaterTempReading? =
        runCatching { parse(httpGet(url(place)), place, Instant.now()) }.getOrNull()

    private fun url(place: Place): String {
        val w = place.lon - BOX_DEG
        val s = place.lat - BOX_DEG
        val e = place.lon + BOX_DEG
        val n = place.lat + BOX_DEG
        // bBox = west,south,east,north. parameterCd 00010 = water temperature.
        return "https://waterservices.usgs.gov/nwis/iv/?format=json" +
            "&bBox=$w,$s,$e,$n&parameterCd=00010&siteStatus=all"
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("USGS returned HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Visible for testing: pick the nearest recent gauge from a USGS IV JSON
     * response, as of [now]. Pure (no network) so it can be asserted against a
     * captured fixture. Returns null if nothing qualifies.
     */
    internal fun parse(json: String, place: Place, now: Instant): WaterTempReading? {
        val series = JSONObject(json).optJSONObject("value")
            ?.optJSONArray("timeSeries") ?: return null

        data class Hit(val tempC: Double, val ageHrs: Long, val miles: Double, val site: String)
        var best: Hit? = null

        for (i in 0 until series.length()) {
            val ts = series.getJSONObject(i)
            val src = ts.optJSONObject("sourceInfo") ?: continue
            val geo = src.optJSONObject("geoLocation")?.optJSONObject("geogLocation") ?: continue
            val lat = geo.optDouble("latitude", Double.NaN)
            val lon = geo.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val site = src.optString("siteName").ifBlank { "USGS gauge" }

            val latest = latestReading(ts) ?: continue
            val ageHrs = ChronoUnit.HOURS.between(latest.second, now)
            if (ageHrs < 0 || ageHrs > MAX_AGE_HOURS) continue
            val miles = haversineMiles(place.lat, place.lon, lat, lon)
            if (miles > MAX_MILES) continue

            if (best == null || miles < best!!.miles) {
                best = Hit(latest.first, ageHrs, miles, site)
            }
        }

        val hit = best ?: return null
        val tempF = hit.tempC * 9.0 / 5.0 + 32.0
        val detail = "${prettySite(hit.site)}: measured ${agoWord(hit.ageHrs)}, " +
            "${hit.miles.roundToInt()} mi away"
        return WaterTempReading(
            tempF = tempF.roundToInt().toDouble(),
            tier = WaterTempTier.GAUGE,
            label = "USGS gauge",
            detail = detail,
        )
    }

    /** The most recent (value °C, time) in a timeSeries, or null. */
    private fun latestReading(ts: JSONObject): Pair<Double, Instant>? {
        val values = ts.optJSONArray("values") ?: return null
        var best: Pair<Double, Instant>? = null
        for (v in 0 until values.length()) {
            val arr = values.getJSONObject(v).optJSONArray("value") ?: continue
            for (k in 0 until arr.length()) {
                val pt = arr.getJSONObject(k)
                val raw = pt.optString("value")
                val c = raw.toDoubleOrNull() ?: continue
                // USGS marks missing data as -999999; skip those.
                if (c <= -100.0) continue
                val t = runCatching { OffsetDateTime.parse(pt.getString("dateTime")).toInstant() }
                    .getOrNull() ?: continue
                if (best == null || t.isAfter(best!!.second)) best = c to t
            }
        }
        return best
    }

    private fun agoWord(hours: Long): String = when {
        hours <= 0 -> "just now"
        hours == 1L -> "1 h ago"
        else -> "$hours h ago"
    }

    /** Trim "Sebago Lake near Standish, ME" style names to something compact. */
    private fun prettySite(name: String): String {
        val cut = name.substringBefore(",").trim()
        return if (cut.length > 34) cut.take(31).trimEnd() + "…" else cut
    }

    private fun haversineMiles(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double,
    ): Double {
        val r = 3958.8 // Earth radius, miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
