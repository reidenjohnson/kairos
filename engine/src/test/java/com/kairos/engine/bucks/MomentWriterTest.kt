package com.kairos.engine.bucks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MomentWriterTest {
    private val z = ZoneId.of("America/New_York")
    private val u = object : UnitFmt by Imperial {
        override fun temp(f: Double) = "${f.toInt()}°F"
        override fun tempDelta(f: Double) = "${f.toInt()}°"
        override fun speed(mph: Double) = "${mph.toInt()} mph"
        override fun compass(deg: Double) = compass8(deg)
        override fun clock(t: Long, zone: ZoneId) = java.time.Instant.ofEpochSecond(t).atZone(zone).toLocalTime().toString()
    }
    private fun t(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m).atZone(z).toEpochSecond()

    private fun f(at: Long, light: Light, legal: Boolean, sr: Int?, ss: Int?, dir: Double = 225.0, mph: Double = 6.0) = HourFeatures(
        at, light, legal, sr, ss, 52.0, mph, dir, 9.0, 1015.0, 20.0, 0.0, 0.0, 1.0, 0.0, FrontState.NONE, RainState.DRY, 6.0, 0.7, SeasonPhase.EARLY, "HRRR",
    )

    @Test fun nightVisitBeforeLightIsTailored() {
        val v1 = Visit("cam2", t(2, 23, 45), t(2, 23, 45), listOf("a"), null, f(t(2, 23, 45), Light.NIGHT, false, -1000, -330))
        val now = t(6, 5, 41)
        val v2 = Visit("cam2", now, now, listOf("b"), null, f(now, Light.NIGHT, false, -70, -680))
        val s = MomentWriter.write(v2.f!!, now, z, "cam2", "Eyeball 2", "Buck 1", false, listOf(v1, v2), RutCalendar(), u)
        assertEquals("70 min before sunrise", s.headline)
        assertTrue(s.sub.contains("2nd of 2 visits"))
        val hist = s.lines.first { it.label == "His history" }.text
        assertTrue(hist, hist.contains("All 2 of Buck 1's visits are at Eyeball 2"))
        assertTrue(hist, hist.contains("Every one so far is outside shooting light"))
        val wind = s.lines.first { it.label == "Wind" }.text
        assertTrue(wind, wind.startsWith("From the SW at 6 mph") && wind.contains("toward the NE"))
        assertEquals("He was heading for bed", s.steps.first().title)
        assertTrue(s.steps.any { it.title == "Don't push in for him yet" })
        assertTrue(s.steps.any { it.title.endsWith("days to your rut") })
    }

    @Test fun daylightVisitSaysHuntIt() {
        val now = t(6, 17, 30)
        val v = Visit("cam1", now, now, listOf("c"), null, f(now, Light.EVENING, true, 640, 45, dir = 0.0))
        val s = MomentWriter.write(v.f!!, now, z, "cam1", "Grass draw", "Buck 2", false, listOf(v), RutCalendar(), u)
        assertEquals("45 min before sunset", s.headline)
        assertTrue(s.sub.contains("his first in shooting light"))
        assertEquals("Hunt this exact window", s.steps.first().title)
        assertTrue(s.steps.first().detail, s.steps.first().detail.contains("on a N wind, from the S side"))
    }

    @Test fun windSwingFromQuarterHours() {
        val now = t(6, 3, 0)
        val near = listOf(WindAt(now - 3600, 220.0, 3.4), WindAt(now, 250.0, 7.0), WindAt(now + 3600, 290.0, 6.5))
        val s = MomentWriter.write(f(now, Light.NIGHT, false, -230, -520), now, z, "cam1", "Grass draw", null, false, emptyList(), RutCalendar(), u, near)
        assertTrue(s.lines.any { it.label == "Wind shift" && it.text.contains("70°") })
    }
}
