package com.kairos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.engine.MAINE_SEASONS
import com.kairos.engine.SeasonStatusKind
import com.kairos.engine.SeasonWindow
import com.kairos.engine.Side
import com.kairos.engine.SpeciesSeasons
import com.kairos.engine.seasonStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The "Seasons" tab: Maine IF&W 2026-27 windows, cited, with a timeline per species. */
@Composable
fun SeasonsScreen(focusSpecies: String?) {
    val today = LocalDate.now()

    // Flat item list: a header, then that side's species, for each side.
    data class Entry(val header: String?, val seasons: SpeciesSeasons?)
    val entries = remember {
        buildList {
            add(Entry("HUNT", null))
            MAINE_SEASONS.filter { it.side == Side.HUNT }.forEach { add(Entry(null, it)) }
            add(Entry("FISH", null))
            MAINE_SEASONS.filter { it.side == Side.FISH }.forEach { add(Entry(null, it)) }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(focusSpecies) {
        if (focusSpecies != null) {
            val idx = entries.indexOfFirst { it.seasons?.speciesName == focusSpecies }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
    }

    val openNow = remember(today) {
        MAINE_SEASONS.filter { seasonStatus(it, today).kind == SeasonStatusKind.OPEN }
            .map { it.speciesName }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Maine Seasons 2026-27", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Today: ${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${monthDay(today)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (openNow.isEmpty()) "Nothing open today." else "Open now: ${openNow.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        itemsIndexed(entries) { _, e ->
            when {
                e.header != null -> Text(
                    e.header,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                e.seasons != null -> SeasonCard(e.seasons, today, e.seasons.speciesName == focusSpecies)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SeasonCard(s: SpeciesSeasons, today: LocalDate, focused: Boolean) {
    val status = seasonStatus(s, today)
    val cardMod = if (focused) {
        Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else {
        Modifier.fillMaxWidth()
    }
    Card(cardMod, colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.speciesName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(status.kind)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                status.headline(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (status.kind) {
                    SeasonStatusKind.OPEN -> ratingColor(com.kairos.engine.Rating.PRIME)
                    SeasonStatusKind.UPCOMING -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (s.windows.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SeasonTimeline(
                    windows = s.windows,
                    today = today,
                    barColor = if (s.side == Side.HUNT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    todayColor = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
                s.windows.forEach { w ->
                    Text(
                        "• ${w.label}: ${monthDay(w.start)} – ${monthDay(w.end)}" +
                            (if (w.note.isNotEmpty()) "  (${w.note})" else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (s.disclaimer.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    s.disclaimer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            SourceLink(s.sourceLabel, s.sourceUrl)
        }
    }
}

@Composable
private fun StatusChip(kind: SeasonStatusKind) {
    val (label, color) = when (kind) {
        SeasonStatusKind.OPEN -> "Open" to ratingColor(com.kairos.engine.Rating.PRIME)
        SeasonStatusKind.UPCOMING -> "Upcoming" to MaterialTheme.colorScheme.secondary
        SeasonStatusKind.CLOSED -> "Closed" to MaterialTheme.colorScheme.onSurfaceVariant
        SeasonStatusKind.NONE -> "No season" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun SourceLink(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        "Source: $label ↗",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { uriHandler.openUri(url) },
    )
}

/**
 * A one-line timeline: a track spanning the species' windows (padded to include
 * today), each window drawn as a colored bar, with a vertical "today" marker.
 */
@Composable
private fun SeasonTimeline(
    windows: List<SeasonWindow>,
    today: LocalDate,
    barColor: Color,
    trackColor: Color,
    todayColor: Color,
) {
    val minStart = windows.minOf { it.start }
    val maxEnd = windows.maxOf { it.end }
    // Pad the range a little and always include today so the marker is visible.
    val rangeStart = minOf(minStart, today).minusDays(7)
    val rangeEnd = maxOf(maxEnd, today).plusDays(7)
    val span = ChronoUnit.DAYS.between(rangeStart, rangeEnd).toFloat().coerceAtLeast(1f)
    fun frac(dte: LocalDate) = ChronoUnit.DAYS.between(rangeStart, dte).toFloat() / span

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(16.dp),
    ) {
        val h = size.height
        val trackY = h / 2f
        val stroke = h * 0.55f
        // Track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackY - stroke / 2f),
            size = Size(size.width, stroke),
            cornerRadius = CornerRadius(stroke / 2f, stroke / 2f),
        )
        // Window bars (overlaps blend via alpha)
        windows.forEach { w ->
            val x0 = frac(w.start) * size.width
            val x1 = frac(w.end) * size.width
            drawRoundRect(
                color = barColor.copy(alpha = 0.75f),
                topLeft = Offset(x0, trackY - stroke / 2f),
                size = Size((x1 - x0).coerceAtLeast(3f), stroke),
                cornerRadius = CornerRadius(stroke / 2f, stroke / 2f),
            )
        }
        // Today marker
        if (!today.isBefore(rangeStart) && !today.isAfter(rangeEnd)) {
            val x = frac(today) * size.width
            drawLine(
                color = todayColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = h * 0.16f,
            )
        }
    }
}
