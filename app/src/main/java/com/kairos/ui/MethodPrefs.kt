package com.kairos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kairos.engine.HuntMethod
import com.kairos.engine.MethodFilter

/**
 * The user's hunting-method subscription ("seasons you hunt"), observable by Compose.
 * Mirrors [SpeciesPrefs]: [MainActivity] restores the saved set before first composition
 * and supplies [onChange] to persist edits; every write also updates the engine's
 * [MethodFilter] so the Seasons screen (and anything else) filters to the same choice.
 *
 * null = no filter (every method on, the default). An empty set = the user turned them
 * all off. Collapsing a full set back to null keeps future-added methods opted-in.
 */
object MethodPrefs {
    var enabled by mutableStateOf<Set<HuntMethod>?>(null)
        private set

    var onChange: ((Set<HuntMethod>?) -> Unit)? = null

    private val all: Set<HuntMethod> get() = HuntMethod.entries.toSet()

    fun restore(methods: Set<HuntMethod>?) {
        enabled = methods
        MethodFilter.enabledMethods = methods
    }

    private fun set(methods: Set<HuntMethod>?) {
        val normalized = if (methods != null && methods == all) null else methods
        enabled = normalized
        MethodFilter.enabledMethods = normalized
        onChange?.invoke(normalized)
    }

    fun isEnabled(m: HuntMethod): Boolean = enabled?.contains(m) ?: true

    private fun current(): Set<HuntMethod> = enabled ?: all

    fun toggle(m: HuntMethod) {
        val cur = current()
        set(if (m in cur) cur - m else cur + m)
    }

    fun isDefault(): Boolean = enabled == null
}
