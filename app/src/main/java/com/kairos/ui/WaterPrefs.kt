package com.kairos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kairos.engine.WaterUserReading

/**
 * The user's hand-entered water-temperature reading, observable by Compose. Mirrors
 * the [SpeciesPrefs] pattern: [MainActivity] restores the saved value before first
 * composition and supplies [onChange] to persist edits. Every write also updates the
 * engine's [WaterUserReading] holder so background scoring (the timing hero, the Wear
 * tile) uses the same reading.
 *
 * A reading is a temperature plus when it was taken. The engine ignores it once it's
 * older than [WaterUserReading.FRESH_DAYS], so a stale reading quietly hands back to a
 * gauge or the labeled estimate. null [tempF] = no reading on file.
 */
object WaterPrefs {
    var tempF by mutableStateOf<Double?>(null)
        private set
    var enteredAtEpochMs by mutableStateOf(0L)
        private set

    /** Persistence hook, set by [MainActivity]; receives (tempF, enteredAt) — null = cleared. */
    var onChange: ((Double?, Long) -> Unit)? = null

    /** Restore a saved reading at startup — does not fire [onChange]. */
    fun restore(tempF: Double?, atEpochMs: Long) {
        this.tempF = tempF
        this.enteredAtEpochMs = atEpochMs
        WaterUserReading.set(tempF, atEpochMs)
    }

    /** Save a new reading taken now. */
    fun set(tempF: Double, atEpochMs: Long = System.currentTimeMillis()) {
        this.tempF = tempF
        this.enteredAtEpochMs = atEpochMs
        WaterUserReading.set(tempF, atEpochMs)
        onChange?.invoke(tempF, atEpochMs)
    }

    fun clear() {
        tempF = null
        enteredAtEpochMs = 0L
        WaterUserReading.clear()
        onChange?.invoke(null, 0L)
    }

    /** True while the reading is still within the engine's freshness window. */
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        WaterUserReading.freshTempF(nowMs) != null
}
