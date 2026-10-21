package com.kairos.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Locks the Maine season table + status logic. Dates trace to official Maine
 * IF&W sources (see Seasons.kt citations); these tests catch a mistyped date or
 * a broken status calc, they do not re-derive the regulations.
 */
class SeasonsTest {

    @Test fun everyHuntSpeciesHasSeasons() {
        // Hunting seasons are tabled; fishing is intentionally not (open all year).
        for (sp in SPECIES.filter { it.side == Side.HUNT }) {
            assertNotNull("missing seasons for ${sp.name}", seasonsFor(sp.name))
        }
    }

    @Test fun fishHaveNoSeasonTable() {
        for (sp in SPECIES.filter { it.side == Side.FISH }) {
            assertNull("fish should not be tabled: ${sp.name}", seasonsFor(sp.name))
        }
        assertTrue(MAINE_SEASONS.all { it.side == Side.HUNT })
    }

    @Test fun deerFirearmsOpenMidNovember() {
        val deer = seasonsFor("Whitetail deer")!!
        val status = seasonStatus(deer, LocalDate.of(2026, 11, 15))
        assertEquals(SeasonStatusKind.OPEN, status.kind)
        assertEquals("Firearms", status.activeWindow!!.label)
    }

    @Test fun deerUpcomingBeforeArchery() {
        val deer = seasonsFor("Whitetail deer")!!
        // Sep 1 is before expanded archery (Sep 12) — next window is upcoming.
        val status = seasonStatus(deer, LocalDate.of(2026, 9, 1))
        assertEquals(SeasonStatusKind.UPCOMING, status.kind)
        assertEquals(11, status.daysUntilNext)
    }

    @Test fun elkHasNoSeason() {
        val elk = seasonsFor("Elk")!!
        assertEquals(SeasonStatusKind.NONE, seasonStatus(elk, LocalDate.of(2026, 11, 1)).kind)
    }

    @Test fun grouseOpensLateSeptember() {
        val upland = seasonsFor("Upland birds")!!
        assertEquals(
            SeasonStatusKind.OPEN,
            seasonStatus(upland, LocalDate.of(2026, 10, 15)).kind,
        )
    }

    @Test fun weightsUnaffectedBySeasonsFile() {
        // Sanity: seasons carry no scoring weight; the roster is the full 21.
        assertEquals(21, SPECIES.size)
        assertEquals(SPECIES.count { it.side == Side.HUNT }, MAINE_SEASONS.size)
    }

    @Test fun turkeyFallOpenInOctober() {
        val turkey = seasonsFor("Wild turkey")!!
        assertEquals(SeasonStatusKind.OPEN, seasonStatus(turkey, LocalDate.of(2026, 10, 15)).kind)
    }

    @Test fun coyoteOpenYearRound() {
        val coyote = seasonsFor("Coyote")!!
        assertEquals(SeasonStatusKind.OPEN, seasonStatus(coyote, LocalDate.of(2026, 2, 1)).kind)
    }
}
