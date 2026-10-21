package com.kairos.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LocalNormalsTest {

    private fun station(lat: Double, lon: Double, lowBias: Double, highBias: Double) =
        StationBias(lat, lon, DoubleArray(365) { lowBias }, DoubleArray(365) { highBias })

    @Test
    fun dayIndexIgnoresLeapDay() {
        assertEquals(0, normalsDayIndex(LocalDate.of(2026, 1, 1)))
        assertEquals(364, normalsDayIndex(LocalDate.of(2024, 12, 31)))
        assertEquals(normalsDayIndex(LocalDate.of(2024, 2, 28)), normalsDayIndex(LocalDate.of(2024, 2, 29)))
        assertEquals(normalsDayIndex(LocalDate.of(2025, 3, 1)), normalsDayIndex(LocalDate.of(2024, 3, 1)))
    }

    @Test
    fun onTopOfAStationUsesItsCorrection() {
        val s = listOf(station(45.0, -70.0, -4.0, 1.0), station(45.3, -70.0, 2.0, 2.0))
        val n = localNormal(45.0, -70.0, 100, 40.0, 60.0, s)
        assertEquals(NormalsSource.STATION_CORRECTED, n.source)
        // 1 km floor vs ~33 km: the co-located station carries ~99.9% of the weight.
        assertEquals(36.0, n.lowF, 0.02)
        assertEquals(61.0, n.highF, 0.01)
    }

    @Test
    fun farFromAnyStationStaysModeled() {
        val s = listOf(station(45.0, -70.0, -4.0, 1.0))
        val n = localNormal(40.0, -80.0, 100, 40.0, 60.0, s)
        assertEquals(NormalsSource.MODELED, n.source)
        assertEquals(40.0, n.lowF, 0.0)
        assertEquals(60.0, n.highF, 0.0)
    }

    @Test
    fun departureAveragesHighAndLow() {
        val normal = DayNormal(30.0, 50.0, NormalsSource.STATION_CORRECTED)
        // A cold dawn at the normal low isn't "below normal" when the day's high is normal.
        assertEquals(0.0, departureFromNormalF(30.0, 50.0, normal), 0.0)
        assertEquals(-10.0, departureFromNormalF(20.0, 40.0, normal), 0.0)
    }

    @Test
    fun modelNormalsSmoothsAndSkipsLeapDay() {
        val start = LocalDate.of(2019, 1, 1)
        val dates = (0 until 365 * 2 + 1).map { start.plusDays(it.toLong()) } // 2019-2020 (leap)
        val lows = dates.map { if (it.monthValue == 2 && it.dayOfMonth == 29) 999.0 else 10.0 }
        val highs = dates.map { 30.0 }
        val (lo, hi) = modelNormals(dates, lows, highs)!!
        assertEquals(10.0, lo[59], 1e-9)
        assertEquals(30.0, hi[200], 1e-9)
    }

    @Test
    fun modelNormalsNeedsEveryDay() {
        assertNull(modelNormals(listOf(LocalDate.of(2020, 1, 1)), listOf(1.0), listOf(2.0)))
    }

    @Test
    fun bundledTableLoads() {
        val all = StationBiasTable.all
        assertTrue("expected Maine stations, got ${all.size}", all.size >= 60)
        // Every station sits in or near Maine.
        assertTrue(all.all { it.lat in 42.9..47.6 && it.lon in -71.2..-66.8 })
    }
}
