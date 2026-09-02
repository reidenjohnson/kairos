package com.kairos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Kairos palette — the approved redesign direction (see design/Main.dc.html): a
 * deep-pine dark theme with pine + water accents. The app commits to this single
 * dark look on purpose (it's an outdoors app used at dawn/dusk), so the scheme is
 * dark regardless of the system setting.
 */
object KairosColors {
    val Bg = Color(0xFF0E1512)
    val BgTop = Color(0xFF16211C)   // top of the radial background wash
    val Surface = Color(0xFF171F1B)
    val Surface2 = Color(0xFF1E2823)
    val Line = Color(0x12FFFFFF)    // rgba(255,255,255,.07)
    val Text = Color(0xFFE9EFEC)
    val Dim = Color(0xFF9AACA4)
    val Faint = Color(0xFF6A7D75)
    val Pine = Color(0xFF74C0A2)
    val Water = Color(0xFF62AED4)

    val Prime = Color(0xFF37A85C)
    val Good = Color(0xFF86B93F)
    val Fair = Color(0xFFD2A03A)
    val Slow = Color(0xFF7C8B84)

    // Emphasis card (best pick / "open now") gradient stops.
    val CardTop = Color(0xFF1C2A24)
    val CardBottom = Color(0xFF161E1A)
    val CardBorder = Color(0x4774C0A2) // pine @ ~.28

    // Selected segment / active nav gradient.
    val SegTop = Color(0xFF25574A)
    val SegBottom = Color(0xFF1C4339)
    val OnSeg = Color(0xFFEAFBF3)

    val Error = Color(0xFFE1614E)
}

private val KairosScheme = darkColorScheme(
    primary = KairosColors.Pine,
    onPrimary = Color(0xFF06120D),
    secondary = KairosColors.Water,
    onSecondary = Color(0xFF06121A),
    background = KairosColors.Bg,
    onBackground = KairosColors.Text,
    surface = KairosColors.Surface,
    onSurface = KairosColors.Text,
    surfaceVariant = KairosColors.Surface2,
    onSurfaceVariant = KairosColors.Dim,
    outline = KairosColors.Faint,
    outlineVariant = KairosColors.Line,
    error = KairosColors.Error,
    onError = Color(0xFF2A0A06),
)

/** The soft radial wash used behind every screen, matching the mockups. */
fun screenBackground(): Brush = Brush.radialGradient(
    colors = listOf(KairosColors.BgTop, KairosColors.Bg),
    radius = 1600f,
    center = androidx.compose.ui.geometry.Offset(540f, -120f),
)

@Composable
fun KairosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KairosScheme, content = content)
}
