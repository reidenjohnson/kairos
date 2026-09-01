package com.kairos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A calm outdoors palette — deep pine + water blue. Swap freely later.
private val Pine = Color(0xFF2E5E4E)
private val PineLight = Color(0xFF8FC7B3)
private val Water = Color(0xFF2C6E8F)

private val LightColors = lightColorScheme(
    primary = Pine,
    secondary = Water,
)

private val DarkColors = darkColorScheme(
    primary = PineLight,
    secondary = Color(0xFF7FB6D2),
)

@Composable
fun KairosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
