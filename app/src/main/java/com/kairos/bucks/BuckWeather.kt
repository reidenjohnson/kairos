package com.kairos.bucks

import com.kairos.data.NormalsRepository
import com.kairos.engine.bucks.WxHour
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Hourly weather at each camera, for every hour it was up (the baseline) and the next 7 days.
 *
 * History: Open-Meteo's historical-forecast archive of NOAA HRRR (3 km, hourly; checked
 * 2026-10-07 to cover at least 2021 through today at his camera). Any hour HRRR lacks is filled
 * from the ERA5 reanalysis archive and marked "ERA5". Cached per camera on disk, so the buck
 * pages open offline. Forecast: see [forecastAt].
 * All calls BLOCKING: run on Dispatchers.IO.
 */
object BuckWeather {
    private const val HOURLY = "temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,cloud_cover,precipitation"
    private const val UNITS = "&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch&timezone=GMT&timeformat=unixtime"

    data class Cached(val lat: Double, val lon: Double, val start: LocalDate, val end: LocalDate, val fetchedAt: Long, val hours: List<WxHour>)

    /** Hours covering [from]..[to] (UTC dates) at the camera, from cache when it already covers them. */
    fun history(cam: Camera, from: LocalDate, to: LocalDate): List<WxHour> {
        val lat = cam.lat ?: return emptyList()
        val lon = cam.lon ?: return emptyList()
        val today = LocalDate.now(ZoneOffset.UTC)
        val end = minOf(to, today)
        val file = File(BuckStore.wxDir(), "${cam.id}.json")
        val cached = runCatching { read(file) }.getOrNull()
        val fresh = cached != null && cached.lat == lat && cached.lon == lon &&
            !cached.start.isAfter(from) && !cached.end.isBefore(end) &&
            // The newest day can still be filling in; re-pull it if it was fetched under 6 h after.
            (cached.end.isBefore(today.minusDays(1)) || System.currentTimeMillis() - cached.fetchedAt < 6 * 3600_000L)
        if (fresh) return cached!!.hours
        return try {
            val hrrr = fetch("https://historical-forecast-api.open-meteo.com/v1/forecast", lat, lon, from, end, "&models=gfs_hrrr", "HRRR")
            val gaps = hrrr.count { it.tempF == null || it.windMph == null }
            val merged = if (gaps > 0) {
                val era = runCatching { fetch("https://archive-api.open-meteo.com/v1/archive", lat, lon, from, end, "", "ERA5") }.getOrNull()
                    ?.associateBy { it.t }.orEmpty()
                hrrr.map { h -> if (h.tempF == null || h.windMph == null) era[h.t] ?: h else h }
            } else hrrr
            write(file, Cached(lat, lon, from, end, System.currentTimeMillis(), merged))
            merged
        } catch (e: Exception) {
            android.util.Log.w("Bucks", "weather ${cam.name}", e)
            cached?.hours.orEmpty() // offline: whatever we had
        }
    }

    /** Last 3 days + next 16 at the camera, for the sits ahead and the Hunt forecast. */
    fun forecast(cam: Camera): List<WxHour> {
        val lat = cam.lat ?: return emptyList()
        val lon = cam.lon ?: return emptyList()
        return forecastAt(lat, lon, "${cam.id}_fc16")
    }

    private const val FC_VARS = "$HOURLY,apparent_temperature,relative_humidity_2m,precipitation_probability,snowfall,weather_code"

    /**
     * 16-day hourly forecast at a point, cached 1 h on disk under [key] (offline = last good copy).
     * Open-Meteo best match = NOAA HRRR for the first ~42 h, then GFS (checked 2026-10-13). A second
     * call adds ECMWF IFS wind for the same hours, so each hour can say whether two models agree.
     * BLOCKING.
     */
    fun forecastAt(lat: Double, lon: Double, key: String): List<WxHour> {
        val file = File(BuckStore.wxDir(), "$key.json")
        val cached = runCatching { read(file) }.getOrNull()
        if (cached != null && cached.lat == lat && cached.lon == lon && System.currentTimeMillis() - cached.fetchedAt < 3600_000L) return cached.hours
        return try {
            val base = "https://api.open-meteo.com/v1/forecast?latitude=${f(lat)}&longitude=${f(lon)}&past_days=3&forecast_days=16$UNITS"
            val main = parse(get("$base&hourly=$FC_VARS"), "Forecast")
            val alt = runCatching { parseAlt(get("$base&hourly=wind_speed_10m,wind_direction_10m&models=ecmwf_ifs025")) }
                .onFailure { android.util.Log.w("Bucks", "ECMWF wind", it) }.getOrNull().orEmpty()
            val hours = main.map { h -> alt[h.t]?.let { (d, m) -> h.copy(altDir = d, altMph = m) } ?: h }
            write(file, Cached(lat, lon, LocalDate.now(), LocalDate.now(), System.currentTimeMillis(), hours))
            hours
        } catch (e: Exception) {
            android.util.Log.w("Bucks", "forecast", e)
            cached?.hours.orEmpty()
        }
    }

    /** ECMWF wind by hour: t -> (dir, mph). */
    private fun parseAlt(body: String): Map<Long, Pair<Double, Double>> {
        val h = JSONObject(body).getJSONObject("hourly")
        val t = h.getJSONArray("time"); val d = h.getJSONArray("wind_direction_10m"); val m = h.getJSONArray("wind_speed_10m")
        return (0 until t.length()).filter { !d.isNull(it) && !m.isNull(it) }.associate { t.getLong(it) to (d.getDouble(it) to m.getDouble(it)) }
    }

    /** One 15-minute step of NOAA HRRR at a camera. [t] unix seconds. */
    data class Quarter(val t: Long, val mph: Double?, val dir: Double?, val gust: Double?, val tempF: Double?)

    private const val Q_VARS = "wind_speed_10m,wind_direction_10m,wind_gusts_10m,temperature_2m"

    /**
     * Wind and temperature every 15 minutes at the camera (HRRR's own 15-minute output, checked
     * 2026-10-07: each step is its own value, e.g. 220° -> 252° within an hour). Cached per day;
     * days older than yesterday never re-pull, recent days re-pull after 3 h. Offline = cache.
     * BLOCKING.
     */
    fun quarters(cam: Camera, from: LocalDate, to: LocalDate): List<Quarter> {
        val lat = cam.lat ?: return emptyList()
        val lon = cam.lon ?: return emptyList()
        val today = LocalDate.now(ZoneOffset.UTC)
        val end = minOf(to, today)
        if (end.isBefore(from)) return emptyList()
        val file = File(BuckStore.wxDir(), "${cam.id}_q15.json")
        val cache = runCatching { JSONObject(file.readText()) }.getOrNull()
            ?.takeIf { it.optDouble("lat") == lat && it.optDouble("lon") == lon }
        val days = cache?.optJSONObject("days") ?: JSONObject()
        val now = System.currentTimeMillis()
        val need = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.filter { d ->
            val o = days.optJSONObject(d.toString())
            o == null || (!d.isBefore(today.minusDays(1)) && now - o.optLong("f") > 3 * 3600_000L)
        }.toList()
        if (need.isNotEmpty()) runCatching {
            val a = need.first(); val b = need.last()
            val base = "latitude=${f(lat)}&longitude=${f(lon)}&minutely_15=$Q_VARS&models=gfs_hrrr&wind_speed_unit=mph&temperature_unit=fahrenheit&timezone=GMT&timeformat=unixtime"
            val hist = parseQ(get("https://historical-forecast-api.open-meteo.com/v1/forecast?$base&start_date=$a&end_date=$b"))
            // The archive can lag a day or two behind; the live API fills the newest hours.
            val recent = if (!b.isBefore(today.minusDays(2))) runCatching { parseQ(get("https://api.open-meteo.com/v1/forecast?$base&past_days=3&forecast_days=1")) }.getOrNull().orEmpty().associateBy { it.t } else emptyMap()
            val merged = hist.map { q -> if (q.mph == null) recent[q.t] ?: q else q }
            val byDay = merged.groupBy { utcDate(it.t) }
            for (d in need) {
                val rows = byDay[d].orEmpty()
                if (rows.isEmpty() || rows.all { it.mph == null }) continue
                days.put(d.toString(), JSONObject().put("f", now).put("r", JSONArray(rows.map { q ->
                    JSONArray().put(q.t).put(q.mph ?: JSONObject.NULL).put(q.dir ?: JSONObject.NULL).put(q.gust ?: JSONObject.NULL).put(q.tempF ?: JSONObject.NULL)
                })))
            }
            val o = JSONObject().put("lat", lat).put("lon", lon).put("days", days)
            val tmp = File(file.path + ".tmp"); tmp.writeText(o.toString()); if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }.onFailure { android.util.Log.w("Bucks", "15-min weather ${cam.name}", it) }
        fun JSONArray.d(i: Int) = if (isNull(i)) null else getDouble(i)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.flatMap { d ->
            val r = days.optJSONObject(d.toString())?.optJSONArray("r") ?: return@flatMap emptySequence()
            (0 until r.length()).asSequence().map { i -> r.getJSONArray(i).let { x -> Quarter(x.getLong(0), x.d(1), x.d(2), x.d(3), x.d(4)) } }
        }.toList()
    }

    private fun parseQ(body: String): List<Quarter> {
        val o = JSONObject(body)
        if (o.optBoolean("error")) error(o.optString("reason"))
        val m = o.getJSONObject("minutely_15")
        val t = m.getJSONArray("time")
        fun col(n: String): (Int) -> Double? {
            val a = m.optJSONArray(n)
            return { i -> if (a == null || i >= a.length() || a.isNull(i)) null else a.getDouble(i) }
        }
        val ws = col("wind_speed_10m"); val wd = col("wind_direction_10m"); val g = col("wind_gusts_10m"); val tp = col("temperature_2m")
        return (0 until t.length()).map { i -> Quarter(t.getLong(i), ws(i), wd(i), g(i), tp(i)) }
    }

    /** The camera's 30-yr normal (low, high) for a date; null if not cached and offline. */
    fun normal(cam: Camera, date: LocalDate): Pair<Double, Double>? {
        val lat = cam.lat ?: return null
        val lon = cam.lon ?: return null
        return NormalsRepository.normalFor(lat, lon, date)?.let { it.lowF to it.highF }
    }

    private fun fetch(base: String, lat: Double, lon: Double, from: LocalDate, to: LocalDate, extra: String, source: String): List<WxHour> {
        val url = "$base?latitude=${f(lat)}&longitude=${f(lon)}&start_date=$from&end_date=$to&hourly=$HOURLY$extra$UNITS"
        return parse(get(url), source)
    }

    private fun parse(body: String, source: String): List<WxHour> {
        val o = JSONObject(body)
        if (o.optBoolean("error")) error(o.optString("reason"))
        val h = o.getJSONObject("hourly")
        val t = h.getJSONArray("time")
        fun col(n: String): (Int) -> Double? {
            val a = h.optJSONArray(n)
            return { i -> if (a == null || i >= a.length() || a.isNull(i)) null else a.getDouble(i) }
        }
        val temp = col("temperature_2m"); val ws = col("wind_speed_10m"); val wd = col("wind_direction_10m")
        val g = col("wind_gusts_10m"); val p = col("pressure_msl"); val c = col("cloud_cover"); val pr = col("precipitation")
        val fl = col("apparent_temperature"); val rh = col("relative_humidity_2m"); val pp = col("precipitation_probability")
        val sn = col("snowfall"); val wc = col("weather_code")
        return (0 until t.length()).map { i ->
            WxHour(t.getLong(i), temp(i), ws(i), wd(i), g(i), p(i), c(i), pr(i), source, fl(i), rh(i), pp(i), sn(i), wc(i)?.toInt())
        }
    }

    private fun get(url: String): String {
        var last: Exception? = null
        repeat(2) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 45_000 }
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (!body.trimStart().startsWith("{")) error("not JSON")
                return body
            } catch (e: Exception) {
                last = e
            } finally {
                conn?.disconnect()
            }
        }
        throw last ?: IllegalStateException("fetch failed")
    }

    private fun f(x: Double) = String.format(Locale.US, "%.4f", x)

    private fun write(file: File, c: Cached) {
        val arr = JSONArray()
        c.hours.forEach { h ->
            arr.put(JSONArray().put(h.t).put(h.tempF ?: JSONObject.NULL).put(h.windMph ?: JSONObject.NULL).put(h.windDir ?: JSONObject.NULL)
                .put(h.gustMph ?: JSONObject.NULL).put(h.pressureHpa ?: JSONObject.NULL).put(h.cloudPct ?: JSONObject.NULL)
                .put(h.precipIn ?: JSONObject.NULL).put(h.source)
                .put(h.feelsF ?: JSONObject.NULL).put(h.humidityPct ?: JSONObject.NULL).put(h.precipProb ?: JSONObject.NULL)
                .put(h.snowIn ?: JSONObject.NULL).put(h.code ?: JSONObject.NULL).put(h.altDir ?: JSONObject.NULL).put(h.altMph ?: JSONObject.NULL))
        }
        val o = JSONObject().put("lat", c.lat).put("lon", c.lon).put("start", c.start.toString()).put("end", c.end.toString())
            .put("fetchedAt", c.fetchedAt).put("hours", arr)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun read(file: File): Cached? {
        if (!file.exists()) return null
        val o = JSONObject(file.readText())
        val a = o.getJSONArray("hours")
        fun JSONArray.d(i: Int) = if (i >= length() || isNull(i)) null else getDouble(i)
        return Cached(
            o.getDouble("lat"), o.getDouble("lon"), LocalDate.parse(o.getString("start")), LocalDate.parse(o.getString("end")),
            o.getLong("fetchedAt"),
            (0 until a.length()).map { i ->
                val r = a.getJSONArray(i)
                WxHour(
                    r.getLong(0), r.d(1), r.d(2), r.d(3), r.d(4), r.d(5), r.d(6), r.d(7), r.optString(8, "HRRR"),
                    r.d(9), r.d(10), r.d(11), r.d(12), r.d(13)?.toInt(), r.d(14), r.d(15),
                )
            },
        )
    }

    fun utcDate(epochSec: Long): LocalDate = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).toLocalDate()
}
