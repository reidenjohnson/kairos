package com.kairos.data

import com.kairos.engine.DayNormal
import com.kairos.engine.StationBiasTable
import com.kairos.engine.localNormal
import com.kairos.engine.modelNormals
import com.kairos.engine.normalsDayIndex
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

/**
 * The spot's 30-year normal high/low for a date (see [com.kairos.engine.localNormal]).
 *
 * The modeled part needs the spot's 1991–2020 daily history from the Open-Meteo archive:
 * one heavy request (~11k days), so it's fetched once per ~1 km spot (0.01°) and cached to disk
 * forever (normals don't change). [cacheDir] is set by the app at startup; while it's
 * null (e.g. a background worker before the app has run) normals are skipped rather
 * than re-pulling 30 years on every fetch. Never throws.
 */
object NormalsRepository {

    @Volatile
    var cacheDir: File? = null

    fun normalFor(lat: Double, lon: Double, date: LocalDate): DayNormal? = runCatching {
        val (lo, hi) = modelFor(lat, lon) ?: return null
        val i = normalsDayIndex(date)
        localNormal(lat, lon, i, lo[i], hi[i], StationBiasTable.all)
    }.getOrNull()

    private fun cellKey(lat: Double, lon: Double) =
        String.format(Locale.US, "%.2f_%.2f", lat, lon)

    private fun modelFor(lat: Double, lon: Double): Pair<DoubleArray, DoubleArray>? {
        val dir = cacheDir ?: return null
        val file = File(File(dir, "normals"), "${cellKey(lat, lon)}.txt")
        if (file.exists()) {
            val lines = file.readLines()
            if (lines.size == 2) {
                val lo = lines[0].split(',').map { it.toDouble() }.toDoubleArray()
                val hi = lines[1].split(',').map { it.toDouble() }.toDoubleArray()
                if (lo.size == 365 && hi.size == 365) return lo to hi
            }
        }
        // Fetch at the exact spot: Open-Meteo downscales to the point's own elevation, and
        // the station biases were measured against the model at each station's exact point.
        // (Rounding to a 0.1° cell centre put Farmington ~1.8°F off NOAA; exact is ~0.)
        val model = fetchModel(lat, lon) ?: return null
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(
            model.first.joinToString(",") { String.format(Locale.US, "%.2f", it) } + "\n" +
                model.second.joinToString(",") { String.format(Locale.US, "%.2f", it) },
        )
        tmp.renameTo(file)
        return model
    }

    private fun fetchModel(lat: Double, lon: Double): Pair<DoubleArray, DoubleArray>? {
        val url = "https://archive-api.open-meteo.com/v1/archive" +
            "?latitude=$lat&longitude=$lon&start_date=1991-01-01&end_date=2020-12-31" +
            "&daily=temperature_2m_min,temperature_2m_max&temperature_unit=fahrenheit&timezone=auto"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val body = try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val daily = JSONObject(body).getJSONObject("daily")
        val t = daily.getJSONArray("time")
        val mn = daily.getJSONArray("temperature_2m_min")
        val mx = daily.getJSONArray("temperature_2m_max")
        val dates = List(t.length()) { LocalDate.parse(t.getString(it)) }
        val lows = List(t.length()) { if (mn.isNull(it)) null else mn.getDouble(it) }
        val highs = List(t.length()) { if (mx.isNull(it)) null else mx.getDouble(it) }
        return modelNormals(dates, lows, highs)
    }
}
