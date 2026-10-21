package com.kairos.engine.bucks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class HuntForecastTest {
    private val zone = ZoneId.of("America/New_York")
    private val lat = 43.8500
    private val lon = -70.5800
    private fun epoch(s: String) = LocalDateTime.parse(s).atZone(zone).toEpochSecond()

    private val his = listOf(
        VisitWind(1, "c", 334.0, 7.0, 58.0), // Buck 1, Oct 2 (real HRRR at the camera)
        VisitWind(2, "c", 252.0, 7.1, 44.3), // Oct 6
        VisitWind(3, "c", 186.0, 4.8, 48.9), // Oct 8
    )

    @Test fun matchesWithin30DegreesAcrossNorth() {
        assertEquals(listOf(1L), HisWinds.matching(355.0, 8.0, his).map { it.t }) // 21° from 334
        assertEquals(listOf(1L), HisWinds.matching(4.0, 8.0, his).map { it.t })   // 30° across 0
        assertTrue(HisWinds.matching(5.0, 8.0, his).isEmpty())                  // 31°: out
        assertEquals(listOf(2L), HisWinds.matching(270.0, 10.0, his).map { it.t })
    }

    @Test fun calmOnlyMatchesCalm() {
        assertTrue(HisWinds.matching(334.0, 2.0, his).isEmpty())
        val calmVisit = listOf(VisitWind(9, "c", null, 1.5, 40.0))
        assertEquals(1, HisWinds.matching(90.0, 2.5, calmVisit).size)
        assertTrue(HisWinds.matching(90.0, 6.0, calmVisit).isEmpty())
    }

    @Test fun modelCheck() {
        fun h(d: Double, m: Double, ad: Double?, am: Double?) = WxHour(0, 50.0, m, d, null, null, null, null, altDir = ad, altMph = am)
        assertEquals(ModelCheck.AGREE, WindCheck.of(h(330.0, 9.0, 292.0, 8.0)))  // 38° apart
        assertEquals(ModelCheck.SPLIT, WindCheck.of(h(262.0, 3.2 + 2, 34.0, 6.0))) // the real h200 case: W vs NE
        assertEquals(ModelCheck.AGREE, WindCheck.of(h(10.0, 2.0, 200.0, 3.0)))   // both near calm
        assertEquals(ModelCheck.ONE, WindCheck.of(h(10.0, 8.0, null, null)))
        assertEquals(ModelCheck.SPLIT, WindCheck.of(h(270.0, 0.2, 309.0, 4.6)))  // the real 10/14 dawn: calm vs NW 5
        assertEquals(ModelCheck.AGREE, WindCheck.of(h(300.0, 3.5, 310.0, 6.0)))  // both a real wind, 10° apart
    }

    /** A forecast where the wind is NNW (matches Oct 2) and both models agree, every hour. */
    private fun forecast(from: Long, hours: Int, alt: Double = 330.0): Pair<List<HourFeatures>, List<WxHour>> {
        val raw = (0 until hours).map { i -> WxHour(from + i * 3600L, 45.0, 8.0, 335.0, 14.0, 1018.0, 30.0, 0.0, "Forecast", altDir = alt, altMph = 8.0) }
        return FeatureBuilder(lat, lon, zone) { null }.build(raw) to raw
    }

    private fun report(): BuckReport {
        val start = epoch("2026-10-01T00:00")
        val wx = (0 until 24 * 10).map { i -> WxHour(start + i * 3600L, 55.0, 7.0, 334.0, 10.0, 1015.0, 50.0, 0.0) }
        val b = FeatureBuilder(lat, lon, zone) { null }
        val built = b.build(wx)
        val cam = CameraWx(CamInfo("c", "eyeball1", lat, lon, 77.0), built) { t -> b.atMoment(t, built) }
        val photos = listOf(BuckPhoto("p1", "c", epoch("2026-10-02T23:45"), null), BuckPhoto("p2", "c", epoch("2026-10-06T03:41"), null))
        return BuckInsights.analyze("b", photos, mapOf("c" to cam), zone, epoch("2026-10-10T00:00"))
    }

    @Test fun sitsSpanTheFirstAndLastThreeHoursOfShootingLight() {
        val r = report()
        val from = epoch("2026-10-13T00:00")
        val (fc, raw) = forecast(from, 24 * 3)
        val sits = HuntWindows.upcoming(r, fc, raw, from, CamInfo("c", "eyeball1", lat, lon, 77.0), null, zone)
        assertEquals(6, sits.size) // 3 days x morning + evening
        val d = Sun.day(java.time.LocalDate.of(2026, 10, 14), lat, lon)
        val am = sits.first { it.kind == SitKind.MORNING && Instant.ofEpochSecond(it.start).atZone(zone).dayOfMonth == 14 }
        assertEquals(d.sunrise!! - 1800, am.start)
        assertEquals(am.start + 3 * 3600, am.end)
        val pm = sits.first { it.kind == SitKind.EVENING && Instant.ofEpochSecond(it.start).atZone(zone).dayOfMonth == 14 }
        assertEquals(d.sunset!! + 1800, pm.end)
        // Every sit carries his wind (NNW, like his visits) and says how many visits that is.
        assertTrue(sits.all { it.hisWind.isNotEmpty() && it.hisWindOf == r.visits.size })
        // The numbers shown come from the hour the score comes from, inside the sit.
        assertTrue(sits.all { it.bestHour + 3600 > it.start && it.bestHour < it.end })
    }

    @Test fun splitModelsEarnNoWindCredit() {
        val r = report()
        val from = epoch("2026-10-13T00:00")
        val (fcA, rawA) = forecast(from, 24 * 2)
        val (fcS, rawS) = forecast(from, 24 * 2, alt = 150.0) // ECMWF says SSE: split
        val cam = CamInfo("c", "eyeball1", lat, lon, 77.0)
        val agree = HuntWindows.upcoming(r, fcA, rawA, from, cam, null, zone)
        val split = HuntWindows.upcoming(r, fcS, rawS, from, cam, null, zone)
        assertTrue(split.all { it.hisWind.isEmpty() && it.check == ModelCheck.SPLIT })
        agree.zip(split).forEach { (a, s) -> assertTrue(a.score > s.score) }
        // Ranking puts the sure wind ahead at an equal score.
        val ranked = HuntWindows.ranked(listOf(split.first().copy(score = 5), agree.first().copy(score = 5)))
        assertEquals(ModelCheck.AGREE, ranked.first().check)
    }

    @Test fun nightOnlyPlanSaysGoNotHold() {
        val r = report()
        assertTrue(r.plan.headline.contains("edges of light"))
        assertFalse(r.plan.actions.any { it.title.startsWith("Hold off") })
    }

    @Test fun cameraActivityMatchesWindAndTimeOfDay() {
        assertEquals(20, CamActivity.clockGap(1430, 10)) // 11:50 PM vs 12:10 AM, across midnight
        val day = epoch("2026-10-17T00:00")
        val v7 = DeerVisit(1, listOf("a"), setOf("Doe"), 320.0, 8.0, 7 * 60 + 10)   // a doe at 7:10 AM on NW
        val v19 = DeerVisit(2, listOf("b"), setOf("Young"), 320.0, 8.0, 19 * 60)    // NW but 7 PM
        val v7s = DeerVisit(3, listOf("c"), setOf("Not sure"), 180.0, 8.0, 7 * 60) // 7 AM but S wind
        val hours = (0 until 24).map { i -> WxHour(day + i * 3600L, 40.0, 10.0, 315.0, 20.0, 1018.0, 50.0, 0.0, altDir = 320.0, altMph = 11.0) }
        val h7 = hours[7]
        assertEquals(setOf(1L, 2L), CamActivity.windMatches(h7.windDir, h7.windMph, listOf(v7, v19, v7s)).map { it.t }.toSet())
        assertEquals(listOf(1L), CamActivity.windAndTime(h7, zone, listOf(v7, v19, v7s)).map { it.t })
        val sr = Sun.day(java.time.LocalDate.of(2026, 10, 17), lat, lon).sunrise!!
        val (wind, both) = CamActivity.forSit(sr - 1800, sr - 1800 + 3 * 3600, hours, zone, listOf(v7, v19, v7s))
        assertEquals(setOf(1L, 2L), wind.map { it.t }.toSet())
        assertEquals(listOf(1L), both.map { it.t })
    }

    @Test fun similarWeatherFindsTheSameWindNearTheSameTime() {
        val from = epoch("2026-10-14T00:00")
        // Day 1 NW all day, day 2 S all day, day 3 NW but ECMWF says S (split).
        val raw = (0 until 72).map { i ->
            val d = when (i / 24) { 0 -> 320.0; 1 -> 180.0; else -> 320.0 }
            val alt = if (i / 24 == 2) 170.0 else d
            WxHour(from + i * 3600L, 50.0 - (i % 24) / 3.0, 8.0, d, 15.0, 1015.0, 50.0, 0.0, altDir = alt, altMph = 8.0)
        }
        val fc = FeatureBuilder(lat, lon, zone) { null }.build(raw)
        // The photo: NNW 7 mph, 56 F, 11:45 PM (Buck 1, Oct 2).
        val r = SimilarWeather.find(334.0, 7.0, 56.0, 23 * 60 + 45, fc, raw, from, zone)
        assertEquals(1, r.sameTime.size) // only day 1 has his wind with the models agreeing
        val s = r.sameTime.single()
        assertEquals(14, Instant.ofEpochSecond(s.h.t).atZone(zone).dayOfMonth)
        assertTrue(s.clockGapMin <= SimilarWeather.SAME_TIME_MIN)
        assertTrue(r.huntable.single().legal)
        assertTrue(r.anyOnWind == 24)
    }
}
