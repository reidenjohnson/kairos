package com.kairos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.advice.GamePlan
import com.kairos.advice.buildSidePlan
import com.kairos.data.Forecast
import com.kairos.engine.Conditions
import com.kairos.engine.Rating
import com.kairos.engine.SeasonStatusKind
import com.kairos.engine.Side
import com.kairos.engine.SpeciesScore
import com.kairos.engine.scoreAll
import com.kairos.engine.seasonStatus
import com.kairos.engine.seasonsFor
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The "Today" screen (Today's Best / Hunt / Fish, chosen by [sideFilter]). Shows
 * current conditions as chips, a segmented Best/Hunt/Fish control, then scores
 * best-first with the top pick emphasized and out-of-season species grouped below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: UiState,
    sideFilter: Side?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSelectSide: (Side?) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenSidePlan: (Side) -> Unit,
) {
    when (state) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { ErrorView(state.message, onRefresh) }
        is UiState.Ready -> PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            ForecastList(state, sideFilter, refreshing, onSelectSide, onOpenDetail, onOpenSidePlan)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

private val today: LocalDate get() = LocalDate.now()

/** In-season or upcoming (or no season table) → shown in the main list; else grouped below. */
private fun isPrimary(speciesName: String): Boolean {
    val s = seasonsFor(speciesName) ?: return true
    val kind = seasonStatus(s, today).kind
    return kind == SeasonStatusKind.OPEN || kind == SeasonStatusKind.UPCOMING
}

@Composable
private fun ForecastList(
    ready: UiState.Ready,
    sideFilter: Side?,
    refreshing: Boolean,
    onSelectSide: (Side?) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenSidePlan: (Side) -> Unit,
) {
    val forecast = ready.forecast
    val c = forecast.conditions
    val scored = remember(forecast, sideFilter) { scoreAll(c, sideFilter) }
    val primary = scored.filter { isPrimary(it.species.name) }
    val secondary = scored.filter { !isPrimary(it.species.name) }
    val openCount = remember(forecast, sideFilter) {
        scored.count { seasonsFor(it.species.name)?.let { s -> seasonStatus(s, today).kind == SeasonStatusKind.OPEN } == true }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Spacer(Modifier.height(Space.xs)) }
        if (!ready.live && !refreshing) item { OfflineBanner(ready.savedAtMillis) }
        // Header + conditions read as one context block (tight), set apart from the controls below.
        item {
            Column {
                Header(forecast, ready.savedAtMillis, ready.live)
                Spacer(Modifier.height(Space.md))
                ConditionChips(forecast)
            }
        }
        item { SegmentedControl(sideFilter, onSelectSide) }
        forecast.timing?.let { t -> item { TimingCard(t, sideFilter) } }
        if (sideFilter != Side.FISH && forecast.legalShootingHours != null) {
            item { LegalLightCard(forecast) }
        }
        // General "where to go today" plan on the Fish / Hunt tabs — the rough idea
        // without picking a species. (Best tab stays glanceable: hero + top pick.)
        if (sideFilter != null) {
            item { SectionHeader("Game plan", "general idea") }
            item {
                GamePlanTeaser(buildSidePlan(sideFilter, c, today, forecast.timing, forecast.precipMmHr), sideFilter) {
                    onOpenSidePlan(sideFilter)
                }
            }
        }
        item { SectionHeader("Best today", "In season · $openCount") }
        items(primary) { row ->
            SpeciesCard(row, c, emphasized = row == primary.firstOrNull(), onOpenDetail = onOpenDetail)
        }
        if (secondary.isNotEmpty()) {
            item { GroupDivider("Out of season · ${secondary.size}") }
            items(secondary) { row -> OutOfSeasonRow(row, onOpenDetail) }
        }
        item { Spacer(Modifier.height(Space.lg)) }
    }
}

@Composable
private fun Header(f: Forecast, savedAtMillis: Long, live: Boolean) {
    Column {
        Overline(dateKicker(), color = KairosColors.Water)
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
            "${if (live) "Updated" else "Cached"} ${timeText(savedAtMillis)}  ·  pull to refresh",
            style = MaterialTheme.typography.bodySmall,
            color = KairosColors.Faint,
        )
        if (f.source == "NWS") {
            Spacer(Modifier.height(Space.xs))
            Text(
                "Backup source (NWS) — Open-Meteo unavailable; no timing curve",
                style = MaterialTheme.typography.bodySmall,
                color = KairosColors.Fair,
            )
        }
    }
}

/** "THU · SEP 5" — the header kicker (Overline uppercases it). */
private fun dateKicker(): String {
    val d = LocalDate.now()
    val dow = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
    return "$dow · ${monthDay(d)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionChips(f: Forecast) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("${"%.2f".format(f.pressureInHg)}\"", f.trendWord)
        Chip("−${f.tempDropNext24hF.roundToInt()}°", "front/24h")
        Chip("${f.windMph.roundToInt()} mph", "wind")
        Chip("${f.airF.roundToInt()}°F", "air · water ~${f.waterF.roundToInt()}°")
        Chip(moonGlyph(f.moonName), f.moonName)
    }
}

@Composable
private fun Chip(value: String, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = KairosColors.Text)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim)
    }
}

@Composable
private fun SegmentedControl(side: Side?, onSelect: (Side?) -> Unit) {
    val options = listOf<Pair<String, Side?>>("Best" to null, "Hunt" to Side.HUNT, "Fish" to Side.FISH)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (label, value) ->
            val selected = side == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .then(
                        if (selected) {
                            Modifier.background(Brush.verticalGradient(listOf(KairosColors.SegTop, KairosColors.SegBottom)))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) KairosColors.OnSeg else KairosColors.Dim,
                )
            }
        }
    }
}

@Composable
private fun LegalLightCard(f: Forecast) {
    val hours = f.legalShootingHours ?: return
    val fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), clip = false, spotColor = KairosColors.ShadowSpot, ambientColor = KairosColors.ShadowSpot)
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "LEGAL SHOOTING HOURS",
                style = MaterialTheme.typography.labelSmall,
                color = KairosColors.Pine,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${fmt.format(hours.first)} – ${fmt.format(hours.second)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "½ hr before sunrise (${fmt.format(f.sunriseTime)}) to ½ hr after sunset (${fmt.format(f.sunsetTime)})",
                style = MaterialTheme.typography.labelSmall,
                color = KairosColors.Faint,
            )
        }
    }
}

/** A section header: a short accent tick + tracked kicker on the left, meta on the right. */
@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(14.dp)
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(KairosColors.Water),
        )
        Spacer(Modifier.width(Space.sm))
        Overline(title, color = KairosColors.Dim)
        Spacer(Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
    }
}

@Composable
private fun SpeciesCard(row: SpeciesScore, c: Conditions, emphasized: Boolean, onOpenDetail: (String) -> Unit) {
    val status = seasonsFor(row.species.name)?.let { seasonStatus(it, today) }
    val radius = if (emphasized) 22.dp else 18.dp
    val shape = RoundedCornerShape(radius)
    val base = Modifier
        .fillMaxWidth()
        .shadow(
            elevation = if (emphasized) 12.dp else 3.dp,
            shape = shape,
            clip = false,
            spotColor = KairosColors.ShadowSpot,
            ambientColor = KairosColors.ShadowSpot,
        )
        .clip(shape)
        .clickable { onOpenDetail(row.species.name) }
    val styled = if (emphasized) {
        base
            .background(Brush.verticalGradient(listOf(KairosColors.CardTop, KairosColors.CardBottom)))
            .border(1.dp, KairosColors.CardBorder, shape)
    } else {
        base
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, shape)
    }
    Column(styled.padding(if (emphasized) 18.dp else 16.dp)) {
        if (emphasized) {
            Overline("Top pick today", color = KairosColors.Water)
            Spacer(Modifier.height(Space.md))
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.species.name,
                    style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KairosColors.Text,
                )
                if (status != null) {
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(seasonDotColor(status.kind)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(status.headline(), style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${row.percent}",
                    fontFamily = Bricolage,
                    fontSize = if (emphasized) 46.sp else 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp,
                    lineHeight = if (emphasized) 46.sp else 34.sp,
                    color = ratingColor(row.rating),
                )
                RatingPill(row.rating)
            }
        }
        Spacer(Modifier.height(Space.md))
        ScoreBar(row.percent, ratingColor(row.rating))
        Spacer(Modifier.height(Space.md))
        Text(whyFor(row.species, c), style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim, lineHeight = 19.sp)
    }
}

@Composable
private fun ScoreBar(percent: Int, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(KairosColors.Line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent / 100f)
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color),
        )
    }
}

@Composable
private fun RatingPill(rating: Rating) {
    val color = ratingColor(rating)
    Box(
        Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(ratingLabel(rating).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun OutOfSeasonRow(row: SpeciesScore, onOpenDetail: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KairosColors.Surface.copy(alpha = 0.5f))
            .border(1.dp, KairosColors.Line, RoundedCornerShape(14.dp))
            .clickable { onOpenDetail(row.species.name) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.species.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = KairosColors.Dim, modifier = Modifier.weight(1f))
        val label = seasonsFor(row.species.name)?.let { seasonStatus(it, today).headline() } ?: ""
        Text(label, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
        Spacer(Modifier.width(12.dp))
        Text("${row.percent}", fontFamily = Bricolage, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KairosColors.Faint)
    }
}

@Composable
private fun GroupDivider(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, letterSpacing = 1.2.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(KairosColors.Line))
    }
}

@Composable
private fun OfflineBanner(savedAtMillis: Long) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KairosColors.Error.copy(alpha = 0.14f))
            .border(1.dp, KairosColors.Error.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            "Couldn't update — showing the last forecast (${ageText(savedAtMillis)}). " +
                "The weather service may be busy; pull down to try again.",
            style = MaterialTheme.typography.bodySmall,
            color = KairosColors.Text,
        )
    }
}

// Season status uses teal = open/active, amber = upcoming/soon, neutral gray =
// closed. Teal (not green) keeps the many in-season dots from flooding the list
// with green and gives blue a consistent, tasteful role throughout the app.
private fun seasonDotColor(kind: SeasonStatusKind) = when (kind) {
    SeasonStatusKind.OPEN -> KairosColors.Water
    SeasonStatusKind.UPCOMING -> KairosColors.Fair
    else -> KairosColors.Slow
}

private fun moonGlyph(name: String): String = when {
    name.contains("new", true) -> "●"
    name.contains("full", true) -> "○"
    name.contains("first", true) -> "◑"
    name.contains("last", true) -> "◐"
    name.contains("waxing", true) -> "◑"
    name.contains("waning", true) -> "◐"
    else -> "◑"
}
