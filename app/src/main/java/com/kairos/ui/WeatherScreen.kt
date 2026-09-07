package com.kairos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.data.Forecast
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The full weather read — the "Details" behind the Today weather card. Consolidates
 * everything the engine already fetches (air, water, pressure + trend, wind, cloud,
 * the 24h cold-front drop, rain, sun times + legal light, moon) into one honest place,
 * including the water-temp-proxy caveat.
 */
@Composable
fun WeatherScreen(state: UiState) {
    val ready = state as? UiState.Ready
    if (ready == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No forecast yet", color = KairosColors.Dim) }
        return
    }
    val f = ready.forecast
    val fmt = DateTimeFormatter.ofPattern("h:mm a")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Spacer(Modifier.height(Space.xs)) }
        item {
            Column {
                Overline("Weather", color = KairosColors.Water)
                Spacer(Modifier.height(Space.xs))
                Text(
                    f.placeLabel,
                    fontFamily = Bricolage,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp,
                    lineHeight = 34.sp,
                    color = KairosColors.Text,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "${if (ready.live) "Updated" else "Cached"} ${timeText(ready.savedAtMillis)} · ${f.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KairosColors.Faint,
                )
            }
        }

        // Big current temperature.
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${f.airF.roundToInt()}°",
                    fontFamily = Bricolage,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp,
                    lineHeight = 64.sp,
                    color = KairosColors.Text,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "  air temp",
                    style = MaterialTheme.typography.titleMedium,
                    color = KairosColors.Dim,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        item { SectionTick("Conditions") }
        item {
            MetricGrid(
                listOf(
                    "%.2f\"".format(f.pressureInHg) to "${trendArrow(f.pressureTrendInHg)} pressure ${f.trendWord}",
                    "${f.windMph.roundToInt()} mph" to "wind",
                    "−${f.tempDropNext24hF.roundToInt()}°" to "cold front, next 24h",
                    "${f.cloudPct.roundToInt()}%" to "cloud cover",
                    waterValueLabel(f) to "water temp${waterTierSuffix(f)}",
                    (if (f.precipMmHr > 0) "%.1f mm/h".format(f.precipMmHr) else "None") to "rain now",
                ),
            )
        }

        item { SectionTick("Sun & moon") }
        item {
            InfoCard {
                f.legalShootingHours?.let { hours ->
                    KeyValue("Legal shooting hours", "${fmt.format(hours.first)} – ${fmt.format(hours.second)}")
                }
                f.sunriseTime?.let { KeyValue("Sunrise", fmt.format(it)) }
                f.sunsetTime?.let { KeyValue("Sunset", fmt.format(it)) }
                KeyValue("Moon", f.moonName)
            }
        }

        item { SectionTick("Water temperature") }
        item {
            InfoCard {
                KeyValue(waterSourceTitle(f), "${f.waterF.roundToInt()}°")
                Text(
                    waterSourceNote(f),
                    style = MaterialTheme.typography.labelSmall,
                    color = KairosColors.Faint,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        item { Spacer(Modifier.height(Space.lg)) }
    }
}

@Composable
private fun SectionTick(text: String) {
    Row(modifier = Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .height(12.dp)
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(KairosColors.Water),
        )
        Spacer(Modifier.width(8.dp))
        Overline(text, color = KairosColors.Dim)
    }
}

@Composable
private fun MetricGrid(items: List<Pair<String, String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp),
    ) {
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                pair.forEach { (value, label) ->
                    Column(Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = KairosColors.Text)
    }
}

internal fun trendArrow(trendInHg: Double): String = when {
    trendInHg < -0.01 -> "↓"
    trendInHg > 0.01 -> "↑"
    else -> "→"
}

// --- Water-temperature provenance (tiered: user reading → USGS gauge → estimate) ---

/** The metric value, with a "~" only on an estimate to signal it isn't measured. */
private fun waterValueLabel(f: Forecast): String {
    val prefix = if (f.waterTemp?.estimated != false) "~" else ""
    return "$prefix${f.waterF.roundToInt()}°"
}

/** A short suffix on the metric label naming the tier: (yours) / (gauge) / (est.). */
private fun waterTierSuffix(f: Forecast): String = when (f.waterTemp?.tier) {
    com.kairos.engine.WaterTempTier.USER -> " (yours)"
    com.kairos.engine.WaterTempTier.GAUGE -> " (gauge)"
    com.kairos.engine.WaterTempTier.SATELLITE -> " (satellite)"
    else -> " (est.)"
}

private fun waterSourceTitle(f: Forecast): String = f.waterTemp?.label ?: "Estimate"

/** One honest line about where the water number came from + how to improve it. */
private fun waterSourceNote(f: Forecast): String = when (f.waterTemp?.tier) {
    com.kairos.engine.WaterTempTier.USER ->
        "${f.waterTemp!!.detail}. Kairos trusts your reading for a few days, then falls back to a " +
            "nearby gauge or an estimate. Update it in Settings when you take a fresh reading."
    com.kairos.engine.WaterTempTier.GAUGE ->
        "${f.waterTemp!!.detail}. A live USGS sensor reading — the most accurate source when a gauge " +
            "covers your water. Standing on a different pond? Enter your own reading in Settings."
    com.kairos.engine.WaterTempTier.SATELLITE ->
        "${f.waterTemp!!.detail}. A measured satellite surface temp."
    else ->
        "Estimated from the seasonal water normal nudged by recent air temperature — not a live reading, " +
            "since the free weather feed gives air temp only. For real accuracy, enter a thermometer reading " +
            "in Settings; Kairos also uses a nearby USGS gauge automatically when one covers your water."
}
