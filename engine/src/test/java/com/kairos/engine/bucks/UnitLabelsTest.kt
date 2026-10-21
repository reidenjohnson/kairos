package com.kairos.engine.bucks

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Bin labels and mentor text follow the user's units; thresholds and matching don't move. */
class UnitLabelsTest {
    private val metric = object : UnitFmt by Imperial {
        override fun temp(f: Double) = "${tempNum(f)}°C"
        override fun tempDelta(f: Double) = "${tempDeltaNum(f)}°"
        override fun speed(mph: Double) = "${speedNum(mph)} km/h"
        override val tempUnit = "°C"
        override fun tempNum(f: Double) = ((f - 32) * 5 / 9).roundToInt()
        override fun tempDeltaNum(f: Double) = (f * 5 / 9).roundToInt()
        override val speedUnit = "km/h"
        override fun speedNum(mph: Double) = (mph * 1.609344).roundToInt()
        override fun rain(inches: Double) = "%.1f mm".format(inches * 25.4)
    }

    @After fun reset() { Factors.units = Imperial }

    @Test fun imperialLabelsUnchanged() {
        assertEquals(listOf("Under 32 °F", "32-45 °F", "45-55 °F", "55-65 °F", "65 °F+"), Factors.tempBand.bins)
        assertEquals(listOf("10+ °F colder", "4-10 °F colder", "About the same (±4 °F)", "4-10 °F warmer", "10+ °F warmer"), Factors.tempSwing.bins)
        assertEquals("Calm (<3 mph)", Factors.windSpeed.bins[0])
    }

    @Test fun metricLabels() {
        Factors.units = metric
        assertEquals(listOf("Under 0 °C", "0-7 °C", "7-13 °C", "13-18 °C", "18 °C+"), Factors.tempBand.bins)
        assertEquals(listOf("6+ °C colder than normal", "2-6 °C colder", "Near normal (±2 °C)", "2-6 °C warmer", "6+ °C warmer"), Factors.tempNormal.bins)
        assertEquals(listOf("Calm (<5 km/h)", "Light (5-11 km/h)", "Moderate (11-19 km/h)", "Strong (19+ km/h)"), Factors.windSpeed.bins)
        assertEquals("Calm (<5 km/h)", Factors.windDir.bins[8])
        assertTrue(Factors.weather.flatMap { it.bins }.none { "°F" in it || "mph" in it })
    }

    @Test fun mentorHitsInMetric() {
        val h = HourFeatures(
            0L, Light.EVENING, true, null, 30, 40.0, 4.0, 90.0, 6.0, 1015.0, 20.0, 0.2, 0.0, -18.0, -8.0,
            FrontState.NONE, RainState.RAINING, 0.0, 0.5, SeasonPhase.EARLY, "HRRR",
        )
        val text = MentorWeather.hits(h, metric).joinToString(" | ") { it.label + " " + (it.caveat ?: "") }
        assertTrue(text, "°F" !in text && "mph" !in text && " in/" !in text)
        assertTrue(text, "10°C colder than the day before" in text)
        assertTrue(text, "Wind down 13 km/h" in text)
        assertTrue(text, "0-13 °C band" in text)
        assertTrue(text, "over 2.5 mm/hr" in text)
    }
}
