package com.kairos.engine

import kotlin.math.roundToInt

/**
 * Water temperature — the input fish actually respond to, done TIERED and honest.
 *
 * The free weather feed only gives AIR temp, so historically Kairos used one flat
 * monthly Sebago proxy everywhere (see [SEBAGO_WATER_F]). That was the biggest
 * fishing-accuracy gap. This replaces it with a tiered resolver that prefers real
 * measured data and clearly LABELS anything it has to estimate:
 *
 *  1. [WaterTempTier.USER]     — a reading the user punched in from a thermometer
 *     (best for the small pond they're standing on), while it's still fresh.
 *  2. [WaterTempTier.GAUGE]    — a real USGS/NOAA sensor on/near the water (see
 *     [com.kairos.data.UsgsWater]); measured, cited, with distance + age disclosed.
 *  3. [WaterTempTier.SATELLITE]— measured lake-surface temp from satellite (broad
 *     but coarse). Not yet wired — no free point-query API found; kept for honesty.
 *  4. [WaterTempTier.ESTIMATE] — a labeled estimate: the seasonal water normal
 *     nudged by how far recent air temp sits from its seasonal normal ([estimate]).
 *
 * The pure [resolve] takes already-fetched inputs so it is fully unit-testable; the
 * network fetch (USGS) and the user reading are supplied by the app/data layer.
 */
enum class WaterTempTier { USER, GAUGE, SATELLITE, ESTIMATE }

/**
 * A resolved water temperature plus enough provenance for the UI to be honest about
 * where it came from. [estimated] is true only for [WaterTempTier.ESTIMATE]; real
 * measurements (user/gauge/satellite) set it false.
 */
data class WaterTempReading(
    val tempF: Double,
    val tier: WaterTempTier,
    /** Short source label for the UI, e.g. "USGS gauge" or "Your reading". */
    val label: String,
    /** One-line honesty note, e.g. "Measured 2 h ago, 4 mi away" or "Estimated". */
    val detail: String,
) {
    val estimated: Boolean get() = tier == WaterTempTier.ESTIMATE
    val tempRoundedF: Int get() = tempF.roundToInt()
}

/**
 * A user-entered water-temperature reading, bridged from the app's prefs into the
 * engine the same way [SpeciesFilter] is: a plain @Volatile holder the UI writes
 * through, so background scoring (the timing hero, the Wear tile) uses it too.
 * null = no reading on file. A reading older than [FRESH_DAYS] days is ignored.
 */
object WaterUserReading {
    /** How long a hand-entered reading is trusted before it goes stale (days). */
    const val FRESH_DAYS = 3L

    @Volatile
    var tempF: Double? = null

    @Volatile
    var enteredAtEpochMs: Long = 0L

    fun set(tempF: Double?, atEpochMs: Long) {
        this.tempF = tempF
        this.enteredAtEpochMs = atEpochMs
    }

    fun clear() {
        tempF = null
        enteredAtEpochMs = 0L
    }

    /** Age in whole days as of [nowEpochMs], or null if there is no reading on file. */
    fun ageDays(nowEpochMs: Long): Long? {
        if (tempF == null || enteredAtEpochMs <= 0L) return null
        val ms = (nowEpochMs - enteredAtEpochMs).coerceAtLeast(0L)
        return ms / 86_400_000L
    }

    /** The reading if it is present and still within [FRESH_DAYS], else null. */
    fun freshTempF(nowEpochMs: Long): Double? {
        val t = tempF ?: return null
        val age = ageDays(nowEpochMs) ?: return null
        return if (age <= FRESH_DAYS) t else null
    }
}

/**
 * Maine (Portland-area) monthly NORMAL air temperature, °F — the "typical" air for
 * each month. Used only to measure how anomalously warm/cold the recent air has
 * been, which is what nudges the water estimate off its seasonal baseline. Approx.
 * NOAA 1991–2020 normals for Portland, ME (KPWM); cited in SOURCES.md. Keyed 1..12.
 */
val MAINE_AIR_NORMAL_F: Map<Int, Int> = mapOf(
    1 to 23, 2 to 26, 3 to 34, 4 to 44, 5 to 54, 6 to 63,
    7 to 69, 8 to 68, 9 to 60, 10 to 49, 11 to 39, 12 to 29,
)

// Estimate tuning. A deep lake's surface tracks a SUSTAINED air anomaly slowly and
// partially, so nudge the seasonal water normal by a damped, clamped fraction of the
// recent air anomaly. Heuristic (labeled an estimate), not a fitted coefficient.
private const val ESTIMATE_DAMP = 0.35
private const val ESTIMATE_MAX_NUDGE_F = 6.0

/**
 * The labeled fallback estimate: the month's seasonal water normal, nudged by how
 * far the recent multi-day average air temperature ([recentAvgAirF]) sits from that
 * month's normal air. A warm spell nudges it up, a cold snap down, both damped and
 * clamped so an afternoon can never claim the lake jumped several degrees.
 *
 * Smarter than the old flat monthly constant, but still an estimate — always tier
 * [WaterTempTier.ESTIMATE].
 */
fun estimate(month: Int, recentAvgAirF: Double?): WaterTempReading {
    val baseline = SEBAGO_WATER_F.getValue(month).toDouble()
    val nudge = if (recentAvgAirF != null) {
        val normalAir = MAINE_AIR_NORMAL_F.getValue(month).toDouble()
        (ESTIMATE_DAMP * (recentAvgAirF - normalAir))
            .coerceIn(-ESTIMATE_MAX_NUDGE_F, ESTIMATE_MAX_NUDGE_F)
    } else 0.0
    val f = (baseline + nudge).roundToInt().toDouble()
    val detail = if (recentAvgAirF != null && kotlin.math.abs(nudge) >= 0.5) {
        "Estimated from seasonal normal + recent air"
    } else {
        "Estimated from seasonal normal"
    }
    return WaterTempReading(f, WaterTempTier.ESTIMATE, "Estimate", detail)
}

/**
 * Pick the best available water temperature, most-trusted tier first:
 * fresh user reading → gauge → satellite → estimate. Pure: the caller supplies the
 * already-fetched [gauge] (null if none/too far/stale) and the current [userReading]
 * (already freshness-checked), and this resolves the rest from [month] +
 * [recentAvgAirF]. Never returns null — the estimate is always available.
 */
fun resolve(
    month: Int,
    recentAvgAirF: Double?,
    userReading: WaterTempReading? = null,
    gauge: WaterTempReading? = null,
    satellite: WaterTempReading? = null,
): WaterTempReading =
    userReading ?: gauge ?: satellite ?: estimate(month, recentAvgAirF)
