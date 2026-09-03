package com.kairos

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kairos.ui.KairosApp
import com.kairos.ui.KairosColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Restore the saved theme before first composition (light-first by default).
        val prefs = getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
        KairosColors.dark = prefs.getBoolean(THEME_KEY_DARK, false)
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
    }
}
