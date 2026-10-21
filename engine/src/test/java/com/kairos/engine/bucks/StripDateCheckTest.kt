package com.kairos.engine.bucks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** On his real photos: every strip read vs the NOAA hourly temperature at the camera. */
class StripDateCheckTest {
    private val zone = ZoneId.of("America/New_York")

    private class Row(val i: Int, val read: LocalDateTime, val stripF: Int, val truth: LocalDateTime)

    private val lines = javaClass.classLoader!!.getResourceAsStream("strip_dates_2026-10.csv")!!.bufferedReader().readLines()
    private val hours: Map<Long, Double> = lines.filter { it.startsWith("H,") }.associate { l ->
        val (_, t, v) = l.split(",")
        LocalDateTime.parse(t).atZone(zone).toEpochSecond() to v.toDouble()
    }
    private val rows = lines.filter { it.startsWith("P,") }.map { l ->
        val p = l.split(",")
        Row(p[1].toInt(), LocalDateTime.parse(p[2]), p[3].toInt(), LocalDateTime.parse(p[4]))
    }

    /** Linear between the two hours around [t], like the app's moment lookup. */
    private fun model(t: Long): Double? {
        val a = t / 3600 * 3600
        val ta = hours[a] ?: return null
        val tb = hours[a + 3600] ?: return ta
        return ta + (tb - ta) * (t - a) / 3600.0
    }

    @Test fun flagsOnlyTheMisreadDatesAndOffersTheRealOne() {
        val now = LocalDateTime.of(2026, 10, 13, 23, 0)
        // Burst twins the app would pass in: other photos with the same clock time and temperature.
        fun twins(r: Row) = rows.filter { it !== r && it.read.toLocalTime() == r.read.toLocalTime() && it.stripF == r.stripF }.map { it.read }
        val fixes = rows.mapNotNull { r -> StripDateCheck.check(r.read, r.stripF, zone, ::model, now, twins(r))?.let { r.i to it } }.toMap()
        // Exactly the two misreads are flagged, and the real date is the first option for both.
        assertEquals(setOf(51, 52), fixes.keys)
        assertEquals(LocalDateTime.of(2026, 10, 7, 0, 8), fixes[51]!!.options.first())
        assertEquals(LocalDateTime.of(2026, 10, 7, 0, 8), fixes[51]!!.twin)
        assertEquals(LocalDateTime.of(2026, 10, 11, 7, 31), fixes[52]!!.options.first())
        // Without its twin, #51 still offers the real date among the options (10/04 matches too).
        val alone = StripDateCheck.check(rows.first { it.i == 51 }.read, 44, zone, ::model, now)!!
        assertEquals(true, LocalDateTime.of(2026, 10, 7, 0, 8) in alone.options)
        rows.filter { it.read == it.truth }.forEach { r -> assertNull("photo #${r.i}", fixes[r.i]) }
    }
}
