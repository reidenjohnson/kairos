package com.kairos.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherParseTest {

    /** Yesterday 00:00 .. tomorrow 23:00, hourly temps from [temp] (index 0 = yesterday 00:00). */
    private fun response(days: Int, temp: (Int) -> Double): JSONObject {
        val times = JSONArray(); val t = JSONArray(); val p = JSONArray()
        val w = JSONArray(); val c = JSONArray()
        val start = java.time.LocalDateTime.of(2026, 10, 30, 0, 0)
        for (h in 0 until days * 24) {
            times.put(start.plusHours(h.toLong()).toString())
            t.put(temp(h)); p.put(1015.0); w.put(5.0); c.put(20.0)
        }
        return JSONObject()
            .put("current", JSONObject().put("time", "2026-10-31T08:00"))
            .put("hourly", JSONObject().put("time", times).put("temperature_2m", t)
                .put("pressure_msl", p).put("wind_speed_10m", w).put("cloud_cover", c))
    }

    @Test
    fun dayOverDayChangeIsMeasuredFromFullDays() {
        // Yesterday 40-70, today 25-50: high -20, low -15.
        val r = response(3) { h ->
            val day = h / 24; val hr = h % 24
            val (lo, hi) = if (day == 0) 40.0 to 70.0 else 25.0 to 50.0
            if (hr == 15) hi else if (hr == 5) lo else (lo + hi) / 2
        }
        val f = WeatherRepository.parse(r, "Test")
        assertEquals(25.0, f.todayLowF!!, 0.0)
        assertEquals(50.0, f.todayHighF!!, 0.0)
        assertEquals(-20.0, f.conditions.highChangeVsYesterdayF!!, 0.0)
        assertEquals(-15.0, f.conditions.lowChangeVsYesterdayF!!, 0.0)
        // No normal without a cache dir / network: stays null, never a guess.
        assertNull(f.conditions.tempVsNormalF)
    }

    @Test
    fun partialYesterdayGivesNoChange() {
        val r = response(2) { 40.0 }
        val trimmed = JSONObject(r.toString())
        val hourly = trimmed.getJSONObject("hourly")
        for (k in listOf("time", "temperature_2m", "pressure_msl", "wind_speed_10m", "cloud_cover")) {
            val a = hourly.getJSONArray(k); val b = JSONArray()
            for (i in 6 until a.length()) b.put(a.get(i)) // drop yesterday's first 6 hours
            hourly.put(k, b)
        }
        val f = WeatherRepository.parse(trimmed, "Test")
        assertNull(f.conditions.highChangeVsYesterdayF)
    }
}
