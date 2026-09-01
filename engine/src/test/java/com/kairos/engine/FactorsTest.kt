package com.kairos.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity tests for the factor functions. Every expected value was generated
 * from the Python reference (forecast.py) with fixed inputs, so these lock the
 * Kotlin port to the same numbers.
 */
class FactorsTest {
    private val eps = 1e-9

    @Test fun clampBounds() {
        assertEquals(0.0, clamp(-0.2), eps)
        assertEquals(0.4, clamp(0.4), eps)
        assertEquals(1.0, clamp(1.7), eps)
    }

    @Test fun trend() {
        assertEquals(0.8, fTrend(-0.06), eps)
    }

    @Test fun range() {
        assertEquals(0.9454545454545434, fRange(30.02), eps)
    }

    @Test fun front() {
        assertEquals(0.6666666666666666, fFront(8.0), eps)
    }

    @Test fun cloud() {
        assertEquals(0.8, fCloud(80.0), eps)
    }

    @Test fun tempCold() {
        assertEquals(0.9736842105263158, fTemp(41.0, TempSpec.Cold(40.0, 78.0)), eps)
    }

    @Test fun tempBand() {
        assertEquals(0.0, fTemp(57.0, TempSpec.Band(ideal = 80.0, spread = 22.0)), eps)
    }

    @Test fun tempColdwater() {
        assertEquals(1.0, fTemp(57.0, TempSpec.Coldwater), eps)
        assertEquals(1.0, fTemp(62.0, TempSpec.Coldwater), eps)
        assertEquals(0.05, fTemp(75.0, TempSpec.Coldwater), eps)
        assertEquals(0.5384615384615384, fTemp(68.0, TempSpec.Coldwater), eps)
    }

    @Test fun wind() {
        assertEquals(1.0, fWind(11.0, 3.0, 12.0, 25.0), eps)   // inside band
        assertEquals(0.6666666666666666, fWind(1.0, 3.0, 12.0, 25.0), eps) // below band
        assertEquals(0.0, fWind(30.0, 8.0, 16.0, 30.0), eps)   // at hard cutoff
        assertEquals(1.0, fWind(0.0, 0.0, 8.0, 18.0), eps)     // lo == 0 guard
    }

    @Test fun moon() {
        assertEquals(0.5, fMoon(0.5, MoonMode.INVERSE), eps)
        assertEquals(0.0, fMoon(0.5, MoonMode.NEWFULL), eps)
        assertEquals(1.0, fMoon(0.0, MoonMode.NEWFULL), eps)
        assertEquals(0.0, fMoon(0.5, MoonMode.NONE), eps)
    }
}
