package com.kairos.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The FROM-direction <-> u/v convention. Get this wrong and every arrow on the screen lies. */
class WindMathTest {
    private val eps = 1e-9

    @Test
    fun northWindBlowsSouth() {
        // From 0° (a north wind) the air moves south: u = 0, v negative.
        assertEquals(0.0, WindMath.u(10.0, 0.0), eps)
        assertEquals(-10.0, WindMath.v(10.0, 0.0), eps)
    }

    @Test
    fun westWindBlowsEast() {
        // From 270° (a west wind) the air moves east: u positive.
        assertEquals(10.0, WindMath.u(10.0, 270.0), eps)
        assertEquals(0.0, WindMath.v(10.0, 270.0), 1e-9)
    }

    @Test
    fun southwestWindBlowsNortheast() {
        val u = WindMath.u(10.0, 225.0)
        val v = WindMath.v(10.0, 225.0)
        assertEquals(7.0710678, u, 1e-6)
        assertEquals(7.0710678, v, 1e-6)
    }

    @Test
    fun roundTripsEveryDirection() {
        for (d in 0 until 360 step 5) {
            val u = WindMath.u(12.5, d.toDouble())
            val v = WindMath.v(12.5, d.toDouble())
            assertEquals(12.5, WindMath.speed(u, v), 1e-9)
            assertEquals(d.toDouble(), WindMath.fromDeg(u, v), 1e-6)
        }
    }

    @Test
    fun fromDegOfTowardVectors() {
        assertEquals(0.0, WindMath.fromDeg(0.0, -5.0), eps)    // moving south -> from N
        assertEquals(90.0, WindMath.fromDeg(-5.0, 0.0), eps)   // moving west -> from E
        assertEquals(180.0, WindMath.fromDeg(0.0, 5.0), eps)   // moving north -> from S
        assertEquals(270.0, WindMath.fromDeg(5.0, 0.0), eps)   // moving east -> from W
        assertEquals(0.0, WindMath.fromDeg(0.0, 0.0), eps)     // calm
    }

    @Test
    fun compassLetters() {
        assertEquals("N", WindMath.compass(0.0))
        assertEquals("N", WindMath.compass(359.0))
        assertEquals("NNW", WindMath.compass(337.5))
        assertEquals("NNE", WindMath.compass(22.5))
        assertEquals("SW", WindMath.compass(225.0))
        assertEquals("W", WindMath.compass(272.0))
    }

    @Test
    fun mercatorRoundTrips() {
        for (lat in listOf(-60.0, 0.0, 37.5, 44.123, 70.0)) {
            assertEquals(lat, WindMath.latOf(WindMath.mercY(lat)), 1e-9)
        }
        assertEquals(-70.56, WindMath.lonOf(WindMath.mercX(-70.56)), 1e-9)
        assertEquals(0.5, WindMath.mercY(0.0), 1e-12)
        // y grows southward.
        assert(WindMath.mercY(40.0) < WindMath.mercY(30.0))
    }
}
