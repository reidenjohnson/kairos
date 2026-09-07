package com.kairos.data

import com.kairos.engine.WaterTempTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

/** [UsgsWater.parse]: nearest recent gauge wins, °C→°F, and range/age gating. */
class UsgsWaterTest {

    private val sebago = Place(43.85, -70.56, "Sebago Lake, ME")
    // 2 h after the readings below (all dated 12:00 -04:00).
    private val now = OffsetDateTime.parse("2026-09-06T14:00:00-04:00").toInstant()

    /** A USGS IV response with the given (siteName, lat, lon, tempC, dateTime) rows. */
    private fun json(vararg rows: Array<String>): String {
        val series = rows.joinToString(",") { r ->
            """
            {"sourceInfo":{"siteName":"${r[0]}",
              "geoLocation":{"geogLocation":{"latitude":${r[1]},"longitude":${r[2]}}}},
             "values":[{"value":[{"value":"${r[3]}","dateTime":"${r[4]}"}]}]}
            """.trimIndent()
        }
        return """{"value":{"timeSeries":[$series]}}"""
    }

    @Test
    fun `picks the nearest recent gauge and converts C to F`() {
        val out = UsgsWater.parse(
            json(
                arrayOf("Far River, ME", "43.60", "-70.20", "18.0", "2026-09-06T12:00:00.000-04:00"),
                arrayOf("Sebago Lake near Standish", "43.86", "-70.57", "20.0", "2026-09-06T12:00:00.000-04:00"),
            ),
            sebago, now,
        )
        assertNotNull(out)
        // 20°C = 68°F; nearest site is the Sebago one.
        assertEquals(68.0, out!!.tempF, 0.0)
        assertEquals(WaterTempTier.GAUGE, out.tier)
        assertEquals("USGS gauge", out.label)
    }

    @Test
    fun `a stale reading is rejected`() {
        val out = UsgsWater.parse(
            json(arrayOf("Sebago Lake", "43.86", "-70.57", "20.0", "2026-09-05T12:00:00.000-04:00")),
            sebago, now, // ~26 h old > 12 h cap
        )
        assertNull(out)
    }

    @Test
    fun `a far gauge is rejected`() {
        val out = UsgsWater.parse(
            json(arrayOf("Distant Pond", "44.80", "-70.10", "20.0", "2026-09-06T12:00:00.000-04:00")),
            sebago, now, // > 30 mi away
        )
        assertNull(out)
    }

    @Test
    fun `missing sentinel values are skipped`() {
        val out = UsgsWater.parse(
            json(arrayOf("Sebago Lake", "43.86", "-70.57", "-999999", "2026-09-06T12:00:00.000-04:00")),
            sebago, now,
        )
        assertNull(out)
    }
}
