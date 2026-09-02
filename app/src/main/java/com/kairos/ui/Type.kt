package com.kairos.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.kairos.R

/**
 * The mockup typefaces, bundled for offline use: Bricolage Grotesque (display /
 * numbers) + Hanken Grotesk (body). Both ship as variable fonts, so each weight
 * pins the `wght` axis. minSdk 30 comfortably supports variable-font settings.
 */
private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun hanken(weight: Int) = Font(
    R.font.hanken_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Display / numeric family — used for headings, species names, and scores. */
internal val Bricolage = FontFamily(bricolage(500), bricolage(700), bricolage(800))

/** Body / UI family. */
internal val Hanken = FontFamily(hanken(400), hanken(500), hanken(600), hanken(700))

/** Material typography with Bricolage on display/headings and Hanken elsewhere. */
val KairosTypography: Typography = Typography().let { d ->
    d.copy(
        displayLarge = d.displayLarge.copy(fontFamily = Bricolage),
        displayMedium = d.displayMedium.copy(fontFamily = Bricolage),
        displaySmall = d.displaySmall.copy(fontFamily = Bricolage),
        headlineLarge = d.headlineLarge.copy(fontFamily = Bricolage),
        headlineMedium = d.headlineMedium.copy(fontFamily = Bricolage),
        headlineSmall = d.headlineSmall.copy(fontFamily = Bricolage),
        titleLarge = d.titleLarge.copy(fontFamily = Bricolage),
        titleMedium = d.titleMedium.copy(fontFamily = Bricolage),
        titleSmall = d.titleSmall.copy(fontFamily = Hanken),
        bodyLarge = d.bodyLarge.copy(fontFamily = Hanken),
        bodyMedium = d.bodyMedium.copy(fontFamily = Hanken),
        bodySmall = d.bodySmall.copy(fontFamily = Hanken),
        labelLarge = d.labelLarge.copy(fontFamily = Hanken),
        labelMedium = d.labelMedium.copy(fontFamily = Hanken),
        labelSmall = d.labelSmall.copy(fontFamily = Hanken),
    )
}
