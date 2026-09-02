package com.kairos.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Locks the Maine season table + status logic. Dates trace to official Maine
 * IF&W sources (see Seasons.kt citations); these tests catch a mistyped date or
 * a broken status calc, they do not re-derive the regulations.
 */
class SeasonsTest {

    @Test fun everyScoredSpeciesHasSeasons() {
        for (sp in SPECIES) {
            assertNotNull("missing seasons for ${sp.name}", seasonsFor(sp.name))
        }
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
        // Sanity: seasons carry no scoring weight; species set is still 11.
        assertEquals(11, SPECIES.size)
        assertTrue(MAINE_SEASONS.size >= SPECIES.size)
    }
}
