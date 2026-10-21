package com.kairos.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the NWS-fallback translation: ISO-8601 duration expansion, the °C→°F /
 * km/h→mph / Pa→inHg conversions, the next-24h cold-front minimum, and the ~6h
 * pressure trend from observation history. Uses compact synthetic fixtures shaped
 * exactly like api.weather.gov so the expected values are hand-checkable.
 */
class NwsWeatherTest {

    // "now" = 2026-09-03T18:00Z (14:00 EDT). Every series covers this hour exactly.
    private val now: Instant = Instant.parse("2026-09-03T18:00:00Z")

    private val grid = """
        {"properties":{
          "temperature":{"uom":"wmoUnit:degC","values":[
            {"validTime":"2026-09-03T17:00:00+00:00/PT2H","value":20},
            {"validTime":"2026-09-03T19:00:00+00:00/PT4H","value":15},
            {"validTime":"2026-09-03T23:00:00+00:00/PT24H","value":10}
          ]},
          "skyCover":{"uom":"wmoUnit:percent","values":[
            {"validTime":"2026-09-03T17:00:00+00:00/PT3H","value":40}
          ]},
          "windSpeed":{"uom":"wmoUnit:km_h-1","values":[
            {"validTime":"2026-09-03T17:00:00+00:00/PT6H","value":16.09344}
          ]}
        }}
    """.trimIndent()

    // Latest pressure 30.00 inHg at 17:55Z; 6h earlier (11:55Z) it was 30.10 → falling 0.10.
    private val obs = """
        {"features":[
          {"properties":{"timestamp":"2026-09-03T17:55:00+00:00","barometricPressure":{"unitCode":"wmoUnit:Pa","value":101591.67}}},
          {"properties":{"timestamp":"2026-09-03T14:55:00+00:00","barometricPressure":{"unitCode":"wmoUnit:Pa","value":101760.0}}},
          {"properties":{"timestamp":"2026-09-03T11:55:00+00:00","barometricPressure":{"unitCode":"wmoUnit:Pa","value":101930.3}}}
        ]}
    """.trimIndent()

    @Test
    fun translatesGridAndObsIntoAForecast() {
        val f = NwsWeather.parse(grid, obs, "America/New_York", "Sebago Lake, ME", now)

        assertEquals("NWS", f.source)
        assertEquals("Sebago Lake, ME", f.placeLabel)
        assertEquals("2026-09-03", f.dateLabel) // 18:00Z → 14:00 EDT, still the 3rd

        // 20°C at 18:00 → 68°F.
        assertEquals(68.0, f.airF, 1e-9)
        // 16.09344 km/h → 10.0 mph.
        assertEquals(10.0, f.windMph, 1e-9)
        // 40% cloud, rounded to the nearest 5.
        assertEquals(40.0, f.cloudPct, 1e-9)

        // 101591.67 Pa → 30.00 inHg; six hours prior 30.10 → trend −0.10.
        assertEquals(30.00, f.pressureInHg, 1e-9)
        assertEquals(-0.10, f.pressureTrendInHg, 1e-9)

        // Coldest air in the next 24h is the 10°C (50°F) block → 68 − 50 = 18°F drop.
        assertEquals(18.0, f.tempDropNext24hF, 1e-9)

        // NWS path omits the timing curve and sun times honestly.
        assertTrue(f.timing == null)
        assertTrue(f.sunrise == null)
    }

    @Test
    fun unknownSixHourReadingLeavesTrendSteady() {
        // Only a current reading, no history within the 90-min window → trend 0.
        val shallow = """
            {"features":[
              {"properties":{"timestamp":"2026-09-03T17:55:00+00:00","barometricPressure":{"unitCode":"wmoUnit:Pa","value":101591.67}}}
            ]}
        """.trimIndent()
        val f = NwsWeather.parse(grid, shallow, "America/New_York", "Sebago Lake, ME", now)
        assertEquals(30.00, f.pressureInHg, 1e-9)
        assertEquals(0.0, f.pressureTrendInHg, 1e-9)
    }
}
