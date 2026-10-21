package com.kairos.engine.bucks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BuckInsightsTest {
    private val zone = ZoneId.of("America/New_York")
    private val lat = 43.8500
    private val lon = -70.5800

    private fun epoch(s: String) = LocalDateTime.parse(s).atZone(zone).toEpochSecond()

    @Test fun sunTimesMatchPublishedPortlandTimes() {
        // Portland, ME, 2026-10-07: Open-Meteo daily gives sunrise 06:45, sunset 18:11 EDT (checked 2026-10-07).
        val d = Sun.day(LocalDate.of(2026, 10, 7), 43.66, -70.26)
        val rise = java.time.Instant.ofEpochSecond(d.sunrise!!).atZone(zone).toLocalTime()
        val set = java.time.Instant.ofEpochSecond(d.sunset!!).atZone(zone).toLocalTime()
        assertTrue("sunrise $rise", rise.toSecondOfDay() in (6 * 3600 + 43 * 60)..(6 * 3600 + 47 * 60))
        assertTrue("sunset $set", set.toSecondOfDay() in (18 * 3600 + 9 * 60)..(18 * 3600 + 13 * 60))
    }

    @Test fun lightPeriods() {
        assertEquals(Light.NIGHT, lightAt(epoch("2026-10-07T23:00"), lat, lon, zone).light)
        assertEquals(Light.EVENING, lightAt(epoch("2026-10-07T17:45"), lat, lon, zone).light)
        assertEquals(Light.DAWN, lightAt(epoch("2026-10-07T06:40"), lat, lon, zone).light)
        val l = lightAt(epoch("2026-10-07T18:30"), lat, lon, zone)
        assertTrue(l.legal) // within 30 min after sunset
        assertTrue(l.minsToSunset!! < 0)
    }

    @Test fun binomialTails() {
        assertEquals(1.0 / 1024, Binomial.upper(10, 10, 0.5), 1e-9)
        assertEquals(1.0, Binomial.upper(10, 0, 0.3), 1e-9)
        assertEquals(Binomial.lower(10, 3, 0.4) + Binomial.upper(10, 4, 0.4), 1.0, 1e-9)
    }

    /** Hourly weather: west wind 1 hour in 4, north otherwise; flat pressure; 50 °F. */
    private fun series(from: Long, hours: Int): List<WxHour> = (0 until hours).map { i ->
        WxHour(from + i * 3600L, 50.0, 6.0, if (i % 4 == 0) 270.0 else 0.0, 10.0, 1015.0, 50.0, 0.0)
    }

    private fun camera(id: String, from: Long, hours: Int): CameraWx {
        val b = FeatureBuilder(lat, lon, zone) { null }
        val built = b.build(series(from, hours))
        return CameraWx(CamInfo(id, "Cam $id", lat, lon, 0.0), built) { t -> b.atMoment(t, built) }
    }

    @Test fun westWindBuckIsStrongAgainstBaseline() {
        val start = epoch("2026-09-20T00:00")
        val cam = camera("1", start, 24 * 20)
        // Five visits, each on an hour that blew from the west (i % 4 == 0), days apart.
        val photos = (0 until 5).map { d -> BuckPhoto("p$d", "1", start + (d * 72 + 20) * 3600L, Heading.RIGHT) }
        val r = BuckInsights.analyze("b", photos, mapOf("1" to cam), zone, start + 24 * 20 * 3600L)
        assertEquals(5, r.visits.size)
        val w = r.factors.first { it.key == "wind_dir" }.bins.first { it.label == "W" }
        assertEquals(5, w.visits)
        assertTrue("lift ${w.lift}", w.lift > 3)
        assertEquals(Verdict.STRONG, w.verdict)
        // Facing north, walking frame-right = heading east, so he comes from the west.
        assertEquals(270.0, r.cameras.first().approachDeg!!, 1.0)
        assertEquals("SW/W/NW", BuckPlanner.safeWinds(270.0))
    }

    @Test fun visitWindowIsTheSetting() {
        val t = epoch("2026-10-01T18:00")
        val photos = listOf(0L, 60L, 600L, 1700L, 4000L).mapIndexed { i, s -> BuckPhoto("p$i", "1", t + s, null) }
        val saved = BuckInsights.visitGapSec
        try {
            BuckInsights.visitGapSec = 30 * 60L
            BuckInsights.visits(photos, emptyMap()).let { assertEquals(2, it.size); assertEquals(4, it[0].photoIds.size) }
            // The 10-minute default splits 1700 s off: a deer 18 min later is its own visit.
            BuckInsights.visitGapSec = 10 * 60L
            BuckInsights.visits(photos, emptyMap()).let { assertEquals(3, it.size); assertEquals(3, it[0].photoIds.size) }
        } finally {
            BuckInsights.visitGapSec = saved
        }
    }

    @Test fun movesBetweenCamerasAreCounted() {
        val start = epoch("2026-09-20T00:00")
        val cams = mapOf("1" to camera("1", start, 24 * 10), "2" to camera("2", start, 24 * 10))
        val photos = listOf(
            BuckPhoto("a", "1", start + 18 * 3600L, null),
            BuckPhoto("b", "2", start + 20 * 3600L, null),
            BuckPhoto("c", "1", start + (48 + 18) * 3600L, null),
            BuckPhoto("d", "2", start + (48 + 19) * 3600L, null),
        )
        val r = BuckInsights.analyze("b", photos, cams, zone, start + 24 * 10 * 3600L)
        val m = r.moves.first { it.fromId == "1" && it.toId == "2" }
        assertEquals(2, m.count)
        assertEquals(90, m.medianGapMin)
    }

    @Test fun coldFrontIsFound() {
        val start = epoch("2026-10-01T00:00")
        // Pressure dips to a low at hour 48 then climbs 6 hPa; temps 60 before, 48 after.
        val hrs = (0 until 120).map { i ->
            val p = if (i <= 48) 1015.0 - (i / 48.0) * 8 else 1007.0 + ((i - 48) / 12.0).coerceAtMost(1.0) * 6
            WxHour(start + i * 3600L, if (i < 48) 60.0 else 48.0, 8.0, if (i < 48) 200.0 else 315.0, 12.0, p, 40.0, 0.0)
        }
        val f = FeatureBuilder(lat, lon, zone) { null }.build(hrs)
        assertEquals(FrontState.AFTER_COLD_FRONT, f[60].front)
        assertEquals(FrontState.NONE, f[110].front)
        assertNotNull(f[80].tempDay24)
        assertNull(f[10].tempDay24)
    }

    @Test fun mentorsCallThePostFrontEvening() {
        val start = epoch("2026-10-01T00:00")
        // A cold front with a big drop: 60 -> 44 °F after a pressure low at hour 48 (24-h mean falls 12°F+ by evening).
        val hrs = (0 until 120).map { i ->
            val p = if (i <= 48) 1015.0 - (i / 48.0) * 8 else 1007.0 + ((i - 48) / 12.0).coerceAtMost(1.0) * 6
            WxHour(start + i * 3600L, if (i < 48) 60.0 else 44.0, 8.0, if (i < 48) 200.0 else 315.0, 12.0, p, 40.0, 0.0)
        }
        val f = FeatureBuilder(lat, lon, zone) { null }.build(hrs)
        val evening = f.first { it.t >= start + 64 * 3600L && it.light == Light.EVENING } // the evening after it passed
        val ids = MentorWeather.hits(evening).map { it.ruleId }
        assertTrue(ids.toString(), "m-wth-cold-front-trifecta-01" in ids)
        assertTrue(ids.toString(), "m-wth-big-temp-drop-01" in ids) // 12 °F colder: past MSU's ~10 °F start
        assertTrue("m-wth-evening-temp-band-01" in ids) // one source, still taken into account
        val front = MentorWeather.hits(evening).first { it.ruleId == "m-wth-cold-front-trifecta-01" }
        assertEquals(Basis.HUNTERS, front.basis)
    }

    @Test fun nightOnlyBuckGetsNightPlan() {
        val start = epoch("2026-09-20T00:00")
        val cam = camera("1", start, 24 * 20)
        val photos = (0 until 4).map { d -> BuckPhoto("p$d", "1", start + (d * 48 + 2) * 3600L, null) }
        val r = BuckInsights.analyze("b", photos, mapOf("1" to cam), zone, start + 24 * 20 * 3600L)
        assertTrue(r.plan.headline.startsWith("Night-only"))
        assertTrue(r.plan.mentors.any { it.ruleId == "m-wth-wait-for-daylight-pattern-01" })
    }
}
