package com.kairos.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Locks the Maine license/lottery deadline table + phase logic. Dates trace to the
 * official IF&W pages cited in Deadlines.kt; these tests catch a mistyped date or a
 * broken phase calc, they do not re-derive the regulations.
 */
class DeadlinesTest {

    private fun moose() = MAINE_DEADLINES.first { it.name == "Moose permit lottery" }
    private fun deer() = MAINE_DEADLINES.first { it.name == "Antlerless deer permit lottery" }

    @Test fun bothLotteriesAreHuntSide() {
        assertTrue(MAINE_DEADLINES.all { it.side == Side.HUNT })
        assertEquals(2, MAINE_DEADLINES.size)
    }

    @Test fun eventsAreChronological() {
        for (d in MAINE_DEADLINES) {
            val dates = d.events.map { it.date }
            assertEquals("events out of order for ${d.name}", dates.sorted(), dates)
        }
    }

    @Test fun everyDeadlineHasAnOfficialSource() {
        for (d in MAINE_DEADLINES) {
            assertTrue(d.sourceUrl.startsWith("https://www.maine.gov/"))
        }
    }

    @Test fun mooseUpcomingBeforeApril() {
        // Mid-March 2026 is before the April 1 open.
        val st = deadlineStatus(moose(), LocalDate.of(2026, 3, 15))
        assertEquals(DeadlinePhase.UPCOMING, st.phase)
        assertEquals(17, st.daysUntilOpen)
    }

    @Test fun mooseOpenInMay() {
        // May 1 is inside the window (open Apr 1, deadline May 18).
        val st = deadlineStatus(moose(), LocalDate.of(2026, 5, 1))
        assertEquals(DeadlinePhase.OPEN, st.phase)
        assertEquals(17, st.daysUntilDeadline)
    }

    @Test fun mooseClosedOnDeadlineDayStillOpen() {
        // The deadline day itself still counts as open (apply by 11:59 pm ET).
        val st = deadlineStatus(moose(), LocalDate.of(2026, 5, 18))
        assertEquals(DeadlinePhase.OPEN, st.phase)
        assertEquals(0, st.daysUntilDeadline)
    }

    @Test fun moosePassedAfterDeadline() {
        val st = deadlineStatus(moose(), LocalDate.of(2026, 6, 1))
        assertEquals(DeadlinePhase.PASSED, st.phase)
    }

    @Test fun deerOpenInJuly() {
        // July 1 is inside the window (open Jun 25, deadline Aug 3).
        val st = deadlineStatus(deer(), LocalDate.of(2026, 7, 1))
        assertEquals(DeadlinePhase.OPEN, st.phase)
    }

    @Test fun deerPassedInSeptember() {
        // Today's real context: both windows are already closed for 2026.
        assertEquals(DeadlinePhase.PASSED, deadlineStatus(deer(), LocalDate.of(2026, 9, 6)).phase)
        assertEquals(DeadlinePhase.PASSED, deadlineStatus(moose(), LocalDate.of(2026, 9, 6)).phase)
    }

    @Test fun passedHeadlinePointsToNextCycle() {
        val d = deer()
        val line = deadlineStatus(d, LocalDate.of(2026, 9, 6)).headline(d.cycleYear, d.typicalWindow)
        assertTrue(line.contains("2027"))
    }
}
