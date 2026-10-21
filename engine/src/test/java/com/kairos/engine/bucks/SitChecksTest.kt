package com.kairos.engine.bucks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SitChecksTest {
    private fun c(met: Boolean?) = Check("x", met, "")

    @Test fun percentCountsOnlyChecksThatApply() {
        assertEquals(83, SitChecks.percent(listOf(c(true), c(true), c(true), c(true), c(true), c(false))))
        // His wind not set: 4 of 5, not 4 of 6.
        assertEquals(80, SitChecks.percent(listOf(c(null), c(true), c(true), c(true), c(true), c(false))))
        assertEquals(0, SitChecks.percent(listOf(c(null))))
    }

    @Test fun favorsMeansMoreVisitsThanTheWindsShareOfTime() {
        assertTrue(SitChecks.favors(5, 15, 100, 500))   // 33% of visits, blew 20% of the time
        assertFalse(SitChecks.favors(5, 15, 200, 500))  // 33% vs 40%: it just blows a lot
        assertFalse(SitChecks.favors(1, 2, 1, 500))     // one visit is chance
    }

    @Test fun goodIsThreeOfFourOrBetter() {
        assertTrue(SitChecks.good(listOf(c(true), c(true), c(true), c(false))))                       // 75
        assertTrue(SitChecks.good(listOf(c(true), c(true), c(true), c(true), c(false))))              // 80
        assertFalse(SitChecks.good(listOf(c(true), c(true), c(true), c(true), c(false), c(false))))   // 67
    }
}
