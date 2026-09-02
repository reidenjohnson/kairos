package com.kairos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.engine.MAINE_FISHING_SPECIAL_REGS_URL
import com.kairos.engine.MAINE_SEASONS
import com.kairos.engine.SeasonStatusKind
import com.kairos.engine.SeasonWindow
import com.kairos.engine.Side
import com.kairos.engine.SpeciesSeasons
import com.kairos.engine.seasonStatus
import com.kairos.engine.seasonsFor
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The "Seasons" screen: a glanceable "season at a glance" timeline — every species
 * as a bar over a shared month grid with a TODAY line — plus an open-now summary
 * and a coming-up list. Tapping a species opens a floating profile card with its
 * full windows and official sources. (Design: design/Seasons.dc.html.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonsScreen(focusSpecies: String?) {
    val today = LocalDate.now()
    var side by remember { mutableStateOf(Side.HUNT) }
    var sheetSpecies by remember { mutableStateOf<String?>(null) }

    // A deep-link from a species row opens straight into that species' card.
    LaunchedEffect(focusSpecies) {
        if (focusSpecies != null) {
            seasonsFor(focusSpecies)?.let { side = it.side }
            sheetSpecies = focusSpecies
        }
    }

    val speciesList = MAINE_SEASONS.filter { it.side == side }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { Text("Maine · general law", style = MaterialTheme.typography.bodySmall, color = KairosColors.Faint) }
        item { HuntFishSegment(side) { side = it } }
        item { OpenNowCard(speciesList, today) }
        item {
            Text(
                "THE SEASON AT A GLANCE",
                style = MaterialTheme.typography.labelSmall,
                color = KairosColors.Faint,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item { GlanceTimeline(speciesList, today) { sheetSpecies = it } }
        item {
            Text(
                "COMING UP",
                style = MaterialTheme.typography.labelSmall,
                color = KairosColors.Faint,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item { ComingUp(speciesList, today) { sheetSpecies = it } }
        item { Footer() }
        item { Spacer(Modifier.height(20.dp)) }
    }

    val focused = sheetSpecies?.let { seasonsFor(it) }
    if (focused != null) {
        SpeciesSeasonSheet(focused, today, onDismiss = { sheetSpecies = null })
    }
}

@Composable
private fun HuntFishSegment(side: Side, onSelect: (Side) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("Hunting" to Side.HUNT, "Fishing" to Side.FISH).forEach { (label, value) ->
            val selected = side == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .then(
                        if (selected) Modifier.background(Brush.verticalGradient(listOf(KairosColors.SegTop, KairosColors.SegBottom)))
                        else Modifier,
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
private fun OpenNowCard(speciesList: List<SpeciesSeasons>, today: LocalDate) {
    val open = speciesList.filter { seasonStatus(it, today).kind == SeasonStatusKind.OPEN }
    val nextUp = speciesList
        .map { it to seasonStatus(it, today) }
        .filter { it.second.kind == SeasonStatusKind.UPCOMING }
        .minByOrNull { it.second.daysUntilNext ?: Int.MAX_VALUE }
    val closingSoon = open
        .mapNotNull { s -> seasonStatus(s, today).activeWindow?.let { s to it } }
        .minByOrNull { ChronoUnit.DAYS.between(today, it.second.end) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(KairosColors.CardTop, KairosColors.CardBottom)))
            .border(1.dp, KairosColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("OPEN NOW", style = MaterialTheme.typography.labelSmall, color = KairosColors.Pine, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            if (open.isEmpty()) "Nothing open today" else open.joinToString(" · ") { it.speciesName },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp,
        )
        val sub = buildList {
            closingSoon?.let {
                val d = ChronoUnit.DAYS.between(today, it.second.end).toInt()
                if (d in 0..30) add("${it.first.speciesName} closes in $d days")
            }
            nextUp?.let { add("${it.first.speciesName} opens ${it.second.daysUntilNext?.let { d -> "in $d days" } ?: "soon"}") }
        }
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = KairosColors.Faint)
        }
    }
}

private val MONTH_ABBR = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@Composable
private fun GlanceTimeline(speciesList: List<SpeciesSeasons>, today: LocalDate, onTap: (String) -> Unit) {
    val withWindows = speciesList.filter { it.windows.isNotEmpty() }
    if (withWindows.isEmpty()) {
        InfoCard("No dated windows — see each species for details.")
        return
    }
    val minStart = withWindows.flatMap { it.windows }.minOf { it.start }
    val maxEnd = withWindows.flatMap { it.windows }.maxOf { it.end }
    val rangeStart = minOf(minStart, today).withDayOfMonth(1)
    val rangeEnd = maxOf(maxEnd, today)
    val span = ChronoUnit.DAYS.between(rangeStart, rangeEnd).toFloat().coerceAtLeast(1f)
    fun frac(d: LocalDate) = ChronoUnit.DAYS.between(rangeStart, d).toFloat() / span

    val labelW = 96.dp

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
    ) {
        // Month header aligned to the same track as the rows.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Spacer(Modifier.width(labelW))
            BoxWithConstraints(Modifier.weight(1f).height(16.dp)) {
                val w = maxWidth
                var m = rangeStart.withDayOfMonth(1)
                while (!m.isAfter(rangeEnd)) {
                    Text(
                        MONTH_ABBR[m.monthValue - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = KairosColors.Faint,
                        modifier = Modifier.offset(x = w * frac(m) + 2.dp),
                    )
                    m = m.plusMonths(1)
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        withWindows.forEach { s ->
            val open = seasonStatus(s, today).kind == SeasonStatusKind.OPEN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTap(s.speciesName) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.speciesName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (open) KairosColors.Text else KairosColors.Dim,
                    maxLines = 1,
                    modifier = Modifier.width(labelW),
                )
                BoxWithConstraints(Modifier.weight(1f).height(14.dp)) {
                    val w = maxWidth
                    // Faint track
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(999.dp))
                            .background(KairosColors.Line),
                    )
                    // Season window bars
                    s.windows.forEach { win ->
                        val x0 = w * frac(win.start)
                        val barW = (w * frac(win.end) - w * frac(win.start)).coerceAtLeast(4.dp)
                        val covers = !today.isBefore(win.start) && !today.isAfter(win.end)
                        Box(
                            Modifier
                                .offset(x = x0)
                                .width(barW)
                                .height(11.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (covers) KairosColors.Prime else KairosColors.Pine.copy(alpha = 0.55f)),
                        )
                    }
                    // Today marker
                    Box(
                        Modifier
                            .offset(x = w * frac(today))
                            .width(2.dp)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .background(KairosColors.Water),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComingUp(speciesList: List<SpeciesSeasons>, today: LocalDate, onTap: (String) -> Unit) {
    val upcoming = speciesList
        .mapNotNull { s ->
            val st = seasonStatus(s, today)
            st.nextWindow?.let { Triple(s, it, st.daysUntilNext ?: 0) }
        }
        .sortedBy { it.third }
        .take(5)
    if (upcoming.isEmpty()) {
        InfoCard("Nothing new opening in this list — most windows are already open or past.")
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp)),
    ) {
        upcoming.forEachIndexed { i, (s, win, days) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTap(s.speciesName) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(monthDay(win.start), style = MaterialTheme.typography.bodySmall, color = KairosColors.Faint, modifier = Modifier.width(64.dp))
                Text("${s.speciesName} — ${win.label.lowercase()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, KairosColors.CardBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("$days days", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = KairosColors.Pine)
                }
            }
            if (i < upcoming.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(KairosColors.Line))
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
    }
}

@Composable
private fun Footer() {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "Dates from Maine IF&W. Some windows vary by Wildlife Management District. Always confirm current rules before you go.",
            style = MaterialTheme.typography.labelSmall,
            color = KairosColors.Faint,
            lineHeight = 15.sp,
        )
    }
}

/** The floating profile card for one species: status, all windows, sources. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesSeasonSheet(s: SpeciesSeasons, today: LocalDate, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val status = seasonStatus(s, today)
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KairosColors.Surface2,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.speciesName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                StatusChip(status.kind)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                status.headline(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (status.kind) {
                    SeasonStatusKind.OPEN -> KairosColors.Prime
                    SeasonStatusKind.UPCOMING -> KairosColors.Water
                    else -> KairosColors.Dim
                },
            )

            if (s.windows.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("SEASON WINDOWS", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(8.dp))
                s.windows.forEach { w -> WindowRow(w, today) }
            }

            if (s.disclaimer.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(s.disclaimer, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, lineHeight = 18.sp)
            }

            Spacer(Modifier.height(18.dp))
            Text("SOURCES", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(8.dp))
            LinkRow("Official season dates — ${s.sourceLabel}") { uriHandler.openUri(s.sourceUrl) }
            if (s.side == Side.FISH) {
                LinkRow("Check your water's special regs — Maine IF&W") { uriHandler.openUri(MAINE_FISHING_SPECIAL_REGS_URL) }
            }
        }
    }
}

@Composable
private fun WindowRow(w: SeasonWindow, today: LocalDate) {
    val covers = !today.isBefore(w.start) && !today.isAfter(w.end)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (covers) KairosColors.Prime else KairosColors.Pine.copy(alpha = 0.55f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(w.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${monthDay(w.start)} – ${monthDay(w.end)}" + if (w.note.isNotEmpty()) "  ·  ${w.note}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = KairosColors.Faint,
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        "$label ↗",
        style = MaterialTheme.typography.bodySmall,
        color = KairosColors.Water,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun StatusChip(kind: SeasonStatusKind) {
    val (label, color) = when (kind) {
        SeasonStatusKind.OPEN -> "Open" to KairosColors.Prime
        SeasonStatusKind.UPCOMING -> "Upcoming" to KairosColors.Water
        SeasonStatusKind.CLOSED -> "Closed" to KairosColors.Faint
        SeasonStatusKind.NONE -> "No season" to KairosColors.Faint
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
