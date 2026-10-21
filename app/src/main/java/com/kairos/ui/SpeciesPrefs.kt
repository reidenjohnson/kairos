package com.kairos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kairos.engine.SPECIES
import com.kairos.engine.Side
import com.kairos.engine.SpeciesFilter

/**
 * The user's species filter, observable by Compose. Mirrors the [KairosColors.dark]
 * pattern: [MainActivity] restores the saved set before first composition and supplies
 * [onChange] to persist edits. Every write also updates the engine's [SpeciesFilter]
 * so background scoring (the Today timing hero) honors the same choice.
 *
 * [enabled] == null means "no filter — all species on" (the default). That is distinct
 * from an empty set, which means the user turned everything off. When the user re-enables
 * everything we collapse back to null, so any species added in a future update default in.
 */
object SpeciesPrefs {
    var enabled by mutableStateOf<Set<String>?>(null)
        private set

    /** Persistence hook, set by [MainActivity]; receives the new set (null = all on). */
    var onChange: ((Set<String>?) -> Unit)? = null

    private val allNames: Set<String> get() = SPECIES.map { it.name }.toSet()

    /** Restore a saved selection at startup — does not fire [onChange]. */
    fun restore(names: Set<String>?) {
        enabled = names
        SpeciesFilter.enabledNames = names
    }

    private fun set(names: Set<String>?) {
        // Collapse a full set to null so the default stays "all" and future species opt in.
        val normalized = if (names != null && names == allNames) null else names
        enabled = normalized
        SpeciesFilter.enabledNames = normalized
        onChange?.invoke(normalized)
    }

    fun isEnabled(name: String): Boolean = enabled?.contains(name) ?: true

    /** Currently-enabled names as a concrete set (all of them when no filter is set). */
    private fun current(): Set<String> = enabled ?: allNames

    fun toggle(name: String) {
        val cur = current()
        set(if (name in cur) cur - name else cur + name)
    }

    /** Turn a whole side on or off at once. */
    fun setSide(side: Side, on: Boolean) {
        val sideNames = SPECIES.filter { it.side == side }.map { it.name }.toSet()
        set(if (on) current() + sideNames else current() - sideNames)
    }

    fun enableAll() = set(null)
    fun clearAll() = set(emptySet())

    /** True when the filter is at its default (everything visible). */
    fun isDefault(): Boolean = enabled == null
}
