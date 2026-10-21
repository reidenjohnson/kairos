package com.kairos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class WindFieldTest {

    @Test
    fun planStaysUnderTheCap() {
        // Zoomed in: native 0.03° spacing.
        val p1 = WindNodes.plan(43.80, -70.65, 44.00, -70.45)
        assertEquals(1, p1.m)
        assertTrue(p1.count <= WindNodes.MAX_NODES)
        // Zoomed way out: coarser, still capped, nodes still on the 0.03° lattice.
        val p2 = WindNodes.plan(40.0, -75.0, 47.0, -66.0)
        assertTrue(p2.m > 1)
        assertTrue(p2.count <= WindNodes.MAX_NODES)
        assertEquals(0, p2.i0 % p2.m)
        assertEquals(p2.count, p2.keys().size)
        // The plan always covers the box.
        assertTrue(WindNodes.lat(p2.i0) <= 40.0 && WindNodes.lat(p2.i1) >= 47.0)
    }

    private fun reply(lat: Double, speeds: List<Double?>, dirs: List<Double?>, t0: Long): String {
        fun arr(v: List<Double?>) = v.joinToString(",", "[", "]") { it?.toString() ?: "null" }
        val times = speeds.indices.joinToString(",", "[", "]") { (t0 + it * 3600L).toString() }
        return """{"latitude":$lat,"longitude":-70.5,"utc_offset_seconds":-14400,"hourly":{"time":$times,
            "wind_speed_10m":${arr(speeds)},"wind_direction_10m":${arr(dirs)},"wind_gusts_10m":${arr(speeds)},
            "temperature_2m":${arr(speeds)},"cloud_cover":${arr(speeds)},"precipitation":${arr(speeds)},"pressure_msl":${arr(speeds)}}}"""
    }

    @Test
    fun parsesSingleAndMultiReplies() {
        val t0 = 1_790_000_000L
        val one = HrrrApi.parse(reply(44.0, listOf(5.0, null), listOf(270.0, 0.0), t0), listOf(44.0 to -70.5), 1L)
        assertEquals(1, one.size)
        assertEquals(5f, one[0].speed[0])
        assertTrue(one[0].speed[1].isNaN())
        assertEquals(-14400, one[0].utcOffsetSec)
        assertEquals(1, one[0].indexOf(t0 + 3600))
        assertEquals(-1, one[0].indexOf(t0 + 7200))

        val multi = "[" + reply(44.0, listOf(1.0), listOf(0.0), t0) + "," + reply(44.03, listOf(2.0), listOf(0.0), t0) + "]"
        val two = HrrrApi.parse(multi, listOf(44.0 to -70.5, 44.03 to -70.5), 1L)
        assertEquals(2, two.size)
        assertEquals(2f, two[1].speed[0])
        // Keys stay on the REQUESTED lattice coords, not the model's snapped ones.
        assertEquals(44.03, two[1].lat, 0.0)
    }

    @Test
    fun urlAsksForHrrrWindow() {
        val u = HrrrApi.url(listOf(44.0 to -70.5, 44.03 to -70.47))
        assertTrue(u.contains("latitude=44.0000,44.0300"))
        assertTrue(u.contains("longitude=-70.5000,-70.4700"))
        assertTrue(u.contains("models=gfs_hrrr"))
        assertTrue(u.contains("past_days=7"))
        assertTrue(u.contains("wind_speed_unit=mph"))
    }

    /** A series covering the whole axis with a constant wind. */
    private fun constSeries(lat: Double, lon: Double, start: Long, speed: Float, dir: Float): WindSeries {
        val n = WindAxis.COUNT
        return WindSeries(
            lat, lon, 1L, 0, LongArray(n) { start + it * 3600L },
            FloatArray(n) { speed }, FloatArray(n) { dir }, FloatArray(n) { speed },
            FloatArray(n), FloatArray(n), FloatArray(n), FloatArray(n),
        )
    }

    @Test
    fun gridInterpolatesBetweenNodes() {
        val start = WindAxis.startSec()
        val plan = WindNodes.Plan(m = 1, i0 = 1000, i1 = 1001, j0 = -2000, j1 = -1999)
        // West column: 10 mph from W (u = +10). East column: 20 mph from W (u = +20).
        val series = mapOf(
            WindNodes.key(1000, -2000) to constSeries(30.0, -60.0, start, 10f, 270f),
            WindNodes.key(1001, -2000) to constSeries(30.03, -60.0, start, 10f, 270f),
            WindNodes.key(1000, -1999) to constSeries(30.0, -59.97, start, 20f, 270f),
            WindNodes.key(1001, -1999) to constSeries(30.03, -59.97, start, 20f, 270f),
        )
        val g = WindGrid.build(plan, start) { series[it] }
        assertEquals(4, g.nodesWithData)
        g.setHour(10f)
        val out = FloatArray(2)
        assertTrue(g.sample(30.015, -59.985, out))
        assertEquals(15f, out[0], 1e-3f)
        assertEquals(0f, out[1], 1e-3f)
        assertFalse(g.sample(31.0, -59.985, out)) // outside the plan
        assertTrue(g.hourHasData(10))
    }

    @Test
    fun gridToleratesMissingCorners() {
        val start = WindAxis.startSec()
        val plan = WindNodes.Plan(m = 1, i0 = 1000, i1 = 1001, j0 = -2000, j1 = -1999)
        val only = constSeries(30.0, -60.0, start, 10f, 0f) // north wind: v = -10
        val g = WindGrid.build(plan, start) { if (it == WindNodes.key(1000, -2000)) only else null }
        g.setHour(0f)
        val out = FloatArray(2)
        assertTrue(g.sample(30.005, -59.995, out)) // close to the one node we have
        assertEquals(-10f, out[1], 1e-3f)
        assertFalse(g.sample(30.029, -59.971, out)) // far corner: too little support
    }

    @Test
    fun cacheRoundTrips() {
        val s = constSeries(44.0, -70.5, 1_790_000_000L, 7.5f, 123f)
        val bytes = ByteArrayOutputStream().also { WindNodeCache.write(DataOutputStream(it), s) }.toByteArray()
        val back = WindNodeCache.read(DataInputStream(ByteArrayInputStream(bytes)))!!
        assertEquals(s.times.size, back.times.size)
        assertEquals(7.5f, back.speed[3])
        assertEquals(123f, back.dir[200])
        assertEquals(44.0, back.lat, 0.0)
    }
}
