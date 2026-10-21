package com.kairos.engine.bucks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PhotoMathTest {
    @Test fun readsSpypointStrip() {
        // The strip on his HD0035 (verified 2026-10-06).
        val r = StripParser.parse("SPYPOINT FLEX G-36C 09/24/2026 17:46 65°F 18°C")
        assertEquals(LocalDateTime.of(2026, 9, 24, 17, 46), r.time)
        assertEquals(65, r.tempF)
    }

    @Test fun readsOcrQuirks() {
        val r = StripParser.parse("09/26/2026 04:55\n57oF 14oC")
        assertEquals(LocalDateTime.of(2026, 9, 26, 4, 55), r.time)
        assertEquals(57, r.tempF)
        val pm = StripParser.parse("10/02/2026 11:45 PM 59 F")
        assertEquals(LocalDateTime.of(2026, 10, 2, 23, 45), pm.time)
    }

    @Test fun fixesOcrSlipsSeenOnHisPhone() {
        // Real ML Kit reads from his app saves, 2026-10-07.
        assertEquals(61, StripParser.parse("10/02/2026\n19:18\nG0F\n16°C\nSPYROINT FLEX 6:sc").tempF)
        assertEquals(55, StripParser.parse("10/02/2026\n23:47\n13°C\nSPYPOINT FLEX G-26c").tempF)
        assertEquals(52, StripParser.parse("10/05/2026\n02:59 52F\n11°C").tempF)
        assertEquals(LocalDateTime.of(2026, 10, 5, 20, 5), StripParser.parse("1O/05/2O26 2O:O5 52°F 11°C").time)
        assertEquals(LocalDateTime.of(2026, 9, 23, 15, 5), StripParser.parse("09/23/2026\n03:05\nPM\n71°F\n22°C").time)
    }

    @Test fun moreRealSlips() {
        // Real reads from his phone after the second pass, 2026-10-07.
        assertEquals(LocalDateTime.of(2026, 10, 4, 20, 25), StripParser.parse("10704/2026\n20:25\n48°F\n9°C\nSPYRONT FLEX G-25C").time)
        assertEquals(60, StripParser.parse("10/02/2026 19:18\n60°F\n16°0\nSPYRONT FLEX G-25").tempF)
        assertEquals(72, StripParser.parse("09/23/2026\n03:05 PM\n1°F 22°C\nSPYPOINT FLEX G").tempF)
    }

    @Test fun timeDigitsAreNeverATemperature() {
        // The °F digits lost: the time's minutes used to be read as the temperature (41°F).
        val r = StripParser.parse("10/06/2026 03:41 °F 11°C")
        assertEquals(LocalDateTime.of(2026, 10, 6, 3, 41), r.time)
        assertEquals(52, r.tempF)
        assertNull(StripParser.parse("10/06/2026 03:41\n°F").tempF)
        assertEquals(48, StripParser.parse("10/04/2026 20:25 48°F 9°C").tempF)
    }

    @Test fun passesVote() {
        val good = StripRead(LocalDateTime.of(2026, 10, 6, 3, 41), 52, false)
        val slip = StripRead(LocalDateTime.of(2026, 10, 6, 8, 41), 52, false) // 3 read as 8
        val c = StripVote.pick(listOf(slip, good, good))!!
        assertEquals(good.time, c.read.time)
        assertEquals(2, c.agree)
        assertEquals(listOf(slip.time), c.alternates)
        assertTrue(StripVote.settled(listOf(slip, good, good)))
        assertTrue(!StripVote.settled(listOf(slip, good)))
        // A tie goes to the first pass.
        assertEquals(slip.time, StripVote.pick(listOf(slip, good))!!.read.time)
    }

    @Test fun lightCheckCatchesAmPmSlips() {
        val z = java.time.ZoneId.of("America/New_York")
        fun t(h: Int, m: Int) = LocalDateTime.of(2026, 10, 6, h, m).atZone(z).toEpochSecond()
        // His camera area; sunrise ~6:50, sunset ~6:15 PM on Oct 6.
        val lat = 43.81; val lon = -70.43
        assertTrue(LightCheck.fits(night = true, t(3, 41), lat, lon, z))
        assertTrue(!LightCheck.fits(night = true, t(13, 41), lat, lon, z))   // IR at 1:41 PM: wrong
        assertTrue(!LightCheck.fits(night = false, t(1, 15), lat, lon, z))   // color at 1:15 AM: wrong
        assertTrue(LightCheck.fits(night = true, t(18, 40), lat, lon, z))    // IR in dusk: fine
        assertTrue(LightCheck.fits(night = false, t(18, 40), lat, lon, z))   // color in dusk: fine
    }

    @Test fun sameFrameVsBurst() {
        val w = PhotoMath.GRID_W; val h = PhotoMath.GRID_H
        val base = IntArray(w * h) { (it * 7919 % 251) }
        val resampled = IntArray(w * h) { (base[it] * 0.98 + 3).toInt() } // same frame, other size/JPEG
        val moved = base.copyOf().also { g -> for (y in 20 until 30) for (x in 40 until 50) g[y * w + x] = 255 - g[y * w + x] } // a deer moved
        assertTrue(PhotoMath.sameFrame(PhotoMath.pixelGrid(base), PhotoMath.pixelGrid(resampled)))
        assertTrue(!PhotoMath.sameFrame(PhotoMath.pixelGrid(base), PhotoMath.pixelGrid(moved)))
    }

    @Test fun droppedDigitTakesHigher() {
        // Real reads from his Deer Insight album, 2026-10-07 (strip says 62°F 17°C and 45°F 7°C).
        assertEquals(62, StripParser.parse("10702/2026 00:27 62°F 7°C\nSPYRONT FLEX G6C").tempF)
        assertEquals(45, StripParser.parse("10/06/2026 03:41\n45°F\n1°C\nC\nSPYROINT FLEX G-25c").tempF)
    }

    @Test fun timeOutOfOrder() {
        assertEquals(LocalDateTime.of(2026, 10, 5, 20, 5), StripParser.parse("20:05\n52°F\n10/05/2026\n11°C").time)
    }

    @Test fun rejectsBadDates() {
        assertNull(StripParser.parse("13/45/2026 25:99").time)
        assertNull(StripParser.parse("no strip here").time)
    }

    @Test fun hashesAndSimilarity() {
        val g = IntArray(72) { (it * 37) % 255 }
        assertEquals(0, PhotoMath.hamming(PhotoMath.dHash(g), PhotoMath.dHash(g.copyOf())))
        val e = PhotoMath.edges(IntArray(32 * 18) { if (it % 32 < 16) 30 else 200 }, 32, 18)
        assertEquals(1.0, PhotoMath.similarity(e, e.copyOf()), 1e-9)
        val other = PhotoMath.edges(IntArray(32 * 18) { if (it / 32 < 9) 30 else 200 }, 32, 18)
        assertTrue(PhotoMath.similarity(e, other) < 0.3)
    }
}

class RuleLinksTest {
    /** Every mentor rule id the buck engine links to must exist in the shipped Learn asset. */
    @Test fun linkedRulesExist() {
        val asset = java.io.File("../app/src/main/assets/learn/deer.json").readText()
        val src = java.io.File("src/main/java/com/kairos/engine/bucks").listFiles()!!.joinToString("\n") { it.readText() }
        val ids = Regex(""""(m-[a-z_\-]+-\d\d)"""").findAll(src).map { it.groupValues[1] }.toSet()
        org.junit.Assert.assertTrue(ids.size > 20)
        val missing = ids.filter { "\"$it\"" !in asset }
        org.junit.Assert.assertEquals("missing rule ids: $missing", emptyList<String>(), missing)
    }

    /** The consensus weights in MentorWeather must match "agreement" in the shipped rules. */
    @Test fun mentorAgreeMatchesRules() {
        val asset = org.json.JSONObject(java.io.File("../app/src/main/assets/learn/deer.json").readText()).getJSONObject("entries")
        val src = java.io.File("src/main/java/com/kairos/engine/bucks/BuckPlanner.kt").readText()
        val calls = Regex("""MentorHit\("(m-[a-z_\-]+-\d\d)", "[^"]*", (?:true|false), Basis\.[A-Z]+, (\d)""").findAll(src).toList()
        org.junit.Assert.assertTrue(calls.size >= 8)
        for (c in calls) {
            val id = c.groupValues[1]; val n = c.groupValues[2].toInt()
            org.junit.Assert.assertEquals("agreement for $id", asset.getJSONObject(id).getInt("agreement"), n)
        }
    }

    @Test fun ageQuiz() {
        val e = AgeQuiz.estimate(mapOf(AgeQ.DOE_LIKE to AgeAnswer.NO, AgeQ.DEEP_CHEST to AgeAnswer.YES, AgeQ.NECK_BLENDS to AgeAnswer.NO))!!
        org.junit.Assert.assertEquals("3.5 to 4.5 years", e.label)
        val y = AgeQuiz.estimate(mapOf(AgeQ.DOE_LIKE to AgeAnswer.YES))!!
        org.junit.Assert.assertEquals("1.5 (yearling) years", y.label)
    }

    @Test fun rutCalendar() {
        val c = RutCalendar()
        org.junit.Assert.assertEquals(SeasonPhase.EARLY, c.phase(java.time.LocalDate.of(2026, 10, 7)))
        org.junit.Assert.assertEquals(SeasonPhase.PRE_RUT, c.phase(java.time.LocalDate.of(2026, 11, 5)))
        org.junit.Assert.assertEquals(SeasonPhase.RUT, c.phase(java.time.LocalDate.of(2026, 11, 18)))
        org.junit.Assert.assertEquals(SeasonPhase.POST_RUT, c.phase(java.time.LocalDate.of(2026, 11, 19)))
        org.junit.Assert.assertEquals("Rut (Nov 6 - Nov 18)", c.label(SeasonPhase.RUT))
    }
}
