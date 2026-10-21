package com.kairos.notify

import android.content.Context
import com.kairos.data.Place
import com.kairos.MainActivity

/**
 * Reads the persisted settings straight from SharedPreferences, without depending on
 * the Compose-side [com.kairos.ui.NotifyPrefs] / [com.kairos.ui.SpeciesPrefs] being
 * initialized — the reminder runs in a process where MainActivity never ran. Uses the
 * same prefs file and keys MainActivity writes.
 */
object NotifyPrefsBridge {
    private const val KEY_LAT = "last_lat"
    private const val KEY_LON = "last_lon"
    private const val KEY_LABEL = "last_label"

    private fun prefs(context: Context) =
        context.getSharedPreferences(MainActivity.THEME_PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean =
        prefs(context).getBoolean(MainActivity.NOTIFY_KEY_ENABLED, false)

    /** Remember where the app last looked, so the daily reminder can forecast that spot
     *  fast — without waiting on a fresh GPS fix (too slow for the alarm's short window). */
    fun saveLastPlace(context: Context, place: Place) {
        prefs(context).edit()
            .putString(KEY_LAT, place.lat.toString())
            .putString(KEY_LON, place.lon.toString())
            .putString(KEY_LABEL, place.label)
            .apply()
    }

    fun lastPlace(context: Context): Place? {
        val p = prefs(context)
        val lat = p.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = p.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        return Place(lat, lon, p.getString(KEY_LABEL, null) ?: "your area")
    }

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
