package com.kairos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the daily reminder notifications are on, observable by Compose. Mirrors the
 * [SpeciesPrefs] pattern: [MainActivity] restores the saved value before first
 * composition and supplies [onChange] to persist edits and (re)schedule the batched
 * WorkManager job. Off by default — the user opts in from Settings.
 */
object NotifyPrefs {
    var enabled by mutableStateOf(false)
        private set

    /** Persistence + scheduling hook, set by [MainActivity]; receives the new value. */
    var onChange: ((Boolean) -> Unit)? = null

    /** Restore the saved value at startup — does not fire [onChange]. */
    fun restore(on: Boolean) {
        enabled = on
    }

    fun set(on: Boolean) {
        enabled = on
        onChange?.invoke(on)
    }
}
