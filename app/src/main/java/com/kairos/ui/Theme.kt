package com.kairos.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Kairos palette. The brand is **light-first** (clean/calm for daylight use) with a
 * **dark-mode toggle** (deep pine, for dawn/dusk). Every screen reads `KairosColors.X`;
 * those are getters over the active [Palette], so flipping [KairosColors.dark] recolors
 * the whole app. Accents (pine/water + the rating colors) are semantic in both themes,
 * tuned for contrast on each background.
 */
private data class Palette(
    val bg: Color,
    val bgTop: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val pine: Color,
    val water: Color,
    val prime: Color,
    val good: Color,
    val fair: Color,
    val slow: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardBorder: Color,
    val segTop: Color,
    val segBottom: Color,
    val onSeg: Color,
    val error: Color,
)

private val LightPalette = Palette(
    bg = Color(0xFFFFFFFF),
    bgTop = Color(0xFFFFFFFF),
    surface = Color(0xFFF3F5F2),
    surface2 = Color(0xFFEAEEEA),
    line = Color(0x11000000), // rgba(0,0,0,.067)
    text = Color(0xFF14201B),
    dim = Color(0xFF566159),
    faint = Color(0xFF8B978F),
    pine = Color(0xFF2E6B52),
    water = Color(0xFF2C7DA8),
    prime = Color(0xFF2E9E54),
    good = Color(0xFF5E9A2E),
    fair = Color(0xFFB9862A),
    slow = Color(0xFF7E8B84),
    cardTop = Color(0xFFEAF3EE),
    cardBottom = Color(0xFFF7FAF8),
    cardBorder = Color(0x332E6B52), // pine @ ~.20
    segTop = Color(0xFF2E5E4E),
    segBottom = Color(0xFF3B9E6E),
    onSeg = Color(0xFFEAFBF3),
    error = Color(0xFFC0402E),
)

private val DarkPalette = Palette(
    bg = Color(0xFF0E1512),
    bgTop = Color(0xFF16211C),
    surface = Color(0xFF171F1B),
    surface2 = Color(0xFF1E2823),
    line = Color(0x12FFFFFF), // rgba(255,255,255,.07)
    text = Color(0xFFE9EFEC),
    dim = Color(0xFF9AACA4),
    faint = Color(0xFF6A7D75),
    pine = Color(0xFF74C0A2),
    water = Color(0xFF62AED4),
    prime = Color(0xFF37A85C),
    good = Color(0xFF86B93F),
    fair = Color(0xFFD2A03A),
    slow = Color(0xFF7C8B84),
    cardTop = Color(0xFF1C2A24),
    cardBottom = Color(0xFF161E1A),
    cardBorder = Color(0x4774C0A2), // pine @ ~.28
    segTop = Color(0xFF25574A),
    segBottom = Color(0xFF1C4339),
    onSeg = Color(0xFFEAFBF3),
    error = Color(0xFFE1614E),
)

object KairosColors {
    /** Active theme. Light-first by default; [MainActivity] restores the saved choice. */
    var dark by mutableStateOf(false)

    private val c: Palette get() = if (dark) DarkPalette else LightPalette

    val Bg get() = c.bg
    val BgTop get() = c.bgTop
    val Surface get() = c.surface
    val Surface2 get() = c.surface2
    val Line get() = c.line
    val Text get() = c.text
    val Dim get() = c.dim
    val Faint get() = c.faint
    val Pine get() = c.pine
    val Water get() = c.water
    val Prime get() = c.prime
    val Good get() = c.good
    val Fair get() = c.fair
    val Slow get() = c.slow
    val CardTop get() = c.cardTop
    val CardBottom get() = c.cardBottom
    val CardBorder get() = c.cardBorder
    val SegTop get() = c.segTop
    val SegBottom get() = c.segBottom
    val OnSeg get() = c.onSeg
    val Error get() = c.error
}

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.pine,
    onPrimary = Color(0xFF06120D),
    secondary = DarkPalette.water,
    onSecondary = Color(0xFF06121A),
    background = DarkPalette.bg,
    onBackground = DarkPalette.text,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.text,
    surfaceVariant = DarkPalette.surface2,
    onSurfaceVariant = DarkPalette.dim,
    // Container roles + tint kept in the pine family so bare Cards never pull
    // in Material's default purple-tinted surfaces.
    surfaceTint = DarkPalette.pine,
    surfaceContainerLowest = Color(0xFF0B110E),
    surfaceContainerLow = DarkPalette.surface,
    surfaceContainer = Color(0xFF1A231E),
    surfaceContainerHigh = DarkPalette.surface2,
    surfaceContainerHighest = Color(0xFF243029),
    outline = DarkPalette.faint,
    outlineVariant = DarkPalette.line,
    error = DarkPalette.error,
    onError = Color(0xFF2A0A06),
)

private val LightScheme = lightColorScheme(
    primary = LightPalette.pine,
    onPrimary = Color(0xFFFFFFFF),
    secondary = LightPalette.water,
    onSecondary = Color(0xFFFFFFFF),
    background = LightPalette.bg,
    onBackground = LightPalette.text,
    surface = LightPalette.surface,
    onSurface = LightPalette.text,
    surfaceVariant = LightPalette.surface2,
    onSurfaceVariant = LightPalette.dim,
    // Container roles + tint kept neutral/pine so bare Cards never pull in
    // Material's default purple-tinted surfaces (the "light purple" box).
    surfaceTint = LightPalette.pine,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9F6),
    surfaceContainer = LightPalette.surface,
    surfaceContainerHigh = LightPalette.surface2,
    surfaceContainerHighest = Color(0xFFE3E8E3),
    outline = LightPalette.faint,
    outlineVariant = LightPalette.line,
    error = LightPalette.error,
    onError = Color(0xFFFFFFFF),
)

/** The soft radial wash used behind every screen, matching the mockups. */
fun screenBackground(): Brush = Brush.radialGradient(
    colors = listOf(KairosColors.BgTop, KairosColors.Bg),
    radius = 1600f,
    center = Offset(540f, -120f),
)

@Composable
fun KairosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (KairosColors.dark) DarkScheme else LightScheme,
        typography = KairosTypography,
        content = content,
    )
}
