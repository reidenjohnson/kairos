package com.kairos.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Parity tests for the moon port. Expected phase/illumination values were
 * generated from astral 3.2 (the library the Python reference uses), so this
 * proves the on-device port matches it across the lunar cycle and leap years.
 */
class MoonTest {
    private val eps = 1e-6

    private data class Golden(val date: LocalDate, val phase: Double, val illum: Double)

    private val goldens = listOf(
        Golden(LocalDate.of(2026, 1, 1), 11.855667, 0.846833),
        Golden(LocalDate.of(2026, 3, 14), 23.677889, 0.308722),
        Golden(LocalDate.of(2026, 6, 30), 14.422333, 0.969833),
        Golden(LocalDate.of(2026, 9, 1), 18.155667, 0.703167),
        Golden(LocalDate.of(2026, 12, 25), 15.511222, 0.892056),
        Golden(LocalDate.of(2024, 2, 29), 18.233444, 0.697611),
        Golden(LocalDate.of(2000, 1, 6), 27.800111, 0.014278),
    )

    @Test fun phaseMatchesAstral() {
        for (g in goldens) {
            assertEquals("phase ${g.date}", g.phase, moonPhase(g.date), eps)
        }
    }

    @Test fun illuminationMatchesReference() {
        for (g in goldens) {
            assertEquals("illum ${g.date}", g.illum, moonInfo(g.date).illum, eps)
        }
    }

    @Test fun phaseNames() {
        assertEquals("waxing gibbous", moonInfo(LocalDate.of(2026, 1, 1)).phaseName)
        assertEquals("full", moonInfo(LocalDate.of(2026, 6, 30)).phaseName)
        assertEquals("waning crescent", moonInfo(LocalDate.of(2000, 1, 6)).phaseName)
    }
}
