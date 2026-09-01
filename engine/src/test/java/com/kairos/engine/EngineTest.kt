package com.kairos.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end parity for the scoring engine. The fixed scenario below was run
 * through forecast.py; the expected raw totals and 0..100 scores are its output.
 */
class EngineTest {

    // A cold-front autumn morning at Sebago (matches gen_golden.py).
    private val conditions = Conditions(
        airF = 41.0,
        waterF = 57.0,
        windMph = 11.0,
        cloudPct = 80.0,
        pressureInHg = 30.02,
        pressureTrendInHg = -0.06,
        tempDropNext24hF = 8.0,
        moonIllum = 0.5,
    )

    // name -> (raw total, rounded percent) from the Python reference.
    private val golden = mapOf(
        "Whitetail deer" to (0.866730 to 87),
        "Moose" to (0.873919 to 87),
        "Elk" to (0.880019 to 88),
        "Black bear" to (0.838106 to 84),
        "Snowshoe hare" to (0.593939 to 59),
        "Upland birds" to (0.692798 to 69),
        "Waterfowl" to (0.800020 to 80),
        "Largemouth bass" to (0.644424 to 64),
        "Smallmouth bass" to (0.658799 to 66),
        "Salmon / togue / brookie" to (0.891045 to 89),
        "Walleye" to (0.729515 to 73),
    )

    @Test fun rawTotalsMatchReference() {
        for (sp in SPECIES) {
            val (expected, _) = golden.getValue(sp.name)
            assertEquals(sp.name, expected, score(sp, conditions), 1e-6)
        }
    }

    @Test fun percentScoresMatchReference() {
        for (sp in SPECIES) {
            val (_, expected) = golden.getValue(sp.name)
            assertEquals(sp.name, expected, scorePercent(sp, conditions))
        }
    }

    @Test fun weightsSumToOne() {
        for (sp in SPECIES) {
            val w = sp.weights
            val sum = w.temp + w.trend + w.range + w.front + w.wind + w.cloud + w.moon
            assertEquals(sp.name, 1.0, sum, 1e-9)
        }
    }

    @Test fun coversElevenSpecies() {
        assertEquals(11, SPECIES.size)
        assertEquals(7, SPECIES.count { it.side == Side.HUNT })
        assertEquals(4, SPECIES.count { it.side == Side.FISH })
    }

    @Test fun scoreAllSortsBestFirstPerSide() {
        val hunt = scoreAll(conditions, Side.HUNT)
        assertEquals(7, hunt.size)
        assertTrue(hunt.all { it.species.side == Side.HUNT })
        assertEquals(hunt.map { it.percent }.sortedDescending(), hunt.map { it.percent })
        // Elk is the top hunt score in this scenario.
        assertEquals("Elk", hunt.first().species.name)
    }

    @Test fun ratingBuckets() {
        assertEquals(Rating.PRIME, rating(75))
        assertEquals(Rating.GOOD, rating(55))
        assertEquals(Rating.FAIR, rating(40))
        assertEquals(Rating.SLOW, rating(39))
    }
}
