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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.engine.MAINE_SEASONS
import com.kairos.engine.MethodFilter
import com.kairos.engine.SeasonStatusKind
import com.kairos.engine.SeasonWindow
import com.kairos.engine.SpeciesSeasons
import com.kairos.engine.methodOf
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
    var sheetSpecies by remember { mutableStateOf<String?>(null) }

    // A deep-link from a species row opens straight into that species' card.
    LaunchedEffect(focusSpecies) {
        if (focusSpecies != null) sheetSpecies = focusSpecies
    }

    // Hunting only (fishing is open year-round, so it has no season table). Honor BOTH the
    // species filter and the "seasons you hunt" method subscription: narrow each species to
    // its enabled-method windows, then drop any left with nothing open. Reading
    // SpeciesPrefs / MethodPrefs recomposes on change.
    MethodPrefs.enabled
    val speciesList = MAINE_SEASONS
        .filter { SpeciesPrefs.isEnabled(it.speciesName) }
        .map { it.copy(windows = MethodFilter.windows(it)) }
        .filter { it.windows.isNotEmpty() || it.noOpenSeason }
        // Drop year-round species (e.g. coyote) — a "season" adds nothing there.
        .filterNot { isYearRound(it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Spacer(Modifier.height(Space.xs)) }
        item { SeasonsHeader() }
        if (speciesList.isEmpty()) {
            item { SeasonsEmptyHint() }
        } else {
            item { OpenNowCard(speciesList, today) }
            item { SectionTick("The season at a glance") }
            item { SeasonChart(speciesList, today) { sheetSpecies = it } }
            item { MethodLegend(speciesList) }
        }
        item { Footer() }
        item { Spacer(Modifier.height(Space.lg)) }
    }

    val focused = sheetSpecies?.let { seasonsFor(it) }
    if (focused != null) {
        SpeciesSeasonSheet(focused, today, onDismiss = { sheetSpecies = null })
    }
}

@Composable
private fun SeasonsHeader() {
    Column {
        Overline("Seasons", color = KairosColors.Water)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Hunting seasons",
            fontFamily = Bricolage,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            lineHeight = 34.sp,
            color = KairosColors.Text,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Official dates from Maine IF&W",
            style = MaterialTheme.typography.bodySmall,
            color = KairosColors.Faint,
        )
    }
}

/** The shared section voice on this screen: an accent tick + tracked kicker. */
@Composable
private fun SectionTick(text: String) {
    Row(
        modifier = Modifier.padding(top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
private fun SeasonsEmptyHint() {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xl)) {
        Text(
            "No species selected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KairosColors.Text,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Turn on the species you're after in Settings to see their seasons.",
            style = MaterialTheme.typography.bodyMedium,
            color = KairosColors.Dim,
        )
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
        Text("OPEN NOW", style = MaterialTheme.typography.labelSmall, color = KairosColors.Water, letterSpacing = 1.6.sp)
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

/** A species with no real season (open ~all year, e.g. coyote) — kept out of the chart. */
private fun isYearRound(s: SpeciesSeasons): Boolean {
    if (s.windows.size != 1) return false
    val w = s.windows.first()
    return ChronoUnit.DAYS.between(w.start, w.end) >= 300
}

/**
 * The season-at-a-glance CHART: one row per species, its method windows drawn as tall,
 * color-coded segments over a shared month axis, with gridlines and a TODAY line. Reads
 * as a picture of the whole fall — far quicker than dates in a list. Tap a row for detail.
 */
@Composable
private fun SeasonChart(speciesList: List<SpeciesSeasons>, today: LocalDate, onTap: (String) -> Unit) {
    val withWindows = speciesList.filter { it.windows.isNotEmpty() }
    if (withWindows.isEmpty()) return
    val minStart = withWindows.flatMap { it.windows }.minOf { it.start }
    val maxEnd = withWindows.flatMap { it.windows }.maxOf { it.end }
    // Snap the range to whole months so the axis lines up, and include today.
    val rangeStart = minOf(minStart, today).withDayOfMonth(1)
    val rangeEnd = maxOf(maxEnd, today).let { it.withDayOfMonth(1).plusMonths(1).minusDays(1) }
    val span = ChronoUnit.DAYS.between(rangeStart, rangeEnd).toFloat().coerceAtLeast(1f)
    fun frac(d: LocalDate) = ChronoUnit.DAYS.between(rangeStart, d).toFloat() / span

    val labelW = 92.dp
    val months = buildList {
        var m = rangeStart
        while (!m.isAfter(rangeEnd)) { add(m); m = m.plusMonths(1) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
    ) {
        // Month axis.
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(labelW))
            BoxWithConstraints(Modifier.weight(1f).height(16.dp)) {
                val w = maxWidth
                months.forEach { m ->
                    Text(
                        MONTH_ABBR[m.monthValue - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = KairosColors.Faint,
                        modifier = Modifier.offset(x = w * frac(m) + 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        withWindows.forEach { s ->
            val open = seasonStatus(s, today).kind == SeasonStatusKind.OPEN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTap(s.speciesName) }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.speciesName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (open) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (open) KairosColors.Text else KairosColors.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(labelW),
                )
                BoxWithConstraints(Modifier.weight(1f).height(20.dp)) {
                    val w = maxWidth
                    // Month gridlines.
                    months.drop(1).forEach { m ->
                        Box(
                            Modifier
                                .offset(x = w * frac(m))
                                .width(1.dp)
                                .fillMaxHeight()
                                .align(Alignment.CenterStart)
                                .background(KairosColors.Line.copy(alpha = 0.6f)),
                        )
                    }
                    // Method segments (tall, rounded, colored).
                    s.windows.forEach { win ->
                        val x0 = w * frac(win.start)
                        // Keep short windows (e.g. the 5-day muzzleloader splits) as small
                        // BARS, not dots — a modest min width + a small radius.
                        val barW = (w * frac(win.end) - w * frac(win.start)).coerceAtLeast(9.dp)
                        val covers = !today.isBefore(win.start) && !today.isAfter(win.end)
                        val past = win.end.isBefore(today)
                        val mColor = methodColor(methodOf(win.label))
                        Box(
                            Modifier
                                .offset(x = x0)
                                .width(barW)
                                .height(if (covers) 16.dp else 12.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (past) mColor.copy(alpha = 0.32f) else mColor),
                        )
                    }
                    // Today line.
                    Box(
                        Modifier
                            .offset(x = w * frac(today))
                            .width(2.dp)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .background(KairosColors.Text),
                    )
                }
            }
        }
    }
}

/** The color key for the chart — only the methods actually present. */
@Composable
private fun MethodLegend(speciesList: List<SpeciesSeasons>) {
    // Only the real weapon methods get a legend; general-season species just show name + dates.
    val present = speciesList.flatMap { it.windows }.map { methodOf(it.label) }
        .filter { it != com.kairos.engine.HuntMethod.GENERAL }.distinct()
    if (present.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        present.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(9.dp).height(9.dp).clip(RoundedCornerShape(3.dp)).background(methodColor(m)))
                Spacer(Modifier.width(5.dp))
                Text(legendLabel(m), style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, maxLines = 1)
            }
        }
    }
}

/** Compact legend labels so the methods fit on one line. */
private fun legendLabel(m: com.kairos.engine.HuntMethod): String = when (m) {
    com.kairos.engine.HuntMethod.EXPANDED_ARCHERY -> "Exp. archery"
    else -> m.label
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
                    SeasonStatusKind.OPEN -> KairosColors.Water
                    SeasonStatusKind.UPCOMING -> KairosColors.Fair
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
        }
    }
}

@Composable
private fun WindowRow(w: SeasonWindow, today: LocalDate) {
    val covers = !today.isBefore(w.start) && !today.isAfter(w.end)
    val mColor = methodColor(methodOf(w.label))
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (covers) mColor else mColor.copy(alpha = 0.5f)),
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
        SeasonStatusKind.OPEN -> "Open" to KairosColors.Water
        SeasonStatusKind.UPCOMING -> "Upcoming" to KairosColors.Fair
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
