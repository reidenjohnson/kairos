package com.kairos

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kairos.notify.NotificationScheduler
import com.kairos.notify.Notifications
import com.kairos.ui.KairosApp
import com.kairos.ui.KairosColors
import com.kairos.ui.NotifyPrefs
import com.kairos.ui.SpeciesPrefs
import com.kairos.ui.WaterPrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Restore the saved theme before first composition (light-first by default).
        val prefs = getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
        KairosColors.dark = prefs.getBoolean(THEME_KEY_DARK, false)

        // Restore the species filter (absent key = no filter, all species on). Then wire
        // the persistence hook: null clears the key, a set is stored verbatim.
        SpeciesPrefs.restore(
            if (prefs.contains(SPECIES_KEY_ENABLED)) {
                prefs.getStringSet(SPECIES_KEY_ENABLED, emptySet())?.toSet() ?: emptySet()
            } else {
                null
            },
        )
        SpeciesPrefs.onChange = { names ->
            prefs.edit().apply {
                if (names == null) remove(SPECIES_KEY_ENABLED) else putStringSet(SPECIES_KEY_ENABLED, names)
            }.apply()
        }

        // Restore the daily-reminders toggle, then persist + (re)schedule on change.
        Notifications.ensureChannel(this)
        NotifyPrefs.restore(prefs.getBoolean(NOTIFY_KEY_ENABLED, false))
        if (NotifyPrefs.enabled) NotificationScheduler.schedule(this)
        NotifyPrefs.onChange = { on ->
            prefs.edit().putBoolean(NOTIFY_KEY_ENABLED, on).apply()
            if (on) NotificationScheduler.schedule(this) else NotificationScheduler.cancel(this)
        }

        // Restore any hand-entered water-temp reading (temp + when it was taken), then
        // persist edits. The engine ages it out on its own once stale.
        val savedWater = if (prefs.contains(WATER_KEY_TEMP)) prefs.getFloat(WATER_KEY_TEMP, 0f).toDouble() else null
        WaterPrefs.restore(savedWater, prefs.getLong(WATER_KEY_AT, 0L))
        WaterPrefs.onChange = { tempF, at ->
            prefs.edit().apply {
                if (tempF == null) {
                    remove(WATER_KEY_TEMP); remove(WATER_KEY_AT)
                } else {
                    putFloat(WATER_KEY_TEMP, tempF.toFloat()); putLong(WATER_KEY_AT, at)
                }
            }.apply()
        }
        enableEdgeToEdge()
        setContent {
            KairosApp(
                onToggleTheme = { dark ->
                    KairosColors.dark = dark
                    prefs.edit().putBoolean(THEME_KEY_DARK, dark).apply()
                },
            )
        }
    }

    companion object {
        const val THEME_PREFS = "kairos_theme"
        const val THEME_KEY_DARK = "dark"
        const val SPECIES_KEY_ENABLED = "species_enabled"
        const val NOTIFY_KEY_ENABLED = "notify_enabled"
        const val WATER_KEY_TEMP = "water_temp_f"
        const val WATER_KEY_AT = "water_temp_at"
    }
}
