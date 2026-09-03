package com.kairos.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the shape of the time-of-day activity curve (dawn/dusk peaks). */
class TimeOfDayTest {
    private val sunrise = 6.0
    private val sunset = 19.0

    @Test fun peaksAtFirstAndLastLight() {
        val dawn = timeOfDayActivity(6.0, sunrise, sunset, Chronotype.CREPUSCULAR)
        val dusk = timeOfDayActivity(19.0, sunrise, sunset, Chronotype.CREPUSCULAR)
        val midday = timeOfDayActivity(12.5, sunrise, sunset, Chronotype.CREPUSCULAR)
        val night = timeOfDayActivity(1.0, sunrise, sunset, Chronotype.CREPUSCULAR)
        assertTrue("dawn should beat midday", dawn > midday)
        assertTrue("dusk should beat midday", dusk > midday)
        assertTrue("midday should beat deep night", midday > night)
        assertEquals("dawn peaks near 1.0", 1.0, dawn, 0.06)
    }

    @Test fun lowLightFeedsMoreAtNight() {
        val crepNight = timeOfDayActivity(1.0, sunrise, sunset, Chronotype.CREPUSCULAR)
        val lowNight = timeOfDayActivity(1.0, sunrise, sunset, Chronotype.LOW_LIGHT)
        assertTrue("low-light species are more active at night", lowNight > crepNight)
    }

    @Test fun multiplierStaysInGentleRange() {
        assertEquals(0.6, activityMultiplier(0.0), 1e-9)
        assertEquals(1.0, activityMultiplier(1.0), 1e-9)
        assertEquals(0.8, activityMultiplier(0.5), 1e-9)
    }
}
