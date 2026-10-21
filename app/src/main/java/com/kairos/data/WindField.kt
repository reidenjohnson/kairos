package com.kairos.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The regional wind field for the Wind screen: NOAA's HRRR model (3 km, hourly) via Open-Meteo's
 * free forecast API, sampled on a fixed lattice of 0.03° nodes (~3 km, the model's own spacing —
 * finer would just repeat cells). Each node's 7-days-back + 48-h-ahead series is cached on disk,
 * so panning back over an area costs nothing and the screen still works offline in the woods.
 *
 * Free-tier budget (non-commercial): < 600 calls/min, < 5000/h, < 10000/day, and a request
 * with N coordinates counts as N calls. So: one request per viewport, capped at [WindNodes.MAX_NODES]
 * nodes (coarser spacing when zoomed out), nodes refreshed at most hourly, and a running
 * per-minute/hour/day tally ([WindBudget]) that refuses a fetch rather than get the app blocked.
 */

/** The slider's hour axis: 7 days back to 48 h ahead, on whole UTC hours. Index [NOW_INDEX] = this hour. */
object WindAxis {
    const val HOURS_BACK = 168
    const val HOURS_AHEAD = 48
    const val COUNT = HOURS_BACK + HOURS_AHEAD + 1
    const val NOW_INDEX = HOURS_BACK

    /** Unix seconds of index 0, for an axis built around [nowMillis]. */
    fun startSec(nowMillis: Long = System.currentTimeMillis()): Long =
        (nowMillis / 1000 / 3600) * 3600 - HOURS_BACK * 3600L
}

/** One point's hourly series (unix-second UTC [times]; NaN = no data that hour). */
class WindSeries(
    val lat: Double,
    val lon: Double,
    val fetchedAtMillis: Long,
    val utcOffsetSec: Int,
    val times: LongArray,
    val speed: FloatArray,    // mph
    val dir: FloatArray,      // FROM, degrees
    val gust: FloatArray,     // mph
    val temp: FloatArray,     // °F
    val cloud: FloatArray,    // %
    val precip: FloatArray,   // inches in the hour
    val pressure: FloatArray, // hPa, sea level
) {
    /** Index of the hour starting at [tSec], or -1 if the series doesn't have it. */
    fun indexOf(tSec: Long): Int {
        if (times.isEmpty()) return -1
        // Open-Meteo hours are contiguous, so this is almost always a direct hit.
        val guess = ((tSec - times[0]) / 3600).toInt()
        if (guess in times.indices && times[guess] == tSec) return guess
        val i = times.binarySearch(tSec)
        return if (i >= 0) i else -1
    }
}

/** Builds Open-Meteo requests and parses their replies. Pure (no Android), so it's unit-tested. */
object HrrrApi {
    private const val HOURLY = "wind_speed_10m,wind_direction_10m,wind_gusts_10m,temperature_2m," +
        "cloud_cover,precipitation,pressure_msl"

    /**
     * One request for every point. forecast_days=3 (not 2) so the +48 h end of the slider is
     * inside the window; HRRR itself only runs ~48 h out, so the last hours come back null and
     * show as "no data" rather than being filled from another model.
     */
    fun url(points: List<Pair<Double, Double>>): String {
        val lats = points.joinToString(",") { String.format(Locale.US, "%.4f", it.first) }
        val lons = points.joinToString(",") { String.format(Locale.US, "%.4f", it.second) }
        return "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lats&longitude=$lons" +
            "&models=gfs_hrrr&hourly=$HOURLY" +
            "&past_days=7&forecast_days=3&timezone=auto&timeformat=unixtime" +
            "&wind_speed_unit=mph&temperature_unit=fahrenheit&precipitation_unit=inch"
    }

    /**
     * Parse a reply into one series per requested point, in request order. A single point comes
     * back as an object, several as an array. [points] supplies the REQUESTED coordinates (the
     * reply's are snapped to the model cell), so cache keys stay on our lattice.
     */
    fun parse(body: String, points: List<Pair<Double, Double>>, fetchedAtMillis: Long): List<WindSeries> {
        val trimmed = body.trimStart()
        val objs: List<JSONObject> = if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } else {
            listOf(JSONObject(trimmed))
        }
        if (objs.size != points.size) error("Open-Meteo returned ${objs.size} points for ${points.size}")
        return objs.mapIndexed { k, o ->
            val h = o.getJSONObject("hourly")
            val t = h.getJSONArray("time")
            val times = LongArray(t.length()) { t.getLong(it) }
            fun col(name: String): FloatArray {
                val a = h.optJSONArray(name) ?: return FloatArray(times.size) { Float.NaN }
                return FloatArray(times.size) { i -> if (i >= a.length() || a.isNull(i)) Float.NaN else a.getDouble(i).toFloat() }
            }
            WindSeries(
                lat = points[k].first,
                lon = points[k].second,
                fetchedAtMillis = fetchedAtMillis,
                utcOffsetSec = o.optInt("utc_offset_seconds", 0),
                times = times,
                speed = col("wind_speed_10m"),
                dir = col("wind_direction_10m"),
                gust = col("wind_gusts_10m"),
                temp = col("temperature_2m"),
                cloud = col("cloud_cover"),
                precip = col("precipitation"),
                pressure = col("pressure_msl"),
            )
        }
    }

    /** Fetch + parse. BLOCKING (network) — call on Dispatchers.IO. Throws on failure. */
    fun fetch(points: List<Pair<Double, Double>>): List<WindSeries> {
        val now = System.currentTimeMillis()
        return parse(getJson(url(points)), points, now)
    }

    /** GET with a short retry, rejecting Open-Meteo's "200 but not JSON" / {"error":true} replies
     *  (same transient failures WeatherRepository guards against). */
    private fun getJson(url: String, attempts: Int = 2): String {
        var last: Exception? = null
        for (i in 0 until attempts) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 20_000
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty().trimStart()
                if (code !in 200..299) error("Open-Meteo returned HTTP $code")
                if (!body.startsWith("{") && !body.startsWith("[")) error("Weather service busy")
                if (Regex("^\\{\\s*\"error\"\\s*:\\s*true").containsMatchIn(body)) {
                    val reason = Regex("\"reason\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
                    error(reason ?: "Weather service unavailable")
                }
                return body
            } catch (e: Exception) {
                last = e
                if (i < attempts - 1) Thread.sleep(800)
            } finally {
                conn?.disconnect()
            }
        }
        throw last ?: java.io.IOException("Wind request failed")
    }
}

/**
 * The node lattice. A node is (i, j) = (lat, lon) / 0.03, so every zoom level shares the same
 * cache entries; zoomed out, only every m-th node is used (m a power of two) to stay under the cap.
 */
object WindNodes {
    const val STEP = 0.03
    const val MAX_NODES = 150

    /** The nodes covering a box: lattice indices [i0..i1] x [j0..j1] every [m]. */
    data class Plan(val m: Int, val i0: Int, val i1: Int, val j0: Int, val j1: Int) {
        val ni get() = (i1 - i0) / m + 1
        val nj get() = (j1 - j0) / m + 1
        val count get() = ni * nj
        fun keys(): List<Pair<Int, Int>> =
            (i0..i1 step m).flatMap { i -> (j0..j1 step m).map { j -> i to j } }
    }

    fun key(i: Int, j: Int) = "${i}_$j"
    fun lat(i: Int) = i * STEP
    fun lon(j: Int) = j * STEP

    /** Smallest spacing (0.03° x 2^k) whose node box covering [south..north] x [west..east] fits the cap. */
    fun plan(south: Double, west: Double, north: Double, east: Double): Plan {
        var m = 1
        while (true) {
            val i0 = (floor(south / STEP / m) * m).toInt()
            val i1 = (ceil(north / STEP / m) * m).toInt()
            val j0 = (floor(west / STEP / m) * m).toInt()
            val j1 = (ceil(east / STEP / m) * m).toInt()
            val p = Plan(m, i0, i1, j0, j1)
            if (p.count <= MAX_NODES || m >= 512) return p
            m *= 2
        }
    }
}

/**
 * The renderable field for one [WindNodes.Plan]: u/v (mph, toward) per node per axis hour,
 * NaN where we have nothing. [sample] is called per particle per frame, so it allocates nothing;
 * [setHour] is called once per frame first.
 */
class WindGrid(
    val plan: WindNodes.Plan,
    val axisStartSec: Long,
    private val u: FloatArray,
    private val v: FloatArray,
    /** Oldest fetch time among the nodes in it (for the "data from" chip); 0 if none. */
    val oldestFetchMillis: Long,
    val nodesWithData: Int,
) {
    private val ni = plan.ni
    private val nj = plan.nj
    private val perHour = ni * nj
    private var h0 = 0
    private var h1 = 0
    private var hf = 0f

    /** Pick the (fractional) axis hour to sample — between hours it blends linearly. */
    fun setHour(hour: Float) {
        val h = hour.coerceIn(0f, (WindAxis.COUNT - 1).toFloat())
        h0 = floor(h).toInt()
        h1 = (h0 + 1).coerceAtMost(WindAxis.COUNT - 1)
        hf = h - h0
    }

    /** Does the current hour have any data at all? (For the "no data this hour" chip.) */
    fun hourHasData(hour: Int): Boolean {
        if (hour !in 0 until WindAxis.COUNT) return false
        val base = hour * perHour
        for (k in 0 until perHour) if (!u[base + k].isNaN()) return true
        return false
    }

    /** Bilinear in space, linear in time. Writes (u, v) to [out]; false when there's no data. */
    fun sample(lat: Double, lon: Double, out: FloatArray): Boolean {
        val gi = (lat / WindNodes.STEP - plan.i0) / plan.m
        val gj = (lon / WindNodes.STEP - plan.j0) / plan.m
        if (gi < 0 || gj < 0 || gi > ni - 1 || gj > nj - 1) return false
        val i = floor(gi).toInt().coerceAtMost(ni - 2).coerceAtLeast(0)
        val j = floor(gj).toInt().coerceAtMost(nj - 2).coerceAtLeast(0)
        val fi = (gi - i).toFloat()
        val fj = (gj - j).toFloat()
        var su = 0f; var sv = 0f; var sw = 0f
        // Corners that are missing (not fetched yet / outside HRRR) are dropped and the rest
        // renormalized, so the field edge fades instead of snapping to zero.
        for (c in 0 until 4) {
            val ii = i + (c shr 1)
            val jj = j + (c and 1)
            if (ii >= ni || jj >= nj) continue
            val w = (if (c shr 1 == 1) fi else 1 - fi) * (if (c and 1 == 1) fj else 1 - fj)
            if (w <= 0f) continue
            val node = ii * nj + jj
            val a0 = h0 * perHour + node
            val a1 = h1 * perHour + node
            val u0 = u[a0]; val u1 = u[a1]
            val cu: Float; val cv: Float
            if (!u0.isNaN() && !u1.isNaN()) {
                cu = u0 + (u1 - u0) * hf; cv = v[a0] + (v[a1] - v[a0]) * hf
            } else if (!u0.isNaN()) {
                cu = u0; cv = v[a0]
            } else if (!u1.isNaN()) {
                cu = u1; cv = v[a1]
            } else continue
            su += cu * w; sv += cv * w; sw += w
        }
        if (sw < 0.25f) return false
        out[0] = su / sw
        out[1] = sv / sw
        return true
    }

    companion object {
        /** Assemble a grid for [plan] from whatever series we hold (keyed by [WindNodes.key]). */
        fun build(plan: WindNodes.Plan, axisStartSec: Long, series: (String) -> WindSeries?): WindGrid {
            val ni = plan.ni
            val nj = plan.nj
            val per = ni * nj
            val u = FloatArray(WindAxis.COUNT * per) { Float.NaN }
            val v = FloatArray(WindAxis.COUNT * per) { Float.NaN }
            var oldest = Long.MAX_VALUE
            var withData = 0
            for (a in 0 until ni) for (b in 0 until nj) {
                val s = series(WindNodes.key(plan.i0 + a * plan.m, plan.j0 + b * plan.m)) ?: continue
                withData++
                oldest = minOf(oldest, s.fetchedAtMillis)
                val node = a * nj + b
                for (h in 0 until WindAxis.COUNT) {
                    val k = s.indexOf(axisStartSec + h * 3600L)
                    if (k < 0) continue
                    val sp = s.speed[k]
                    val d = s.dir[k]
                    if (sp.isNaN() || d.isNaN()) continue
                    u[h * per + node] = WindMath.u(sp.toDouble(), d.toDouble()).toFloat()
                    v[h * per + node] = WindMath.v(sp.toDouble(), d.toDouble()).toFloat()
                }
            }
            return WindGrid(plan, axisStartSec, u, v, if (withData == 0) 0L else oldest, withData)
        }
    }
}

/**
 * Per-node disk cache (`filesDir/windgrid/<i>_<j>.bin`), ForecastCache's job for the wind field:
 * the last good series for every node we've fetched, so the field shows offline. Compact binary
 * (a node is ~7 KB) rather than JSON — a viewport loads 150 of them.
 */
object WindNodeCache {
    private const val VERSION = 1
    private const val KEEP_MS = 10L * 24 * 3600 * 1000 // older than the slider window = useless

    private fun dir(context: Context) = File(context.filesDir, "windgrid").apply { mkdirs() }

    fun save(context: Context, key: String, s: WindSeries) {
        runCatching {
            val tmp = File(dir(context), "$key.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { o -> write(o, s) }
            tmp.renameTo(File(dir(context), "$key.bin"))
        }
    }

    fun load(context: Context, key: String): WindSeries? {
        val f = File(dir(context), "$key.bin")
        if (!f.exists()) return null
        return runCatching { DataInputStream(f.inputStream().buffered()).use { read(it) } }.getOrNull()
    }

    /** Drop node files too old to cover any part of the slider. Cheap; run once per screen open. */
    fun prune(context: Context) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        dir(context).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    internal fun write(o: DataOutputStream, s: WindSeries) {
        o.writeInt(VERSION)
        o.writeDouble(s.lat); o.writeDouble(s.lon)
        o.writeLong(s.fetchedAtMillis); o.writeInt(s.utcOffsetSec)
        o.writeInt(s.times.size)
        s.times.forEach { o.writeLong(it) }
        for (col in listOf(s.speed, s.dir, s.gust, s.temp, s.cloud, s.precip, s.pressure)) {
            col.forEach { o.writeFloat(it) }
        }
    }

    internal fun read(i: DataInputStream): WindSeries? {
        if (i.readInt() != VERSION) return null
        val lat = i.readDouble(); val lon = i.readDouble()
        val fetched = i.readLong(); val off = i.readInt()
        val n = i.readInt()
        val times = LongArray(n) { i.readLong() }
        fun col() = FloatArray(n) { i.readFloat() }
        return WindSeries(lat, lon, fetched, off, times, col(), col(), col(), col(), col(), col(), col())
    }
}

/**
 * Running tally of Open-Meteo "calls" (one per location) so the Wind screen can never trip the
 * free tier's limits, even with a lot of panning. Minute/hour windows live in memory; the daily
 * count is persisted so app restarts don't reset it.
 */
object WindBudget {
    private const val PER_MIN = 500
    private const val PER_HOUR = 4_000
    private const val PER_DAY = 9_000
    private val recent = ArrayDeque<Pair<Long, Int>>() // (millis, locations)

    @Synchronized
    fun tryTake(context: Context, n: Int): Boolean {
        val now = System.currentTimeMillis()
        while (recent.isNotEmpty() && now - recent.first().first > 3_600_000) recent.removeFirst()
        val lastMin = recent.filter { now - it.first < 60_000 }.sumOf { it.second }
        val lastHour = recent.sumOf { it.second }
        val p = context.getSharedPreferences("wind_budget", Context.MODE_PRIVATE)
        val day = now / 86_400_000
        val today = if (p.getLong("day", -1) == day) p.getInt("count", 0) else 0
        if (lastMin + n > PER_MIN || lastHour + n > PER_HOUR || today + n > PER_DAY) return false
        recent.addLast(now to n)
        p.edit().putLong("day", day).putInt("count", today + n).apply()
        return true
    }
}

/**
 * Holds the node series in memory over the disk cache and decides what to fetch. All methods
 * BLOCK (disk + network) — call on Dispatchers.IO. One instance per Wind screen.
 */
class WindFieldStore(private val context: Context) {
    private val mem = HashMap<String, WindSeries>()
    private var lastFailMillis = 0L
    // Serializes fetches: a pan during a slow request waits, then finds its nodes already fresh.
    private val fetchLock = Any()

    /** What happened on the last [ensure]: whether we're showing older data because the fetch failed. */
    data class Status(val offline: Boolean, val refused: Boolean)

    @Synchronized
    fun get(key: String): WindSeries? = mem[key] ?: WindNodeCache.load(context, key)?.also { mem[key] = it }

    /** The node nearest a point, if we hold it — the sheet's offline fallback. */
    @Synchronized
    fun nearest(lat: Double, lon: Double): WindSeries? {
        val i = Math.round(lat / WindNodes.STEP).toInt()
        val j = Math.round(lon / WindNodes.STEP).toInt()
        get(WindNodes.key(i, j))?.let { return it }
        return mem.values.minByOrNull { (it.lat - lat) * (it.lat - lat) + (it.lon - lon) * (it.lon - lon) }
            ?.takeIf { Math.abs(it.lat - lat) < 0.2 && Math.abs(it.lon - lon) < 0.2 }
    }

    /** Make sure every node in [plan] is loaded and fresh (< 1 h), fetching the rest in ONE request. */
    fun ensure(plan: WindNodes.Plan): Status = synchronized(fetchLock) { ensureLocked(plan) }

    private fun ensureLocked(plan: WindNodes.Plan): Status {
        val now = System.currentTimeMillis()
        val keys = plan.keys()
        val need = keys.filter { (i, j) ->
            val s = get(WindNodes.key(i, j))
            s == null || now - s.fetchedAtMillis > REFRESH_MS
        }
        if (need.isEmpty()) return Status(offline = false, refused = false)
        // After a failure, don't hammer the network on every pan; wait a minute.
        if (now - lastFailMillis < 60_000) return Status(offline = true, refused = false)
        if (!WindBudget.tryTake(context, need.size)) return Status(offline = false, refused = true)
        val points = need.map { (i, j) -> WindNodes.lat(i) to WindNodes.lon(j) }
        return try {
            val got = HrrrApi.fetch(points)
            synchronized(this) {
                need.forEachIndexed { k, (i, j) ->
                    val key = WindNodes.key(i, j)
                    mem[key] = got[k]
                    WindNodeCache.save(context, key, got[k])
                }
            }
            Status(offline = false, refused = false)
        } catch (e: Exception) {
            android.util.Log.w("KairosWind", "HRRR fetch failed", e)
            lastFailMillis = now
            Status(offline = true, refused = false)
        }
    }

    @Synchronized
    fun grid(plan: WindNodes.Plan, axisStartSec: Long): WindGrid = WindGrid.build(plan, axisStartSec) { mem[it] }

    companion object {
        const val REFRESH_MS = 3_600_000L
    }
}
