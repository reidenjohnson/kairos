package com.kairos.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.kairos.engine.HuntMethod
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
 * The "Seasons" screen, in two tabs. **Yours**: a card per season you follow (Settings
 * species + methods), each window as a plain row with its dates and status, so nothing
 * overlaps. **All**: every Maine species on one month axis, one lane per method or
 * season type so overlapping windows (whitetail's archery/firearms/muzzleloader) sit
 * on their own lines instead of piled on one bar. Tapping a species opens a floating
 * profile card with its full windows and official sources.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonsScreen(focusSpecies: String?) {
    val today = LocalDate.now()
    var sheetSpecies by remember { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    // A deep-link from a species row opens straight into that species' card.
    LaunchedEffect(focusSpecies) {
        if (focusSpecies != null) sheetSpecies = focusSpecies
    }

    // Hunting only (fishing is open year-round, so it has no season table). "Yours" honors
    // BOTH the species filter and the "seasons you hunt" method subscription: narrow each
    // species to its enabled-method windows, then drop any left with nothing open. Reading
    // SpeciesPrefs / MethodPrefs recomposes on change.
    MethodPrefs.enabled
    val yours = MAINE_SEASONS
        .filter { SpeciesPrefs.isEnabled(it.speciesName) }
        .map { it.copy(windows = MethodFilter.windows(it)) }
        .filter { it.windows.isNotEmpty() && !it.noOpenSeason }
    val followed = yours.map { it.speciesName }.toSet()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Spacer(Modifier.height(Space.xs)) }
        item { SeasonsHeader() }
        item { SeasonTabs(showAll) { showAll = it } }
        if (!showAll) {
            if (yours.isEmpty()) {
                item { SeasonsEmptyHint() }
            } else {
                item { OpenNowCard(yours.filterNot { isYearRound(it) }, today) }
                item {
                    SeasonChart(yours, followed, today, startExpanded = true, stateKey = "yours") { sheetSpecies = it }
                }
            }
        } else {
            item {
                SeasonChart(MAINE_SEASONS, followed, today, startExpanded = false, stateKey = "all") { sheetSpecies = it }
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

/**
 * One color per species, used on the chart lanes, the card spines and the window dots.
 * Validated with the dataviz palette check (light and dark, in chart row order): each
 * neighboring pair stays apart for colorblind readers, and every mark sits beside its
 * species name, so color is never the only cue. Moose/bear/hare colors are arbitrary but
 * fixed; whitetail is rust, upland birds ochre, waterfowl water-blue, turkey wattle-red.
 */
internal fun speciesColor(name: String): Color {
    val dark = KairosColors.dark
    return when (name) {
        "Whitetail deer" -> if (dark) Color(0xFFD95926) else Color(0xFFEB6834)
        "Moose" -> if (dark) Color(0xFF9085E9) else Color(0xFF4A3AA7)
        "Black bear" -> Color(0xFF008300)
        "Snowshoe hare" -> if (dark) Color(0xFFD55181) else Color(0xFFE87BA4)
        "Upland birds" -> if (dark) Color(0xFFC98500) else Color(0xFFEDA100)
        "Waterfowl" -> if (dark) Color(0xFF3987E5) else Color(0xFF2A78D6)
        "Wild turkey" -> if (dark) Color(0xFFE66767) else Color(0xFFE34948)
        else -> KairosColors.Faint
    }
}

/** Yours / All switch, in the app's segment style. */
@Composable
private fun SeasonTabs(showAll: Boolean, onPick: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(999.dp))
            .padding(4.dp),
    ) {
        listOf(false to "Your seasons", true to "All seasons").forEach { (all, label) ->
            val selected = all == showAll
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .then(
                        if (selected) Modifier.background(Brush.linearGradient(listOf(KairosColors.SegTop, KairosColors.SegBottom)))
                        else Modifier,
                    )
                    .clickable { onPick(all) }
                    .padding(vertical = 10.dp),
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
            .background(Brush.verticalGradient(listOf(KairosColors.HeroTop, KairosColors.HeroBottom)))
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

/** "Oct 31" for one day, "Oct 23–24" inside a month, "Nov 30–Dec 5" across months. */
private fun dateRange(a: LocalDate, b: LocalDate): String = when {
    a == b -> monthDay(a)
    a.month == b.month -> "${monthDay(a)}–${b.dayOfMonth}"
    else -> "${monthDay(a)}–${monthDay(b)}"
}

/** The dates behind a lane plus where it stands today, e.g. "Nov 2–28 · opens in 34 days". */
private fun laneCaption(lane: Lane, today: LocalDate): String {
    val dates = lane.windows.joinToString(", ") { dateRange(it.start, it.end) }
    val open = lane.windows.firstOrNull { !today.isBefore(it.start) && !today.isAfter(it.end) }
    val next = lane.windows.filter { it.start.isAfter(today) }.minByOrNull { it.start }
    val status = when {
        open != null -> ChronoUnit.DAYS.between(today, open.end).toInt().let { if (it == 0) "last day" else "open, $it days left" }
        next != null -> ChronoUnit.DAYS.between(today, next.start).toInt().let { if (it == 1) "opens tomorrow" else "opens in $it days" }
        else -> "closed for the year"
    }
    return "$dates · $status"
}

/** One row inside an expanded species: a short name and every window it covers. */
private data class Lane(val label: String, val windows: List<SeasonWindow>)

/**
 * Split a species into lanes so overlapping windows never share a line. Whitetail goes by
 * method, with its youth and residents-only days together as "Special days"; other species
 * go by window name without the zone/WMD detail in parentheses (that stays on the full card),
 * so e.g. waterfowl's two South Zone splits share one lane.
 */
private fun lanesFor(s: SpeciesSeasons): List<Lane> {
    val deer = s.windows.any { methodOf(it.label) != HuntMethod.GENERAL }
    val keyed = s.windows.groupBy { w ->
        if (deer) {
            when (val m = methodOf(w.label)) {
                HuntMethod.GENERAL -> "Special days"
                HuntMethod.ARCHERY -> "Archery"
                else -> m.label
            }
        } else {
            w.label.substringBefore(" (").trim()
        }
    }
    return keyed.map { (k, ws) -> Lane(k, ws.sortedBy { it.start }) }.sortedBy { it.windows.first().start }
}

/** Overlapping or touching windows merged into continuous spans, for the one-bar row. */
private fun spansOf(windows: List<SeasonWindow>): List<Pair<LocalDate, LocalDate>> {
    val out = mutableListOf<Pair<LocalDate, LocalDate>>()
    windows.sortedBy { it.start }.forEach { w ->
        val last = out.lastOrNull()
        if (last != null && !w.start.isAfter(last.second.plusDays(1))) {
            out[out.lastIndex] = last.first to maxOf(last.second, w.end)
        } else {
            out += w.start to w.end
        }
    }
    return out
}

/**
 * The seasons as a picture, on one shared month axis. Collapsed, each species is ONE bar
 * in its own color (its windows merged). Tap a species to expand it in place into one lane
 * per method or season type; tap a lane for its dates and where it stands today. Words
 * stay out of the way: lanes carry only a short name, and the full written list lives in
 * the species' Details card. Faint month bands replace gridlines; one TODAY line runs
 * through every row. On "All", species you follow are in full-strength text.
 */
@Composable
private fun SeasonChart(
    all: List<SpeciesSeasons>,
    followed: Set<String>,
    today: LocalDate,
    startExpanded: Boolean,
    stateKey: String,
    onDetails: (String) -> Unit,
) {
    val dated = all.filter { it.windows.isNotEmpty() && !it.noOpenSeason && !isYearRound(it) }
    if (dated.isEmpty()) return
    // Axis: the month the earliest window opens through December. Longer windows (hare
    // runs to March) run off the right edge and say their end date when expanded.
    val rangeStart = minOf(dated.flatMap { it.windows }.minOf { it.start }, today).withDayOfMonth(1)
    val rangeEnd = maxOf(LocalDate.of(rangeStart.year, 12, 31), today)
    val span = ChronoUnit.DAYS.between(rangeStart, rangeEnd).toFloat() + 1f
    fun frac(d: LocalDate) = (ChronoUnit.DAYS.between(rangeStart, d).toFloat() / span).coerceIn(0f, 1f)
    val months = buildList {
        var m = rangeStart
        while (!m.isAfter(rangeEnd)) { add(m); m = m.plusMonths(1) }
    }
    val labelW = 112.dp
    var expanded by rememberSaveable(stateKey) {
        mutableStateOf(if (startExpanded) all.map { it.speciesName } else emptyList<String>())
    }
    // The one lane whose dates are showing ("species|lane"), if any.
    var picked by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }

    // The plot area behind a row: faint alternating month bands, the marks, the TODAY line.
    @Composable
    fun Track(height: Dp, content: @Composable BoxWithConstraintsScope.() -> Unit) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
            val w = maxWidth
            months.forEachIndexed { i, m ->
                if (i % 2 == 1) {
                    val x0 = w * frac(m)
                    val x1 = w * frac(m.plusMonths(1))
                    Box(Modifier.offset(x = x0).width(x1 - x0).fillMaxHeight().background(KairosColors.Text.copy(alpha = 0.035f)))
                }
            }
            content()
            Box(
                Modifier
                    .offset(x = w * frac(today) - 1.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(KairosColors.Text),
            )
        }
    }

    @Composable
    fun Bars(windows: List<SeasonWindow>, color: Color, barH: Dp) {
        Track(barH + 18.dp) {
            val w = maxWidth
            spansOf(windows).forEach { (a, b) ->
                val x0 = w * frac(a)
                val barW = (w * frac(b.plusDays(1)) - x0).coerceAtLeast(6.dp)
                Box(
                    Modifier
                        .offset(x = x0)
                        .width(barW)
                        .height(barH)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (b.isBefore(today)) color.copy(alpha = 0.35f) else color),
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        // Month axis, each label centered in its month.
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(labelW))
            BoxWithConstraints(Modifier.weight(1f).height(18.dp)) {
                val w = maxWidth
                months.forEach { m ->
                    val x0 = w * frac(m)
                    val x1 = w * frac(m.plusMonths(1))
                    Box(Modifier.offset(x = x0).width(x1 - x0), contentAlignment = Alignment.Center) {
                        Text(MONTH_ABBR[m.monthValue - 1], style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        all.forEach { s ->
            val isFollowed = s.speciesName in followed
            val color = speciesColor(s.speciesName)
            val yearRound = isYearRound(s)
            val canExpand = s.windows.isNotEmpty() && !s.noOpenSeason && !yearRound
            val open = s.speciesName in expanded
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canExpand) {
                            expanded = if (open) expanded - s.speciesName else expanded + s.speciesName
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.width(labelW), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.speciesName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isFollowed) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isFollowed) KairosColors.Text else KairosColors.Dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (canExpand) {
                            Icon(
                                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (open) "Collapse ${s.speciesName}" else "Expand ${s.speciesName}",
                                tint = KairosColors.Faint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when {
                            s.noOpenSeason || s.windows.isEmpty() -> Text(
                                "No open season in Maine",
                                style = MaterialTheme.typography.labelSmall,
                                color = KairosColors.Faint,
                                modifier = Modifier.padding(vertical = 7.dp),
                            )
                            yearRound -> Text(
                                "Open all year",
                                style = MaterialTheme.typography.labelSmall,
                                color = KairosColors.Faint,
                                modifier = Modifier.padding(vertical = 7.dp),
                            )
                            else -> Bars(s.windows, color, 12.dp)
                        }
                    }
                }
                if (open && canExpand) {
                    val lastEnd = s.windows.maxOf { it.end }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KairosColors.Bg)
                            .padding(vertical = 6.dp),
                    ) {
                        lanesFor(s).forEach { lane ->
                            val id = "${s.speciesName}|${lane.label}"
                            val shown = picked == id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { picked = if (shown) null else id },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    lane.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (shown) FontWeight.SemiBold else FontWeight.Normal,
                                    color = KairosColors.Dim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(labelW).padding(start = 8.dp, end = 4.dp),
                                )
                                Box(Modifier.weight(1f)) { Bars(lane.windows, color, 8.dp) }
                            }
                            if (shown) {
                                Text(
                                    laneCaption(lane, today),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KairosColors.Text,
                                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                                )
                            }
                        }
                        if (lastEnd.isAfter(rangeEnd)) {
                            Text(
                                "Runs past December, to ${monthDay(lastEnd)}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = KairosColors.Faint,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            )
                        }
                        Text(
                            "Details & sources ›",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KairosColors.Water,
                            modifier = Modifier
                                .clickable { onDetails(s.speciesName) }
                                .padding(start = 8.dp, top = 6.dp, bottom = 2.dp, end = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (startExpanded) "Tap a bar for its dates. Tap a species name to fold it."
            else "Tap a species to see each season on its own line. Darker names are the seasons you follow.",
            style = MaterialTheme.typography.labelSmall,
            color = KairosColors.Faint,
            lineHeight = 15.sp,
        )
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
                s.windows.forEach { w -> WindowRow(w, today, speciesColor(s.speciesName)) }
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
private fun WindowRow(w: SeasonWindow, today: LocalDate, mColor: Color) {
    val covers = !today.isBefore(w.start) && !today.isAfter(w.end)
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
