package com.kairos.bucks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuartersTest {
    private val s = listOf(
        BuckWeather.Quarter(0, 10.0, 350.0, 15.0, 40.0),
        BuckWeather.Quarter(900, 10.0, 10.0, 15.0, 42.0),
        BuckWeather.Quarter(1800, 6.0, 220.0, 9.0, 44.0),
    )

    @Test fun blendsThroughNorthNotSouth() {
        val q = Quarters.at(s, 450)!!
        val d = if (q.dir > 180) q.dir - 360 else q.dir
        assertEquals(0.0, d, 0.5)
        assertEquals(41.0, q.tempF!!, 0.01)
    }

    @Test fun exactStepAndOutside() {
        assertEquals(220.0, Quarters.at(s, 1800)!!.dir, 0.001)
        assertNull(Quarters.at(s, 5000))
        assertNull(Quarters.at(s, -10))
    }
}
