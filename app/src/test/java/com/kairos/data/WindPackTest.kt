package com.kairos.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a synthetic .kwp (the fixture generator) and reads it back through [WindPack], checking
 * orientation (row 0 = north, col 0 = west), scaling (mph x 100), no-data, time interpolation and
 * the edge blend. Also the format reference for tools/windninja's writer.
 */
class WindPackTest {

    /** The fixture generator: u/v per (hour, row, col) in mph, NaN = no data. */
    private fun writePack(
        file: File,
        west: Double, south: Double, east: Double, north: Double,
        ncols: Int, nrows: Int,
        hours: List<Long>,
        elev: ShortArray? = null,
        uv: (h: Int, row: Int, col: Int) -> Pair<Double, Double>,
    ) {
        val header = JSONObject()
            .put("name", "Test ridge")
            .put("centerLat", (south + north) / 2).put("centerLon", (west + east) / 2)
            .put("west", west).put("south", south).put("east", east).put("north", north)
            .put("ncols", ncols).put("nrows", nrows)
            .put("hours", JSONArray(hours))
            .put("meshMeters", 50.0)
            .put("source", "WindNinja")
            .put("createdUtc", 1_790_000_000L)
        if (elev != null) {
            val eb = ByteBuffer.allocate(elev.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            elev.forEach { eb.putShort(it) }
            header.put("elev", java.util.Base64.getEncoder().encodeToString(eb.array()))
        }
        val hb = header.toString().toByteArray(Charsets.UTF_8)
        val n = ncols * nrows
        val buf = ByteBuffer.allocate(8 + hb.size + hours.size * 2 * n * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("KWP1".toByteArray(Charsets.US_ASCII))
        buf.putInt(hb.size)
        buf.put(hb)
        fun enc(x: Double): Short = if (x.isNaN()) WindPack.NO_DATA else Math.round(x * 100).toInt().toShort()
        for (h in hours.indices) {
            for (r in 0 until nrows) for (c in 0 until ncols) buf.putShort(enc(uv(h, r, c).first))
            for (r in 0 until nrows) for (c in 0 until ncols) buf.putShort(enc(uv(h, r, c).second))
        }
        file.writeBytes(buf.array())
    }

    private fun tempPack(): File = File.createTempFile("test", ".kwp").also { it.deleteOnExit() }

    @Test
    fun readsHeaderAndOrientation() {
        val f = tempPack()
        val hours = listOf(1_790_000_000L, 1_790_003_600L)
        // 4 cols x 3 rows. u = column index (west->east), v = row index (north->south), + 10 at hour 1.
        writePack(f, -71.0, 44.0, -70.96, 44.03, ncols = 4, nrows = 3, hours = hours) { h, r, c ->
            (c + 10.0 * h) to (r + 10.0 * h)
        }
        val p = WindPack.open(f)
        assertEquals("Test ridge", p.name)
        assertEquals(4, p.ncols); assertEquals(3, p.nrows)
        assertEquals(2, p.hours.size)
        assertEquals(50.0, p.meshMeters, 0.0)
        // Raw access: row 0 is north, col 0 is west.
        assertEquals(3f, p.value(0, 0, 0, 3), 1e-4f)  // u at NE cell, hour 0
        assertEquals(2f, p.value(0, 1, 2, 0), 1e-4f)  // v at SW cell, hour 0
        assertEquals(13f, p.value(1, 0, 1, 3), 1e-4f) // hour 1 adds 10

        val out = FloatArray(2)
        p.setTime(hours[0].toDouble())
        // Center of the NW cell -> exactly (0, 0); center of the SE cell -> (3, 2).
        val dLon = 0.04 / 4; val dLat = 0.03 / 3
        assertTrue(p.sample(44.03 - dLat / 2, -71.0 + dLon / 2, out) >= 0f)
        // NW/SE cell centers sit inside the blend margin, so check the values, not the weight.
        p.sample(44.03 - dLat / 2, -71.0 + dLon / 2, out)
        assertEquals(0f, out[0], 1e-4f); assertEquals(0f, out[1], 1e-4f)
        p.sample(44.0 + dLat / 2, -70.96 - dLon / 2, out)
        assertEquals(3f, out[0], 1e-4f); assertEquals(2f, out[1], 1e-4f)

        // Halfway between the hours -> +5.
        p.setTime((hours[0] + 1800).toDouble())
        p.sample(44.0 + dLat / 2, -70.96 - dLon / 2, out)
        assertEquals(8f, out[0], 1e-3f); assertEquals(7f, out[1], 1e-3f)
    }

    @Test
    fun edgeBlendAndCoverage() {
        val f = tempPack()
        val t = 1_790_000_000L
        writePack(f, -71.0, 44.0, -70.9, 44.1, ncols = 20, nrows = 20, hours = listOf(t)) { _, _, _ -> 5.0 to -5.0 }
        val p = WindPack.open(f)
        val out = FloatArray(2)
        p.setTime(t.toDouble())
        assertEquals(1f, p.sample(44.05, -70.95, out), 1e-6f)            // center: full weight
        val edge = p.sample(44.05, -70.997, out)                          // 3% in from the west edge
        assertTrue(edge > 0f && edge < 1f)
        assertEquals(0f, p.sample(44.05, -71.01, out), 0f)                // outside
        p.setTime((t + 7200).toDouble())
        assertEquals(0f, p.sample(44.05, -70.95, out), 0f)                // 2 h after its only hour
        assertTrue(p.covers(t + 1200)); assertFalse(p.covers(t + 3600))
    }

    @Test
    fun noDataCellsAreNotCovered() {
        val f = tempPack()
        val t = 1_790_000_000L
        writePack(f, -71.0, 44.0, -70.9, 44.1, ncols = 10, nrows = 10, hours = listOf(t)) { _, r, c ->
            if (r == 5 && c == 5) Double.NaN to Double.NaN else 1.0 to 1.0
        }
        val p = WindPack.open(f)
        val out = FloatArray(2)
        p.setTime(t.toDouble())
        // Cell (5,5) center: lon -70.945, lat 44.045.
        assertEquals(0f, p.sample(44.045, -70.945, out), 0f)
        assertEquals(1f, p.sample(44.08, -70.92, out), 1e-6f)
    }

    @Test
    fun decodesElevation() {
        val f = tempPack()
        val elev = ShortArray(6) { (100 + it * 10).toShort() }
        writePack(f, -71.0, 44.0, -70.97, 44.02, ncols = 3, nrows = 2, hours = listOf(1L), elev = elev) { _, _, _ -> 0.0 to 0.0 }
        val p = WindPack.open(f)
        assertNotNull(p.elev)
        assertEquals(150.toShort(), p.elev!![5])
    }

    @Test
    fun samplerPrefersPackAndBlends() {
        val f = tempPack()
        val start = WindAxis.startSec()
        val t = start + WindAxis.NOW_INDEX * 3600L
        writePack(f, -71.0, 44.0, -70.9, 44.1, ncols = 20, nrows = 20, hours = listOf(t)) { _, _, _ -> 10.0 to 0.0 }
        val pack = WindPack.open(f)
        val sampler = WindSampler(grid = null, packs = listOf(pack))
        sampler.setHour(WindAxis.NOW_INDEX.toFloat())
        val out = FloatArray(2)
        assertEquals(1f, sampler.sample(44.05, -70.95, out), 1e-6f)
        assertEquals(10f, out[0], 1e-4f)
        assertEquals(-1f, sampler.sample(45.0, -70.95, out), 0f) // no pack, no grid -> no data
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedPack() {
        val f = tempPack()
        writePack(f, -71.0, 44.0, -70.9, 44.1, ncols = 10, nrows = 10, hours = listOf(1L)) { _, _, _ -> 1.0 to 1.0 }
        f.writeBytes(f.readBytes().copyOf(f.length().toInt() - 10))
        WindPack.open(f)
    }
}
