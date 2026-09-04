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

// Reiden's brand palette (2026-09-03): a warm-neutral base (White Smoke ↔ Carbon
// Black) carries the whole app; the five primaries are used sparingly as ACCENTS.
// Fern = brand/primary; Amber Earth = highlight/CTA + Fair; Dark Teal = secondary
// (fish/support); Brick = Poor/error. The crisp app green + other chart hues are
// secondaries reserved for charts (see the chart palette), not general UI. Color
// is kept minimal — surfaces and chrome stay neutral so the accents read.
private val LightPalette = Palette(
    bg = Color(0xFFF5F5F4),      // White Smoke — the ground
    bgTop = Color(0xFFF8F8F7),   // barely-lighter, for a near-flat radial wash
    surface = Color(0xFFFFFFFF), // clean white cards on the smoke ground
    surface2 = Color(0xFFECECEA),
    line = Color(0x14202321),    // Carbon @ ~.08
    text = Color(0xFF202321),    // Carbon Black
    dim = Color(0xFF5E605B),
    faint = Color(0xFF93938C),
    pine = Color(0xFF566E3F),    // Fern — brand/primary accent
    water = Color(0xFF074552),   // Dark Teal — secondary accent (fish/support)
    prime = Color(0xFF566E3F),   // Fern — primary emphasis
    good = Color(0xFF566E3F),    // Fern — Good rating
    fair = Color(0xFFDE8521),    // Amber Earth — Fair rating / highlight
    slow = Color(0xFF8B8C84),    // warm gray — out-of-season / no-data
    cardTop = Color(0xFFFFFFFF), // emphasized card = neutral, thin Fern border
    cardBottom = Color(0xFFF6F6F4),
    cardBorder = Color(0x38566E3F), // Fern @ ~.22
    segTop = Color(0xFF5C7642),  // active nav/segment — Fern gradient
    segBottom = Color(0xFF445734),
    onSeg = Color(0xFFF2F4EC),
    error = Color(0xFFB23A2E),   // Brick — Poor / error
)

private val DarkPalette = Palette(
    bg = Color(0xFF181A17),      // deep carbon ground
    bgTop = Color(0xFF202321),   // Carbon Black — top of the wash
    surface = Color(0xFF232622),
    surface2 = Color(0xFF2C2F2A),
    line = Color(0x1FF1F1EE),    // near-white @ ~.12
    text = Color(0xFFF1F1EE),
    dim = Color(0xFFB2B3AB),
    faint = Color(0xFF7C7D75),
    pine = Color(0xFF86A866),    // Fern, lifted for contrast on carbon
    water = Color(0xFF4F9DB0),   // Dark Teal, lifted
    prime = Color(0xFF86A866),
    good = Color(0xFF86A866),
    fair = Color(0xFFE89A45),    // Amber, lifted
    slow = Color(0xFF83837C),
    cardTop = Color(0xFF242722),
    cardBottom = Color(0xFF1C1F1B),
    cardBorder = Color(0x4C86A866), // Fern @ ~.30
    segTop = Color(0xFF4A6635),
    segBottom = Color(0xFF354926),
    onSeg = Color(0xFFEFF3E8),
    error = Color(0xFFE1614E),   // Brick, lifted
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
    surfaceContainerLowest = Color(0xFF121411),
    surfaceContainerLow = DarkPalette.surface,
    surfaceContainer = Color(0xFF1F221D),
    surfaceContainerHigh = DarkPalette.surface2,
    surfaceContainerHighest = Color(0xFF2E312B),
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
    surfaceContainerLow = Color(0xFFF7F7F6),
    surfaceContainer = LightPalette.surface,
    surfaceContainerHigh = LightPalette.surface2,
    surfaceContainerHighest = Color(0xFFE3E3E0),
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
