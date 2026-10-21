package com.kairos.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tiered water-temperature resolver: estimate math, priority, and freshness. */
class WaterTempTest {

    @After
    fun reset() = WaterUserReading.clear()

    // --- estimate() -------------------------------------------------------------

    @Test
    fun `estimate is the seasonal baseline when air is normal`() {
        // July baseline 73°F, normal air 69°F; recent air == normal → no nudge.
        val r = estimate(7, recentAvgAirF = 69.0)
        assertEquals(73.0, r.tempF, 0.0)
        assertEquals(WaterTempTier.ESTIMATE, r.tier)
        assertTrue(r.estimated)
    }

    @Test
    fun `estimate with no recent air falls back to the flat baseline`() {
        assertEquals(SEBAGO_WATER_F.getValue(6).toDouble(), estimate(6, null).tempF, 0.0)
    }

    @Test
    fun `a warm spell nudges the estimate up, clamped`() {
        // +20°F above normal → 0.35*20 = 7, clamped to +6 → 73 + 6 = 79.
        assertEquals(79.0, estimate(7, recentAvgAirF = 89.0).tempF, 0.0)
    }

    @Test
    fun `a cold snap nudges the estimate down, clamped`() {
        // −20°F below normal → clamped to −6 → 73 − 6 = 67.
        assertEquals(67.0, estimate(7, recentAvgAirF = 49.0).tempF, 0.0)
    }

    // --- resolve() priority -----------------------------------------------------

    @Test
    fun `resolve prefers a user reading over a gauge over the estimate`() {
        val user = WaterTempReading(60.0, WaterTempTier.USER, "Your reading", "")
        val gauge = WaterTempReading(64.0, WaterTempTier.GAUGE, "USGS gauge", "")
        assertEquals(user, resolve(7, 69.0, userReading = user, gauge = gauge))
        assertEquals(gauge, resolve(7, 69.0, userReading = null, gauge = gauge))
        assertEquals(WaterTempTier.ESTIMATE, resolve(7, 69.0).tier)
    }

    // --- WaterUserReading freshness --------------------------------------------

    @Test
    fun `a fresh user reading is used and a stale one is ignored`() {
        val now = 1_000_000_000_000L
        val dayMs = 86_400_000L
        WaterUserReading.set(58.0, now - 1 * dayMs)
        assertEquals(58.0, WaterUserReading.freshTempF(now))
        assertEquals(1L, WaterUserReading.ageDays(now))

        WaterUserReading.set(58.0, now - (WaterUserReading.FRESH_DAYS + 1) * dayMs)
        assertNull(WaterUserReading.freshTempF(now))
    }

    @Test
    fun `no reading on file is not fresh`() {
        WaterUserReading.clear()
        assertNull(WaterUserReading.freshTempF(1L))
        assertFalse(WaterUserReading.tempF != null)
    }
}
