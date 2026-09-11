package com.kairos.ui

import android.content.Context

/**
 * Remembers the user's map choices — the base map and which overlays are toggled on — so they
 * stick across leaving the screen and restarting the app (they used to reset to defaults every
 * time the Map screen left composition). Small, synchronous SharedPreferences.
 */
internal object MapPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)

    private val DEFAULT_ENABLED = setOf("hunting-verified", "expanded-archery")

    fun loadBase(c: Context): BaseMap =
        runCatching { BaseMap.valueOf(prefs(c).getString("base", null) ?: "") }.getOrDefault(BaseMap.SHADED)

    fun saveBase(c: Context, b: BaseMap) {
        prefs(c).edit().putString("base", b.name).apply()
    }

    fun loadEnabled(c: Context): Set<String> =
        prefs(c).getStringSet("enabled", null)?.let { HashSet(it) } ?: DEFAULT_ENABLED

    fun saveEnabled(c: Context, s: Set<String>) {
        prefs(c).edit().putStringSet("enabled", HashSet(s)).apply()
    }
}
