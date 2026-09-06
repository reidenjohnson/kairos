package com.kairos.notify

import android.content.Context
import com.kairos.MainActivity

/**
 * Reads the persisted settings straight from SharedPreferences, without depending on
 * the Compose-side [com.kairos.ui.NotifyPrefs] / [com.kairos.ui.SpeciesPrefs] being
 * initialized — WorkManager can run the job in a process where MainActivity never ran.
 * Uses the same prefs file and keys MainActivity writes.
 */
object NotifyPrefsBridge {
    private fun prefs(context: Context) =
        context.getSharedPreferences(MainActivity.THEME_PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean =
        prefs(context).getBoolean(MainActivity.NOTIFY_KEY_ENABLED, false)

    /** The saved species filter (null = no filter, all species on). */
    fun enabledSpecies(context: Context): Set<String>? {
        val p = prefs(context)
        return if (p.contains(MainActivity.SPECIES_KEY_ENABLED)) {
            p.getStringSet(MainActivity.SPECIES_KEY_ENABLED, emptySet())?.toSet() ?: emptySet()
        } else {
            null
        }
    }
}
