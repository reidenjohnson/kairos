package com.kairos.data

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.floor

/**
 * A terrain wind pack (`.kwp`): a WindNinja run over one hunting area — wind bent by the actual
 * ridges and hollows at ~30 m, where HRRR only knows 3 km cells. Generated off-device (tools/
 * windninja) and dropped into `filesDir/windpacks/`. Little-endian layout:
 *
 *     "KWP1" | int32 headerLen | headerLen bytes UTF-8 JSON |
 *     for each hour (header.hours order): int16[nrows*ncols] u, then int16[nrows*ncols] v
 *
 * Grid row 0 is the NORTH row, col 0 the WEST column; values are mph x 100 of the vector the air
 * moves TOWARD (u east, v north — same as [WindMath]); -32768 = no data. Header: name, centerLat/
 * Lon, west/south/east/north (outer cell edges, EPSG:4326), ncols, nrows, hours (unix s UTC,
 * ascending), meshMeters, source, createdUtc, optional elev (base64 int16 meters, same shape).
 *
 * The file is memory-mapped, so a 20 MB pack costs nothing until a cell is read.
 */
class WindPack private constructor(
    val file: File,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val ncols: Int,
    val nrows: Int,
    val hours: LongArray,
    val meshMeters: Double,
    val source: String,
    val createdUtc: Long,
    private val elevBase64: String?,
    private val buf: ByteBuffer,
    private val dataStart: Int,
) {
    private val cells = nrows * ncols
    private val dLon = (east - west) / ncols
    private val dLat = (north - south) / nrows

    // Per-frame time bracket (see [setTime]); -1 = the pack doesn't cover that time.
    private var t0 = -1
    private var t1 = -1
    private var tf = 0f

    /** Elevation grid (meters, row 0 north), decoded on first use; null if the pack has none. */
    val elev: ShortArray? by lazy {
        val b64 = elevBase64 ?: return@lazy null
        val bytes = java.util.Base64.getDecoder().decode(b64)
        if (bytes.size < cells * 2) return@lazy null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        ShortArray(cells) { bb.getShort(it * 2) }
    }

    fun contains(lat: Double, lon: Double) = lat in south..north && lon in west..east

    /** Does the pack have the moment [tSec]? (Within half an hour of its first/last hour counts.) */
    fun covers(tSec: Long): Boolean =
        hours.isNotEmpty() && tSec >= hours.first() - 1800 && tSec <= hours.last() + 1800

    /** Pick the time to sample (unix seconds, fractional OK): brackets it once so [sample] stays cheap. */
    fun setTime(tSec: Double) {
        if (hours.isEmpty() || tSec < hours.first() - 1800 || tSec > hours.last() + 1800) { t0 = -1; return }
        val t = tSec.coerceIn(hours.first().toDouble(), hours.last().toDouble())
        var lo = 0
        var hi = hours.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (hours[mid] <= t) lo = mid else hi = mid
        }
        if (hours.size == 1 || t <= hours[lo]) { t0 = lo; t1 = lo; tf = 0f; return }
        t0 = lo; t1 = hi
        tf = ((t - hours[lo]) / (hours[hi] - hours[lo])).toFloat().coerceIn(0f, 1f)
    }

    /** Raw value (mph) of component [comp] (0 = u, 1 = v) at hour index [h], cell (row, col); NaN = no data. */
    fun value(h: Int, comp: Int, row: Int, col: Int): Float {
        val idx = dataStart + ((h * 2 + comp) * cells + row * ncols + col) * 2
        val raw = buf.getShort(idx)
        return if (raw == NO_DATA) Float.NaN else raw / 100f
    }

    /**
     * Sample at (lat, lon) for the time set by [setTime]: bilinear between cell centers, linear
     * between hours. Writes (u, v) mph to [out] and returns the blend weight in [0, 1] — 1 inside,
     * easing to 0 across the outer [EDGE_BLEND] of the box so the pack fades into HRRR with no seam.
     * 0 = not covered (outside, wrong time, or a no-data cell).
     */
    fun sample(lat: Double, lon: Double, out: FloatArray): Float {
        if (t0 < 0 || !contains(lat, lon)) return 0f
        val fx = minOf(lon - west, east - lon) / (east - west)
        val fy = minOf(lat - south, north - lat) / (north - south)
        val w = (smooth(fx / EDGE_BLEND) * smooth(fy / EDGE_BLEND)).toFloat()
        if (w <= 0f) return 0f
        // Continuous cell coords with centers on integers; clamp to the outermost centers.
        val cx = ((lon - west) / dLon - 0.5).coerceIn(0.0, (ncols - 1).toDouble())
        val cy = ((north - lat) / dLat - 0.5).coerceIn(0.0, (nrows - 1).toDouble())
        val c0 = floor(cx).toInt().coerceAtMost(maxOf(ncols - 2, 0))
        val r0 = floor(cy).toInt().coerceAtMost(maxOf(nrows - 2, 0))
        val c1 = (c0 + 1).coerceAtMost(ncols - 1)
        val r1 = (r0 + 1).coerceAtMost(nrows - 1)
        val ax = (cx - c0).toFloat()
        val ay = (cy - r0).toFloat()
        for (comp in 0..1) {
            val a = lerpT(comp, r0, c0); val b = lerpT(comp, r0, c1)
            val c = lerpT(comp, r1, c0); val d = lerpT(comp, r1, c1)
            if (a.isNaN() || b.isNaN() || c.isNaN() || d.isNaN()) return 0f
            val top = a + (b - a) * ax
            val bot = c + (d - c) * ax
            out[comp] = top + (bot - top) * ay
        }
        return w
    }

    /** One-off sample at a whole moment [tSec] without disturbing the per-frame time set by [setTime]. */
    fun sampleAt(lat: Double, lon: Double, tSec: Long, out: FloatArray): Float {
        val s0 = t0; val s1 = t1; val sf = tf
        setTime(tSec.toDouble())
        val w = sample(lat, lon, out)
        t0 = s0; t1 = s1; tf = sf
        return w
    }

    private fun lerpT(comp: Int, row: Int, col: Int): Float {
        val a = value(t0, comp, row, col)
        if (t1 == t0) return a
        val b = value(t1, comp, row, col)
        return a + (b - a) * tf
    }

    /** The box as a closed GeoJSON ring, for the outline on the map. */
    fun outlineGeoJson(): String =
        """{"type":"Feature","properties":{"name":${JSONObject.quote(name)}},"geometry":{"type":"LineString",""" +
            """"coordinates":[[$west,$south],[$east,$south],[$east,$north],[$west,$north],[$west,$south]]}}"""

    companion object {
        const val NO_DATA: Short = -32768
        /** Outer fraction of the box (each side) over which the pack blends into HRRR. */
        const val EDGE_BLEND = 0.10
        const val DIR = "windpacks"

        private fun smooth(x: Double): Double {
            val t = x.coerceIn(0.0, 1.0)
            return t * t * (3 - 2 * t)
        }

        /** Open + validate a pack. Throws with a clear message if the file is malformed. */
        fun open(file: File): WindPack {
            val ch = RandomAccessFile(file, "r").channel
            val buf = ch.use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) }
                .order(ByteOrder.LITTLE_ENDIAN)
            // Absolute bulk get(index, array) is API 34+; read through a positioned duplicate instead.
            val magic = ByteArray(4).also { buf.duplicate().apply { position(0) }.get(it) }
            require(String(magic, Charsets.US_ASCII) == "KWP1") { "${file.name}: not a KWP1 pack" }
            val headerLen = buf.getInt(4)
            require(headerLen in 2..(buf.capacity() - 8)) { "${file.name}: bad header length $headerLen" }
            val hb = ByteArray(headerLen).also { buf.duplicate().apply { position(8) }.get(it) }
            val h = JSONObject(String(hb, Charsets.UTF_8))
            val ncols = h.getInt("ncols")
            val nrows = h.getInt("nrows")
            val ha = h.getJSONArray("hours")
            val hours = LongArray(ha.length()) { ha.getLong(it) }
            require(ncols > 0 && nrows > 0) { "${file.name}: empty grid" }
            require(hours.indices.drop(1).all { hours[it] > hours[it - 1] }) { "${file.name}: hours not ascending" }
            val dataStart = 8 + headerLen
            val need = dataStart.toLong() + hours.size.toLong() * 2 * nrows * ncols * 2
            require(buf.capacity() >= need) { "${file.name}: truncated (${buf.capacity()} < $need bytes)" }
            val west = h.getDouble("west"); val east = h.getDouble("east")
            val south = h.getDouble("south"); val north = h.getDouble("north")
            require(east > west && north > south) { "${file.name}: bad bounds" }
            return WindPack(
                file = file,
                name = h.optString("name", file.nameWithoutExtension),
                centerLat = h.optDouble("centerLat", (south + north) / 2),
                centerLon = h.optDouble("centerLon", (west + east) / 2),
                west = west, south = south, east = east, north = north,
                ncols = ncols, nrows = nrows, hours = hours,
                meshMeters = h.optDouble("meshMeters", Double.NaN),
                source = h.optString("source", "WindNinja"),
                createdUtc = h.optLong("createdUtc", 0L),
                elevBase64 = if (h.has("elev") && !h.isNull("elev")) h.getString("elev") else null,
                buf = buf,
                dataStart = dataStart,
            )
        }

        /** Every readable pack in [dir]; a broken file is logged and skipped, never fatal. */
        fun loadAll(dir: File): List<WindPack> =
            (dir.listFiles { f -> f.isFile && f.name.endsWith(".kwp", ignoreCase = true) } ?: emptyArray())
                .sortedBy { it.name }
                .mapNotNull { f ->
                    runCatching { open(f) }
                        .onFailure { System.err.println("KairosWind: skipping pack ${f.name}: ${it.message}") }
                        .getOrNull()
                }
    }
}

/**
 * HRRR + any terrain packs, as ONE field: where a pack covers the point and the hour it wins,
 * blended into HRRR over its outer edge. This is what the particles and the tap sheet read.
 * Not thread-safe: [setTime] then [sample] on one thread (the UI thread, per frame).
 */
open class WindSampler(val grid: WindGrid?, val packs: List<WindPack>) {
    private val tmp = FloatArray(2)

    /** Select the moment to sample: a fractional slider index (blends between hours). */
    open fun setHour(hour: Float) {
        grid?.setHour(hour)
        val start = grid?.axisStartSec ?: WindAxis.startSec()
        val t = start + hour.toDouble() * 3600.0
        packs.forEach { it.setTime(t) }
    }

    /** (u, v) mph toward into [out]. Returns the pack weight used (0 = pure HRRR), or -1 = no data. */
    open fun sample(lat: Double, lon: Double, out: FloatArray): Float {
        var best = 0f
        var pu = 0f
        var pv = 0f
        for (p in packs) {
            val w = p.sample(lat, lon, tmp)
            if (w > best) { best = w; pu = tmp[0]; pv = tmp[1] }
        }
        val hasGrid = grid?.sample(lat, lon, out) == true
        if (best <= 0f) return if (hasGrid) 0f else -1f
        if (!hasGrid || best >= 1f) {
            out[0] = pu; out[1] = pv
            return 1f
        }
        out[0] = pu * best + out[0] * (1 - best)
        out[1] = pv * best + out[1] * (1 - best)
        return best
    }
}
