package com.kairos.engine.bucks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StandWindTest {
    private val zone = ZoneId.of("America/New_York")
    private val lat = 43.8500
    private val lon = -70.5800
    private fun epoch(s: String) = LocalDateTime.parse(s).atZone(zone).toEpochSecond()

    @Test fun sectorsAreCenteredOnTheirPoint() {
        assertEquals(0, StandWind.sectorOf(0.0))
        assertEquals(0, StandWind.sectorOf(11.0))
        assertEquals(1, StandWind.sectorOf(11.3))
        assertEquals(15, StandWind.sectorOf(337.5))  // NNW
        assertEquals(0, StandWind.sectorOf(349.0))   // N, across 0
        assertEquals(0, StandWind.sectorOf(360.0))
    }

    @Test fun fitsOnlyARealWind() {
        val nToNnw = setOf(15, 0)
        assertEquals(true, StandWind.fits(nToNnw, 340.0, 8.0))
        assertEquals(false, StandWind.fits(nToNnw, 200.0, 8.0))
        assertNull(StandWind.fits(nToNnw, 200.0, 2.0))  // calm: nobody knows where scent goes
        assertNull(StandWind.fits(emptySet(), 340.0, 8.0))
    }

    @Test fun labelsReadClockwiseAcrossNorth() {
        assertEquals("NNW–N", StandWind.label(setOf(15, 0)))
        assertEquals("NW–N", StandWind.label(setOf(14, 15, 0)))
        assertEquals("E", StandWind.label(setOf(4)))
        assertEquals("S–SSW, NW", StandWind.label(setOf(8, 9, 14)))
    }

    /** His eyeball1 daylight mornings (handoff 2026-10-14): 4 on N (339-11°), 2 calm, 1 W. */
    @Test fun suggestsWhereDaylightDeerCame() {
        fun v(t: Long, dir: Double?, legal: Boolean = true) = DeerVisit(t, listOf("p$t"), setOf("Doe"), dir, if (dir == null) 1.0 else 5.0, 7 * 60, legal)
        val visits = listOf(v(1, 339.0), v(2, 350.0), v(3, 5.0), v(4, 11.0), v(5, null), v(6, null), v(7, 270.0), v(8, 180.0, legal = false))
        val s = StandWind.suggest(visits)!!
        assertTrue(s.daylightOnly)
        assertEquals(setOf(15, 0), s.sectors)   // NNW (339°) + N (350, 5, 11): edges with nothing trimmed
        assertEquals(4, s.hits); assertEquals(5, s.of); assertEquals(2, s.calm)
    }

    @Test fun tooFewDaylightVisitsUsesThemAll() {
        fun v(t: Long, dir: Double, legal: Boolean) = DeerVisit(t, listOf("p$t"), setOf("Doe"), dir, 6.0, 0, legal)
        val s = StandWind.suggest(listOf(v(1, 180.0, true), v(2, 185.0, false), v(3, 190.0, false)))!!
        assertTrue(!s.daylightOnly)
        assertEquals(setOf(8), s.sectors); assertEquals(3, s.hits)
        assertNull(StandWind.suggest(listOf(DeerVisit(1, listOf("a"), setOf("Doe"), null, 1.0, 0))))
    }

    /** A sit on his wind earns the points; one on a wrong real wind ranks behind every other. */
    @Test fun yourWindRanksSits() {
        val start = epoch("2026-10-01T00:00")
        val wx = (0 until 24 * 10).map { i -> WxHour(start + i * 3600L, 55.0, 7.0, 90.0, 10.0, 1015.0, 50.0, 0.0) }
        val b = FeatureBuilder(lat, lon, zone) { null }
        val built = b.build(wx)
        val cam = CamInfo("c", "eyeball1", lat, lon, 77.0)
        val r = BuckInsights.analyze("b", listOf(BuckPhoto("p1", "c", epoch("2026-10-02T23:45"), null)), mapOf("c" to CameraWx(cam, built) { t -> b.atMoment(t, built) }), zone, epoch("2026-10-10T00:00"))
        val from = epoch("2026-10-13T00:00")
        // Day 1 NNW all day, day 2 S all day; both models agree.
        val raw = (0 until 48).map { i -> val d = if (i < 24) 340.0 else 180.0; WxHour(from + i * 3600L, 45.0, 8.0, d, 12.0, 1018.0, 30.0, 0.0, "Forecast", altDir = d, altMph = 8.0) }
        val fc = FeatureBuilder(lat, lon, zone) { null }.build(raw)
        val plain = HuntWindows.upcoming(r, fc, raw, from, cam, null, zone)
        val mine = HuntWindows.upcoming(r, fc, raw, from, cam, null, zone, yourWind = setOf(15, 0))
        val nnw = mine.filter { it.windDir == 340.0 }; val s = mine.filter { it.windDir == 180.0 }
        assertTrue(nnw.isNotEmpty() && s.isNotEmpty())
        assertTrue(nnw.all { it.windOk == true && "your wind for this stand" in it.matches })
        assertTrue(s.all { it.windOk == false })
        plain.zip(mine).filter { it.second.windOk == true }.forEach { (p, m) -> assertEquals(p.score + StandWind.PTS, m.score) }
        // Every point is listed with what earned it, and the list adds up to the score shown.
        (plain + mine).forEach { assertEquals(it.score, it.points.sumOf { p -> p.second }) }
        assertTrue(nnw.all { w -> w.points.any { it.first == "your wind for this stand" && it.second == StandWind.PTS } })
        // Even with a higher score, the wrong wind ranks behind.
        val ranked = HuntWindows.ranked(listOf(s.first().copy(score = 99), nnw.first()))
        assertEquals(true, ranked.first().windOk)
    }
}
