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
            item { SectionTick("Your seasons") }
            // Per-species cards, open/soonest first, each with its methods as dated rows.
            val ordered = speciesList.sortedWith(
                compareBy(
                    { orderRank(seasonStatus(it, today).kind) },
                    { seasonStatus(it, today).daysUntilNext ?: Int.MAX_VALUE },
                    { it.speciesName },
                ),
            )
            items(ordered, key = { it.speciesName }) { s ->
                SeasonSpeciesCard(s, today) { sheetSpecies = it }
            }
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

/** Sort key: open first, then upcoming, then closed/none. */
private fun orderRank(kind: SeasonStatusKind): Int = when (kind) {
    SeasonStatusKind.OPEN -> 0
    SeasonStatusKind.UPCOMING -> 1
    else -> 2
}

/**
 * One species as a readable card: its name + overall status, then each of its (filtered)
 * method windows as a dated, color-coded row with a live per-window status. Far clearer
 * than a single squished bar — you see exactly when archery / firearms / muzzleloader run.
 * Tap for the full sheet (sources, disclaimers).
 */
@Composable
private fun SeasonSpeciesCard(s: SpeciesSeasons, today: LocalDate, onTap: (String) -> Unit) {
    val status = seasonStatus(s, today)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .clickable { onTap(s.speciesName) }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.speciesName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KairosColors.Text,
                modifier = Modifier.weight(1f),
            )
            StatusChip(status.kind)
        }
        if (s.windows.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            s.windows.sortedBy { it.start }.forEachIndexed { i, w ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KairosColors.Line))
                MethodWindowRow(w, today)
            }
        } else if (s.disclaimer.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(s.disclaimer, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, lineHeight = 18.sp)
        }
    }
}

/** One method window inside a species card: color dot + method + dates + a status word. */
@Composable
private fun MethodWindowRow(w: SeasonWindow, today: LocalDate) {
    val color = methodColor(methodOf(w.label))
    val covers = !today.isBefore(w.start) && !today.isAfter(w.end)
    val upcoming = w.start.isAfter(today)
    val days = ChronoUnit.DAYS.between(today, w.start).toInt()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(9.dp)
                .height(9.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (covers || upcoming) color else color.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(w.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = KairosColors.Text)
            Text(
                "${monthDay(w.start)} – ${monthDay(w.end)}" + if (w.note.isNotEmpty()) "  ·  ${w.note}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = KairosColors.Faint,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        val (label, c) = when {
            covers -> "Open" to color
            upcoming && days <= 30 -> "in ${days}d" to KairosColors.Dim
            upcoming -> monthDay(w.start) to KairosColors.Faint
            else -> "Closed" to KairosColors.Faint
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c)
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
