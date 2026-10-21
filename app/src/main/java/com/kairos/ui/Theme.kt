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
import androidx.compose.ui.unit.dp

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
    // Ambient color grading: the screen wash leans faintly teal at top, fern at
    // bottom (blue + green in balance); hero cards carry a subtle teal-graded tint
    // so the app isn't flat white. All kept very low-chroma — accents, not floods.
    val washTop: Color,
    val washBottom: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val segTop: Color,
    val segBottom: Color,
    val onSeg: Color,
    val error: Color,
    /** Elevation shadow tint — a cool deep tone on light; black (invisible) on dark. */
    val shadowSpot: Color,
)

/**
 * The Kairos brand colors — the ONLY hues used anywhere in the UI. Every palette
 * role below is one of these, a lightness step of one (for light/dark contrast,
 * always on the same hue — annotated), or a step of the warm-neutral base ramp.
 * Nothing is eyeballed off-palette, so the whole app is on-brand by construction.
 * (Charts use a separate reserved set — see [ChartColors] — the one documented
 * exception, because two data series need a colorblind-safe pair.)
 */
private object Brand {
    // Warm-neutral base ramp: White Smoke ↔ Carbon Black. Carries every surface,
    // text, and hairline; steps of it are the only "grays".
    val Smoke = Color(0xFFF5F5F4)
    val Carbon = Color(0xFF202321)
    // Brand primaries — the ONLY accent hues. Target on-screen balance is roughly
    // green ~40 / teal ~40 / amber ~20, so green (Fern) and teal (water) do most of
    // the accent work and amber stays a minority highlight; brick is reserved for
    // negatives. No "crisp" green/red — Fern is the up/Good/Prime green, Brick the
    // down/Poor red.
    val Fern = Color(0xFF566E3F)   // primary / Good / Prime / "up" / chrome
    val Teal = Color(0xFF074552)   // secondary — fish / links / active / timing / "now"
    val Amber = Color(0xFFDE8521)  // highlight / Fair
    val Brick = Color(0xFFB23A2E)  // Poor / error / "down"
}

private val LightPalette = Palette(
    bg = Brand.Smoke,            // White Smoke — the ground
    bgTop = Color(0xFFFAFAF9),   // Smoke +1 step, for a near-flat radial wash
    surface = Color(0xFFFFFFFF), // white (neutral) cards on the smoke ground
    surface2 = Color(0xFFEEEEEC), // neutral ramp step
    line = Color(0x12202321),    // Carbon @ ~.07 — neutral hairline
    text = Color(0xFF1E201D),    // Carbon Black (neutral)
    dim = Color(0xFF5E605B),     // neutral ramp
    faint = Color(0xFF95968F),   // neutral ramp
    pine = Brand.Fern,           // Fern — brand/primary accent + Good/Prime/"up"
    water = Color(0xFF167C93),   // Brand.Teal, +2 steps (same hue) for accent legibility
    prime = Brand.Fern,          // Fern — top tier (no separate crisp green)
    good = Brand.Fern,           // Fern — Good rating
    fair = Brand.Amber,          // Amber Earth — Fair rating / highlight
    slow = Color(0xFF8B8C84),    // neutral warm gray — out-of-season / no-data
    cardTop = Color(0xFFFFFFFF), // emphasized card = neutral, hairline border
    cardBottom = Color(0xFFF7F7F5), // neutral ramp step
    cardBorder = Color(0x14202321), // Carbon @ ~.08 — neutral hairline
    washTop = Color(0xFFEDF2F3),    // Smoke + a whisper of teal (cool top)
    washBottom = Color(0xFFF1F3EC), // Smoke + a whisper of fern (warm-green bottom)
    heroTop = Color(0xFFEAF1F1),    // hero card: a faint teal hint — the shadow gives the lift
    heroBottom = Color(0xFFF4F6F3), // … close to neutral, so there's no loud gradient
    segTop = Color(0xFF5C7642),  // Fern +1 step — active nav/segment gradient (brand moment)
    segBottom = Color(0xFF445734), // Fern −1 step
    onSeg = Color(0xFFF3F2EF),   // near-white (neutral) text on the Fern segment
    error = Brand.Brick,         // Brick — Poor / error / "down"
    shadowSpot = Color(0xFF1B2B31), // cool deep teal-carbon — soft, designed shadow
)

// Dark theme is a WARM-NEUTRAL carbon — deliberately NOT green-tinted (the earlier
// green-black surfaces made everything read green). Greens appear only as accents:
// Fern for chrome (active nav/segments/brand) and Good, crisp green for Prime.
private val DarkPalette = Palette(
    bg = Color(0xFF19181A),      // warm-neutral carbon ground (neutral ramp, no green)
    bgTop = Color(0xFF201F21),   // Carbon (neutral) — top of the near-flat wash
    surface = Color(0xFF232224), // neutral dark-gray card
    surface2 = Color(0xFF2C2B2E), // neutral ramp step
    line = Color(0x1AF1F0F2),    // near-white @ ~.10 — neutral hairline
    text = Color(0xFFECEBEC),    // near-white (neutral)
    dim = Color(0xFFA8A7A9),     // neutral ramp
    faint = Color(0xFF767579),   // neutral ramp
    pine = Color(0xFF8CB06B),    // Brand.Fern, lifted for contrast on carbon (same hue)
    water = Color(0xFF57A6BA),   // Brand.Teal, lifted (same hue)
    prime = Color(0xFF8CB06B),   // Brand.Fern, lifted — top tier (no separate crisp green)
    good = Color(0xFF8CB06B),    // Brand.Fern, lifted — Good rating
    fair = Color(0xFFE89A45),    // Brand.Amber, lifted (same hue)
    slow = Color(0xFF86857F),    // neutral ramp
    cardTop = Color(0xFF262528), // emphasized card = neutral, hairline border
    cardBottom = Color(0xFF1D1C1E), // neutral ramp step
    cardBorder = Color(0x1FF1F0F2), // neutral hairline @ ~.12
    washTop = Color(0xFF171C1E),    // Carbon + a whisper of teal (cool top)
    washBottom = Color(0xFF1A1B17), // Carbon + a whisper of fern (warm-green bottom)
    heroTop = Color(0xFF1E2528),    // hero card: faint teal grade …
    heroBottom = Color(0xFF1D1C1E), // … fading to near-neutral
    segTop = Color(0xFF4E6A37),  // Brand.Fern step — active nav/segment gradient (brand moment)
    segBottom = Color(0xFF3A5029), // Brand.Fern, darker step
    onSeg = Color(0xFFF1F1EE),   // near-white (neutral) text on the Fern segment
    error = Color(0xFFE1614E),   // Brand.Brick, lifted (same hue)
    shadowSpot = Color(0xFF000000), // black — shadows are ~invisible on the dark ground
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
    val WashTop get() = c.washTop
    val WashBottom get() = c.washBottom
    val HeroTop get() = c.heroTop
    val HeroBottom get() = c.heroBottom
    val SegTop get() = c.segTop
    val SegBottom get() = c.segBottom
    val OnSeg get() = c.onSeg
    val Error get() = c.error
    val ShadowSpot get() = c.shadowSpot
}

/**
 * Chart series colors: the two-series Trends chart needs a colorblind-safe pair, so
 * it uses the brand green (Fern, theme-aware) for the forecast line and a blue for
 * the recorded dots — green↔blue separates well under CVD, and shape reinforces it
 * (line vs dots). Blue appears only here as a data-series color. Grid, labels, and
 * the "today" reference line follow the active theme and stay recessive/neutral.
 */
object ChartColors {
    val Expected get() = KairosColors.Pine // brand green (Fern), theme-aware — forecast line
    val Actual get() = if (KairosColors.dark) Color(0xFF5B9BE8) else Color(0xFF2E7FD0) // blue — recorded
    val Grid get() = KairosColors.Line
    val Today get() = KairosColors.Faint // neutral, recessive reference line
    val Label get() = KairosColors.Dim
}

/**
 * Spacing scale (8pt-based) so the whole app shares one rhythm instead of ad-hoc
 * spacers. The intent: group related elements tightly ([xs]/[sm]), set sections
 * apart generously ([lg]/[section]). [screen] is the horizontal page gutter.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val section = 30.dp
    val screen = 20.dp
}

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.pine,
    onPrimary = Color(0xFF0E100E),   // neutral near-black
    secondary = DarkPalette.water,
    onSecondary = Color(0xFF0E100E), // neutral near-black
    background = DarkPalette.bg,
    onBackground = DarkPalette.text,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.text,
    surfaceVariant = DarkPalette.surface2,
    onSurfaceVariant = DarkPalette.dim,
    // Container roles kept NEUTRAL carbon (was green-tinted) so bare Cards never
    // pull in Material's default purple tint — and never add a green cast either.
    surfaceTint = DarkPalette.surface,
    surfaceContainerLowest = Color(0xFF131214),
    surfaceContainerLow = DarkPalette.surface,
    surfaceContainer = Color(0xFF1E1D1F),
    surfaceContainerHigh = DarkPalette.surface2,
    surfaceContainerHighest = Color(0xFF322F33),
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
    // Container roles + tint kept neutral so bare Cards never pull in Material's
    // default purple-tinted surfaces (the "light purple" box) or a green cast.
    surfaceTint = LightPalette.surface,
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

/**
 * The ambient screen wash: a soft vertical grade that leans faintly teal at the top
 * and faintly fern at the bottom, over the neutral ground — a little color so the
 * app isn't flat white, kept very low-chroma so it reads as atmosphere, not tint.
 */
fun screenBackground(): Brush = Brush.verticalGradient(
    colors = listOf(KairosColors.WashTop, KairosColors.Bg, KairosColors.WashBottom),
)

@Composable
fun KairosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (KairosColors.dark) DarkScheme else LightScheme,
        typography = KairosTypography,
        content = content,
    )
}
