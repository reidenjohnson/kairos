package com.kairos.ui.bucks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.bucks.Buck
import com.kairos.bucks.BuckAnalysis
import com.kairos.bucks.BuckStore
import com.kairos.bucks.U
import com.kairos.engine.bucks.CamActivity
import com.kairos.engine.bucks.Check
import com.kairos.engine.bucks.DeerVisit
import com.kairos.engine.bucks.HisWinds
import com.kairos.engine.bucks.HuntWindow
import com.kairos.engine.bucks.HuntWindows
import com.kairos.engine.bucks.ModelCheck
import com.kairos.engine.bucks.SitChecks
import com.kairos.engine.bucks.SitKind
import com.kairos.engine.bucks.StandWind
import com.kairos.engine.bucks.WindCheck
import com.kairos.engine.bucks.WxHour
import com.kairos.ui.HourLane
import com.kairos.ui.HourReadout
import com.kairos.ui.HourlyChart
import com.kairos.ui.KairosColors
import com.kairos.ui.centerOn
import com.kairos.ui.rememberColPx
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The Forecast tab, simple first: pick who you're after (all deer or one buck), see the next
 * good sit, then the week as a calendar with a percent per sit (how many of a short checklist line
 * up). Tap a sit = the checklist, the wind at the stand drawn, and the photos. Hour by hour is
 * folded away for whoever wants it.
 */
@Composable
fun ForecastScreen(state: com.kairos.ui.UiState, startCam: String?, startHour: Long?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { com.kairos.bucks.BuckPrefs.load(context) }
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { BuckStore.ensureLoaded(context) } }
    // No camera pinned yet: the forecast for where you are.
    if (BuckStore.db.cameras.none { it.lat != null && it.lon != null }) com.kairos.ui.WeatherScreen(state)
    else HuntPane(startCam, startHour)
}

/** One sit at one camera, judged for one target (all deer, or one buck) at one hour. */
private class Judged(
    val camId: String,
    val kind: SitKind,
    val start: Long,
    val end: Long,
    /** The hour every check and number comes from. */
    val hour: WxHour,
    val checks: List<Check>,
    /** The photos behind it: deer here on this wind or at this time (all deer), or his visits on this wind (one buck). */
    val proof: List<String>,
) {
    val pct = SitChecks.percent(checks)
    /** Fewer than 3 checks could be judged (the forecasts disagree on the wind): no percent. */
    val unsure get() = checks.count { it.met != null } < 3
    val good get() = !unsure && pct >= SitChecks.GOOD_PCT
    /** The score's color: Fern 75%+, Amber 50-74%, plain below; "?" plain too. */
    val tint: Color get() = when { unsure -> KairosColors.Dim; pct >= SitChecks.GOOD_PCT -> Accent.fern; pct >= SitChecks.FAIR_PCT -> Accent.amber; else -> KairosColors.Text }
    val wash: Color get() = when {
        unsure -> KairosColors.Surface
        pct >= SitChecks.GOOD_PCT -> Accent.fern.copy(alpha = if (KairosColors.dark) 0.26f else 0.18f)
        pct >= SitChecks.FAIR_PCT -> Accent.amber.copy(alpha = if (KairosColors.dark) 0.16f else 0.12f)
        else -> KairosColors.Surface
    }
    val day: LocalDate get() = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** A day's morning or evening, every camera, best first. */
private class Cell(val day: LocalDate, val kind: SitKind, val options: List<Judged>) {
    val best get() = options.first()
}

@Composable
internal fun HuntPane(startCam: String?, startHour: Long?) {
    val nav = com.kairos.ui.LocalAppNav.current
    val db = BuckStore.db
    val res = BuckAnalysis.state
    val zone = ZoneId.systemDefault()
    val pinned = db.cameras.filter { it.lat != null && it.lon != null }
    if (pinned.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pin a camera first", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("The forecast is pulled for each camera's exact spot.", color = KairosColors.Dim)
        }
        return
    }
    if (res == null) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp))
            Text("Pulling the forecast…", color = KairosColors.Dim)
        }
        return
    }
    val today = LocalDate.now(zone)
    // Who you're after: null = all deer, else one buck (only bucks with a known wind).
    val targets = db.bucks.filter { b -> res.reports[b.id]?.let { HisWinds.of(it) }.orEmpty().isNotEmpty() }
        .sortedWith(compareBy<Buck> { it.pool }.thenByDescending { it.star })
    var who by ::forecastWho
    if (who != null && targets.none { it.id == who }) who = null

    // The stand you hunt from: one camera judges the whole page (no picking whichever scores
    // better). Remembered; a switch at the top looks at another.
    val context = androidx.compose.ui.platform.LocalContext.current
    val standPrefs = remember { context.getSharedPreferences("forecast", android.content.Context.MODE_PRIVATE) }
    var camId by rememberSaveable { mutableStateOf(startCam?.takeIf { id -> pinned.any { it.id == id } } ?: standPrefs.getString("stand", null)?.takeIf { id -> pinned.any { it.id == id } } ?: pinned.first().id) }
    if (pinned.none { it.id == camId }) camId = pinned.first().id
    fun pickStand(id: String) { camId = id; standPrefs.edit().putString("stand", id).apply() }

    val cells = remember(res, who, camId) {
        val until = today.plusDays(7)
        weekCells(res, camId, who, zone, until)
            .also { cs ->
                // The week as shown, with each check, for checking it against the forecast and photos.
                cs.forEach { c ->
                    val j = c.best
                    android.util.Log.i("KairosWeek", "${c.day} ${c.kind} ${db.camera(j.camId)?.name} who=${who?.let { db.buck(it)?.name } ?: "all"} ${j.pct}% hour=${Instant.ofEpochSecond(j.hour.t).atZone(zone).toLocalTime()} " +
                        "wind=${j.hour.windDir?.roundToInt()}/${j.hour.windMph?.roundToInt()} alt=${j.hour.altDir?.roundToInt()}/${j.hour.altMph?.roundToInt()} t=${j.hour.tempF?.roundToInt()} " +
                        j.checks.joinToString(" ") { "[${when (it.met) { true -> "Y"; false -> "N"; null -> "-" }} ${it.label}: ${it.detail}]" } +
                        "")
                }
            }
    }
    val now = System.currentTimeMillis() / 1000
    val next = cells.firstOrNull { it.best.good && it.best.end > now }
    val best = cells.filter { it.best.end > now && !it.best.unsure }.maxWithOrNull(compareBy<Cell> { it.best.pct }.thenByDescending { it.best.start })

    // The open sit card: "<epochDay>|<MORNING/EVENING>"; saved so back from a photo returns to it.
    var openCell by rememberSaveable { mutableStateOf<String?>(null) }
    var windFor by rememberSaveable { mutableStateOf<String?>(null) }
    var hoursOpen by rememberSaveable { mutableStateOf(startHour != null) }

    // ---- Hour by hour (folded), at one camera.
    val bundle = res.cams[camId]
    val hours = remember(bundle, now / 3600) { bundle?.raw.orEmpty().filter { it.t + 3600 > now } }
    val cam = pinned.first { it.id == camId }
    var selected by rememberSaveable { mutableStateOf(startHour ?: next?.best?.hour?.t) }
    val sel = hours.firstOrNull { it.t == selected } ?: hours.firstOrNull()
    val outer = rememberScrollState()
    val chart = rememberScrollState()
    val colPx = rememberColPx()
    var hourTop by remember { mutableStateOf(0) }
    LaunchedEffect(camId, hours.isNotEmpty(), hoursOpen) {
        val s = selected ?: return@LaunchedEffect
        if (hoursOpen && hours.any { it.t == s }) {
            chart.centerOn(hours, s, colPx)
            if (startHour != null && s == startHour) { kotlinx.coroutines.delay(150); outer.animateScrollTo(hourTop) }
        }
    }

    openCell?.let { k ->
        val (ed, kind) = k.split("|")
        val cell = cells.firstOrNull { it.day.toEpochDay() == ed.toLong() && it.kind.name == kind }
        if (cell == null) openCell = null
        else SitSheet(cell, who, zone, onClose = { openCell = null }, onSetWind = { windFor = it })
    }
    windFor?.let { YourWindSheet(it) { windFor = null } }
    fun open(c: Cell) { openCell = "${c.day.toEpochDay()}|${c.kind.name}" }

    Column(Modifier.fillMaxSize().verticalScroll(outer).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(2.dp))
        // New photos only count once sorted: say so where the sits are.
        val toSort = db.unsorted.size
        if (toSort > 0) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Accent.soft(Accent.amber)).clickable { nav.openSort() }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$toSort", color = Accent.amber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(12.dp))
            Text("new photo${if (toSort == 1) "" else "s"} to sort. They count here once sorted.", color = KairosColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("Sort", color = Accent.amber, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        // ---- Where you're hunting from, and who you're after
        if (pinned.size > 1) Seg(pinned.map { it.name }, pinned.indexOfFirst { it.id == camId }) { pickStand(pinned[it].id) }
        if (targets.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("All deer", who == null, null) { who = null }
            targets.forEach { b -> ToggleChip(if (b.pool) "Young bucks" else b.name, who == b.id, buckColor(b.colorIdx)) { who = b.id } }
        }

        // His wind is the check that separates days most; until it's set, say so up top.
        if (cam.windSectors.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { windFor = camId }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your wind at ${cam.name}: ", color = KairosColors.Dim, style = MaterialTheme.typography.bodyMedium)
            Text(StandWind.label(cam.windSectors), color = Accent.fern, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Change", color = Accent.teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        else Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Accent.soft(Accent.teal)).clickable { windFor = camId }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Set your wind for ${cam.name}. It's the check that matters most, so the % counts it once it's set.", color = KairosColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text("Set", color = Accent.teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        // ---- The answer first: the next good sit, then the best one if it's a different sit.
        if (next != null) NextCard(next, zone) { open(next) }
        else Text("No sit lines up ${SitChecks.GOOD_PCT}% or better in the next 7 days.", color = KairosColors.Dim, style = MaterialTheme.typography.bodyMedium)
        if (best != null && best !== next && best.best.pct > (next?.best?.pct ?: 0)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface).clickable { open(best) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Best this week", color = KairosColors.Dim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("${dayWord(best.day, today)} ${best.kind.label.lowercase()}", color = KairosColors.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("${best.best.pct}%", color = best.best.tint, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
        }

        // ---- The week
        Spacer(Modifier.height(2.dp))
        Text("THIS WEEK", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        WeekGrid(cells, today, now) { open(it) }
        ScoreKey()

        // ---- Hour by hour, folded
        Row(
            Modifier.fillMaxWidth().onGloballyPositioned { hourTop = it.positionInParent().y.roundToInt() }.clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface)
                .clickable { hoursOpen = !hoursOpen }.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hour by hour", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (hoursOpen) "Hide" else "Show", color = Accent.teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        if (hoursOpen) {
            if (hours.isEmpty()) Text("No forecast for ${cam.name} yet. It downloads with signal and then works offline.", color = KairosColors.Dim)
            else {
                val lanes = remember(hours, res, cam.windSectors) { lanesFor(res, camId, cam.windSectors, hours, zone) }
                sel?.let { h ->
                    HourReadout(h, zone) {
                        DeerOnThisWind(h, cam.name, res.deerVisits[camId].orEmpty(), zone)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.clip(CircleShape).background(Accent.brush(Accent.teal)).clickable { nav.openMap(h.t, null) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Accent.on(Accent.teal), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Watch this wind on the map", color = Accent.on(Accent.teal), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KairosColors.Surface).padding(vertical = 12.dp, horizontal = 8.dp)) {
                    HourlyChart(hours, cam.lat!!, cam.lon!!, zone, lanes, sel?.t, chart, sits = cells.map { it.best.start to it.best.end }) { t -> selected = t }
                }
                Text(
                    "Rows mark the hours whose wind matches one that deer came on (within ${HisWinds.MATCH_DEG.roundToInt()}°, or both calm). " +
                        "Forecast: NOAA HRRR about 2 days, then GFS, to 16 days (Open-Meteo); ECMWF is the second opinion. Pulled ${U.clock(res.at / 1000, zone)}; updates hourly.",
                    color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Who the Forecast tab is set to (null = all deer). App-wide, so a buck's page can open it on him. */
internal var forecastWho by mutableStateOf<String?>(null)

/**
 * A buck's next good sit at your stand, the same check and percent as the Forecast tab. Tap =
 * the Forecast tab on him.
 */
@Composable
internal fun NextSitFor(buckId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nav = com.kairos.ui.LocalAppNav.current
    val res = BuckAnalysis.state ?: return
    val camId = standId(context) ?: return
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val now = System.currentTimeMillis() / 1000
    val cells = remember(res, camId, buckId) { weekCells(res, camId, buckId, zone, today.plusDays(7)) }
    val next = cells.firstOrNull { it.best.good && it.best.end > now }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Accent.soft(Accent.fern))
            .clickable { forecastWho = buckId; nav.openForecast(camId, null) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("NEXT GOOD SIT FOR HIM", color = Accent.fern, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (next == null) Text("None lines up ${SitChecks.GOOD_PCT}% or better this week", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            else {
                Text("${dayWord(next.day, today)} ${next.kind.label.lowercase()}  ·  ${windShort(next.best.hour)}", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("At ${BuckStore.db.camera(camId)?.name}  ·  ${U.clock(next.best.start, zone)} to ${U.clock(next.best.end, zone)}", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (next != null) Text("${next.best.pct}%", color = next.best.tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

/** The sits at one stand from today until [until], a cell per morning / evening. */
private fun weekCells(res: BuckAnalysis.Result, camId: String, who: String?, zone: ZoneId, until: LocalDate): List<Cell> =
    judgeAll(res, camId, who, zone)
        .groupBy { it.day to it.kind }.filterKeys { (d, _) -> d.isBefore(until) }
        .map { (k, v) -> Cell(k.first, k.second, v) }
        .sortedWith(compareBy<Cell> { it.day }.thenBy { it.kind.ordinal })

/** The stand the Forecast tab is set to (remembered there), else the first pinned camera. */
internal fun standId(context: android.content.Context): String? {
    val pinned = BuckStore.db.cameras.filter { it.lat != null && it.lon != null }
    val saved = context.getSharedPreferences("forecast", android.content.Context.MODE_PRIVATE).getString("stand", null)
    return pinned.firstOrNull { it.id == saved }?.id ?: pinned.firstOrNull()?.id
}

/**
 * The Today tab: one clear answer over your #1 buck's photo (hunt this morning / evening, or the
 * next good sit), today's two sits, right now at your stand, the week at a glance and the newest
 * photos. Every number is the Forecast tab's own (same checks, same hour); every tap goes there
 * or to the photos.
 */
@Composable
internal fun TodayHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nav = com.kairos.ui.LocalAppNav.current
    val db = BuckStore.db
    val res = BuckAnalysis.state
    val camId = standId(context)
    val cam = db.camera(camId)
    if (res == null || camId == null || cam == null) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp))
            Text("Pulling the forecast for your stand…", color = KairosColors.Dim)
        }
        return
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val now = System.currentTimeMillis() / 1000
    val cells = remember(res, camId) { weekCells(res, camId, null, zone, today.plusDays(7)) }
    val todays = cells.filter { it.day == today && it.best.end > now }
    val pick = todays.firstOrNull { it.best.good } ?: cells.firstOrNull { it.best.good && it.best.end > now }
    val openForecast = { nav.openForecast(camId, null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(2.dp))
        Hero(pick, today, zone, cam.id, cam.name, openForecast)

        // Today's two sits.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in SitKind.entries) {
                val c = cells.firstOrNull { it.day == today && it.kind == k }
                Column(Modifier.weight(1f)) {
                    Text("THIS ${k.label.uppercase()}", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    if (c == null) Box(
                        Modifier.fillMaxWidth().height(CELL_H).clip(RoundedCornerShape(16.dp)).border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("Done", color = KairosColors.Faint, style = MaterialTheme.typography.labelMedium) }
                    else SitCell(c, now, openForecast)
                }
            }
        }

        // Right now at the stand: its own forecast hour, and legal light from today's sits.
        val hour = res.cams[camId]?.raw.orEmpty().firstOrNull { it.t + 3600 > now }
        val in3 = hour?.let { h -> res.cams[camId]?.raw.orEmpty().firstOrNull { it.t == h.t + 3 * 3600 } }
        val morning = cells.firstOrNull { it.day == today && it.kind == SitKind.MORNING }
        val evening = cells.firstOrNull { it.day == today && it.kind == SitKind.EVENING }
        Column {
            Text("NOW AT ${cam.name.uppercase()}", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Temp", hour?.tempF?.let { U.temp(it) } ?: "-", null, Modifier.weight(1f))
                Tile("Wind", hour?.let { windShort(it) } ?: "-", hour?.gustMph?.let { "gusts ${U.speedNum(it)}" }, Modifier.weight(1f))
                Tile(
                    "Pressure", hour?.pressureHpa?.let { U.pressure(it) } ?: "-",
                    if (hour?.pressureHpa != null && in3?.pressureHpa != null) (in3.pressureHpa!! - hour.pressureHpa!!).let { d -> if (d <= -1) "falling" else if (d >= 1) "rising" else "steady" } else null,
                    Modifier.weight(1f),
                )
            }
            // Legal light = the sits' own edges (30 min before sunrise, 30 min after sunset).
            val first = morning?.best?.end?.let { it - 3 * 3600 }
            val last = evening?.best?.end
            if (first != null || last != null) Text(
                "Legal light today: " + listOfNotNull(first?.takeIf { it > now - 12 * 3600 }?.let { U.clock(it, zone) }, last?.let { U.clock(it, zone) }).joinToString(" to "),
                color = KairosColors.Dim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }

        // The week at a glance: a dot per sit in the score's color. Tap = Forecast.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KairosColors.Surface).clickable(onClick = openForecast).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Forecast", color = Accent.teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row {
                for (i in 0 until 7) {
                    val d = today.plusDays(i.toLong())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "Today" else DOW.format(d), color = if (i == 0) Accent.teal else KairosColors.Dim, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        for (k in SitKind.entries) {
                            val j = cells.firstOrNull { it.day == d && it.kind == k }?.best
                            Box(
                                Modifier.padding(vertical = 2.dp).size(width = 30.dp, height = 14.dp).clip(RoundedCornerShape(5.dp))
                                    .background(
                                        when {
                                            j == null || j.end <= now -> KairosColors.Surface2.copy(alpha = 0.5f)
                                            j.unsure -> KairosColors.Surface2
                                            j.pct >= SitChecks.GOOD_PCT -> Accent.fern
                                            j.pct >= SitChecks.FAIR_PCT -> Accent.amber
                                            else -> KairosColors.Surface2
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Top row mornings, bottom row evenings. Green ${SitChecks.GOOD_PCT}%+, amber ${SitChecks.FAIR_PCT}%+.", color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall)
        }

        // The newest photos (deer only), newest first.
        val latest = db.photos.filter { it.label != com.kairos.bucks.PhotoLabel.NO_DEER && it.label != com.kairos.bucks.PhotoLabel.UNSORTED && it.epochSec != null }
            .sortedByDescending { it.epochSec }.distinctBy { (it.epochSec ?: 0) / 600 }.take(8)
        if (latest.isNotEmpty()) Column {
            Text("LATEST ON CAMERA", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(latest.size, key = { latest[it].id }) { i ->
                    val p = latest[i]
                    Column(Modifier.width(140.dp).clickable { nav.openPhotos(latest.map { it.id }, i) }) {
                        Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp))) { PhotoThumb(p.id, Modifier.fillMaxSize()) }
                        Text(whoLabel(p), color = KairosColors.Text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text("${db.camera(p.cameraId)?.name ?: ""} · ${DAY.format(Instant.ofEpochSecond(p.epochSec!!).atZone(zone))}", color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The answer, over your #1 buck's newest photo: hunt today, or when the next good sit is. */
@Composable
private fun Hero(pick: Cell?, today: LocalDate, zone: ZoneId, standId: String, standName: String, onTap: () -> Unit) {
    val db = BuckStore.db
    // The newest daylight deer photo at this stand (color reads best big), else the newest anywhere.
    val deer = db.photos.filter { it.label != com.kairos.bucks.PhotoLabel.NO_DEER && it.label != com.kairos.bucks.PhotoLabel.UNSORTED }.sortedByDescending { it.epochSec ?: 0 }
    val photo = deer.firstOrNull { it.cameraId == standId && !it.night } ?: deer.firstOrNull { !it.night } ?: deer.firstOrNull()
    // 16:8.6 and top-aligned: the camera's bottom strip falls off the frame.
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 8.6f).clip(RoundedCornerShape(28.dp)).background(Accent.planBrush).clickable(onClick = onTap)) {
        photo?.let { PhotoThumb(it.id, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, full = true) }
        // A dark fade at the bottom so the answer reads over any photo.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            val j = pick?.best
            val head = when {
                pick == null -> "No good sit this week"
                pick.day == today -> "Hunt this ${pick.kind.label.lowercase()}"
                else -> "Next: ${dayWord(pick.day, today)} ${pick.kind.label.lowercase()}"
            }
            Text("TODAY AT ${standName.uppercase()}", color = Accent.fernOnPhoto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(head, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (j != null) Text("${j.pct}%", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            if (j != null) Text(
                listOfNotNull("${U.clock(j.start, zone)} to ${U.clock(j.end, zone)}", windShort(j.hour), j.hour.tempF?.let { U.temp(it) }).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium,
            )
            else Text("Nothing lines up ${SitChecks.GOOD_PCT}% or better in the next 7 days. Tap for the week.", color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label.uppercase(), color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = KairosColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub ?: " ", color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** The score colors, once: what green and amber mean, and "?". */
@Composable
private fun ScoreKey() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Accent.fern to "${SitChecks.GOOD_PCT}%+", Accent.amber to "${SitChecks.FAIR_PCT}-${SitChecks.GOOD_PCT - 1}%", KairosColors.Text to "lower").forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(5.dp))
                Text(t, color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text("? = forecasts disagree", color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall)
    }
    Text("% = how many of the checks line up. Tap a sit to see them.", color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall)
}

private fun dayWord(d: LocalDate, today: LocalDate) = when (d) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> DOW.format(d) }

private fun windShort(h: WxHour) = if ((h.windMph ?: 0.0) < HisWinds.CALM_MPH || h.windDir == null) "calm" else "${U.compass(h.windDir!!)} ${U.speedNum(h.windMph!!)}"

/** The answer card: when, where, the percent. */
@Composable
private fun NextCard(c: Cell, zone: ZoneId, onTap: () -> Unit) {
    val db = BuckStore.db
    val j = c.best
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Accent.planBrush).clickable(onClick = onTap).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("NEXT GOOD SIT", color = Accent.fernOnPhoto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("${dayWord(c.day, LocalDate.now(zone))} ${c.kind.label.lowercase()}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                listOf("${U.clock(j.start, zone)} to ${U.clock(j.end, zone)}", windShort(j.hour), j.hour.tempF?.let { U.temp(it) } ?: "").filter { it.isNotEmpty() }.joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("${j.pct}%", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
    }
}

// ------------------------------------------------------------------ the checks

/**
 * Every sit at one camera, judged for [who] (null = all deer). The hour is the one the sit is
 * best at (HuntWindows); every check reads that one hour.
 */
private fun judgeAll(res: BuckAnalysis.Result, camId: String, who: String?, zone: ZoneId): List<Judged> {
    val db = BuckStore.db
    val cam = db.camera(camId) ?: return emptyList()
    val raw = res.cams[camId]?.raw.orEmpty().associateBy { it.t }
    val deer = res.deerVisits[camId].orEmpty()
    val windows = res.camWindows[camId].orEmpty()
    // The windows to judge: his (one buck), or the best buck's for each sit (all deer).
    val sits: List<HuntWindow> = if (who != null) windows[who].orEmpty()
    else windows.values.flatten().groupBy { it.start }.values.map { HuntWindows.ranked(it).first() }
    val feats = res.cams[camId]?.forecast.orEmpty().associateBy { it.t }
    return sits.mapNotNull { w ->
        // Judged at one fixed hour, never the sit's best one (that would pick the hour that fits):
        // the hour of sunrise for a morning, of sunset for an evening, when deer move most.
        val at = if (w.kind == SitKind.MORNING) w.end - 3 * 3600 + 1800 else w.end - 1800
        val h = raw[at / 3600 * 3600] ?: return@mapNotNull null
        val mentors = feats[h.t]?.let { com.kairos.engine.bucks.MentorWeather.hits(it).distinctBy { m -> m.ruleId } }.orEmpty()
        val split = WindCheck.of(h) == ModelCheck.SPLIT
        val calm = (h.windMph ?: 0.0) < HisWinds.CALM_MPH || h.windDir == null
        val mid = Instant.ofEpochSecond(h.t + 1800).atZone(zone).let { it.hour * 60 + it.minute }
        val notCounted = "Not counted: the forecasts disagree on the wind"
        val checks = ArrayList<Check>()
        // 1. Your wind at this stand.
        val fits = if (cam.windSectors.isEmpty() || split || calm) null else StandWind.fits(cam.windSectors, h.windDir, h.windMph)
        checks += Check(
            "Your wind", fits,
            when {
                cam.windSectors.isEmpty() -> "Not set for ${cam.name}"
                split -> notCounted
                calm -> "Calm: no clear direction for your scent"
                fits == true -> "${windShort(h)}, one you picked (${StandWind.label(cam.windSectors)})"
                else -> "${windShort(h)} blows your scent toward the ${U.compass((h.windDir!! + 180) % 360)}; you picked ${StandWind.label(cam.windSectors)}"
            },
        )
        val proof: List<String>
        if (who == null) {
            // 2. A named buck has come on this wind (any camera). The young-buck group is left out:
            // with many visits on many winds it matched nearly every hour.
            val bucks = if (split) emptyList() else db.bucks.filter { b -> !b.pool && HisWinds.matching(h.windDir, h.windMph, res.reports[b.id]?.let { HisWinds.of(it) }.orEmpty()).isNotEmpty() }
            checks += Check("A buck's wind", if (split) null else bucks.isNotEmpty(),
                when { split -> notCounted; bucks.isEmpty() -> "None of your named bucks has come on this wind yet"; else -> bucks.joinToString(", ") { it.name } + " came on this wind" })
            // 3. Deer came here on this wind AND at this time of day (within an hour), at least once.
            // One strict check: wind alone or time alone matched nearly every sit (10/14 audit).
            val onWind = if (split) emptyList() else CamActivity.windMatches(h.windDir, h.windMph, deer)
            val both = onWind.filter { CamActivity.clockGap(it.minuteOfDay, mid) <= CamActivity.TIME_MIN }
            checks += Check("Deer here on this wind at this time", if (split) null else both.isNotEmpty(),
                if (split) notCounted else if (both.isEmpty()) "${onWind.size} visits at ${cam.name} on this wind, none within an hour of ${U.clock(h.t + 1800, zone)}"
                else "${both.size} visit${if (both.size == 1) "" else "s"} at ${cam.name} on this wind within an hour of ${U.clock(h.t + 1800, zone)}")
            proof = (both.sortedByDescending { it.t } + onWind.sortedByDescending { it.t }).distinctBy { it.t }.map { it.photoIds.first() }
        } else {
            val b = db.buck(who)
            val name = if (b?.pool == true) "Young bucks" else b?.name ?: "He"
            val report = res.reports[who]
            val his = report?.let { HisWinds.of(it) }.orEmpty()
            val m = if (split) emptyList() else HisWinds.matching(h.windDir, h.windMph, his)
            checks += Check("$name's wind", if (split) null else m.isNotEmpty(),
                if (split) notCounted else "Came on this wind ${m.size} of ${his.size} times" + if (m.isNotEmpty()) " (" + m.joinToString(", ") { DAY.format(Instant.ofEpochSecond(it.t).atZone(zone)) } + ")" else "")
            val visits = report?.visits.orEmpty()
            val here = visits.count { it.cameraId == camId }
            checks += Check("Seen at ${cam.name}", here > 0, if (here > 0) "$here of ${visits.size} visits here" else "Not seen at ${cam.name} yet")
            val atTime = visits.filter { v -> CamActivity.clockGap(Instant.ofEpochSecond(v.start).atZone(zone).let { it.hour * 60 + it.minute }, mid) <= CamActivity.TIME_MIN }
            checks += Check("His time of day", atTime.isNotEmpty(), "${atTime.size} of ${visits.size} visits within an hour of ${U.clock(h.t + 1800, zone)}")
            proof = db.photosOf(who).filter { p -> m.any { v -> p.epochSec?.let { kotlin.math.abs(it - v.t) < 3600 } == true } }.map { it.id }
        }
        // 5. A weather trigger the mentors agree on (cold front, temperature drop, wind drop, light rain...).
        val plus = mentors.filter { it.plus }; val minus = mentors.filter { !it.plus }
        checks += Check(
            "Weather", if (minus.isNotEmpty()) false else plus.isNotEmpty(),
            when {
                minus.isNotEmpty() -> "Against: " + minus.joinToString(", ") { it.label }
                plus.isNotEmpty() -> plus.joinToString(", ") { it.label }
                else -> "No cold front, temperature drop, wind drop or light rain"
            },
        )
        // 6. The two forecasts agree on the wind.
        val mc = WindCheck.of(h)
        checks += Check(
            "Forecasts agree", when (mc) { ModelCheck.AGREE -> true; ModelCheck.SPLIT -> false; ModelCheck.ONE -> null },
            when (mc) {
                ModelCheck.AGREE -> "NOAA and ECMWF agree on the wind"
                ModelCheck.SPLIT -> "${com.kairos.ui.splitWords(h)}. Check before you go"
                ModelCheck.ONE -> "Too far out for a second forecast"
            },
        )
        Judged(camId, w.kind, w.start, w.end, h, checks, proof)
    }
}

private fun lanesFor(res: BuckAnalysis.Result, camId: String, sectors: Set<Int>, hours: List<WxHour>, zone: ZoneId): List<HourLane> {
    val db = BuckStore.db
    val ok = hours.filter { WindCheck.of(it) != ModelCheck.SPLIT }
    val deer = res.deerVisits[camId].orEmpty()
    return listOfNotNull(
        sectors.takeIf { it.isNotEmpty() }?.let { s -> HourLane("Your wind", Accent.fern, ok.filter { StandWind.fits(s, it.windDir, it.windMph) == true }.map { it.t }.toSet()) },
        deer.takeIf { it.isNotEmpty() }?.let {
            HourLane(
                "All deer", KairosColors.Text.copy(alpha = 0.85f),
                ok.filter { h -> CamActivity.windMatches(h.windDir, h.windMph, deer).isNotEmpty() }.map { it.t }.toSet(),
                ok.filter { h -> CamActivity.windAndTime(h, zone, deer).isNotEmpty() }.map { it.t }.toSet(),
            )
        },
    ) + db.bucks.sortedWith(compareBy<Buck> { it.pool }.thenByDescending { it.star }).mapNotNull { b ->
        val his = res.reports[b.id]?.let { HisWinds.of(it) }.orEmpty()
        if (his.isEmpty()) null
        else HourLane(if (b.pool) "Young" else b.name, buckColor(b.colorIdx), ok.filter { HisWinds.matching(it.windDir, it.windMph, his).isNotEmpty() }.map { it.t }.toSet())
    }
}

// ------------------------------------------------------------------ the week

private val DOW = DateTimeFormatter.ofPattern("EEE")
private val DAY = DateTimeFormatter.ofPattern("EEE M/d")
private val DAY_W = 52.dp
private val CELL_H = 64.dp

/** 7 rows (today first), Morning | Evening; each cell = the percent, the wind, the camera. */
@Composable
private fun WeekGrid(cells: List<Cell>, today: LocalDate, now: Long, onOpen: (Cell) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Spacer(Modifier.width(DAY_W))
            Text("Morning", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Spacer(Modifier.width(8.dp))
            Text("Evening", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        for (i in 0 until 7) {
            val d = today.plusDays(i.toLong())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(DAY_W)) {
                    Text(if (i == 0) "Today" else DOW.format(d), color = if (i == 0) Accent.teal else KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text("${d.monthValue}/${d.dayOfMonth}", color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall)
                }
                for (k in SitKind.entries) {
                    if (k == SitKind.EVENING) Spacer(Modifier.width(8.dp))
                    val c = cells.firstOrNull { it.day == d && it.kind == k }
                    Box(Modifier.weight(1f)) {
                        if (c == null) Box(
                            Modifier.fillMaxWidth().height(CELL_H).clip(RoundedCornerShape(16.dp)).border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (i == 0) "Done" else "No forecast", color = KairosColors.Faint, style = MaterialTheme.typography.labelMedium) }
                        else SitCell(c, now) { onOpen(c) }
                    }
                }
            }
        }
    }
}

/** The percent big; the wind (drawn against the camera's view) and the camera small. Green = good. */
@Composable
private fun SitCell(c: Cell, now: Long, onTap: () -> Unit) {
    val db = BuckStore.db
    val j = c.best
    Row(
        Modifier.fillMaxWidth().height(CELL_H).clip(RoundedCornerShape(16.dp))
            .background(j.wash)
            .clickable(onClick = onTap).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val calm = (j.hour.windMph ?: 0.0) < HisWinds.CALM_MPH
        MiniWindDial(if (calm) null else j.hour.windDir, null, size = 34.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(if (j.unsure) "?" else "${j.pct}%", color = j.tint, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                (if (j.start <= now) "Now · " else "") + windShort(j.hour),
                color = if (j.start <= now) Accent.teal else KairosColors.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ one sit

/**
 * A sit: the percent, the wind at the stand drawn, the checklist (each line yes / no / doesn't
 * apply, with what it read), and the photos behind it. A switch shows the same sit at the other
 * camera.
 */
@Composable
private fun SitSheet(cell: Cell, who: String?, zone: ZoneId, onClose: () -> Unit, onSetWind: (String) -> Unit) {
    val db = BuckStore.db
    val nav = com.kairos.ui.LocalAppNav.current
    val j = cell.best
    val cam = db.camera(j.camId)
    val h = j.hour
    val split = WindCheck.of(h) == ModelCheck.SPLIT
    val calm = (h.windMph ?: 0.0) < HisWinds.CALM_MPH || h.windDir == null
    val sectors = cam?.windSectors.orEmpty()
    val ok = if (split || calm) null else StandWind.fits(sectors, h.windDir, h.windMph)

    Takeover(onClose, title = "${dayWord(cell.day, LocalDate.now(zone))} ${cell.day.monthValue}/${cell.day.dayOfMonth} · ${cell.kind.label}") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (j.unsure) "?" else "${j.pct}%", color = j.tint, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 6.dp)) {
                val used = j.checks.filter { it.met != null }
                Text(if (j.unsure) "The forecasts disagree on the wind" else "${used.count { it.met == true }} of ${used.size} line up", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${cam?.name} · ${U.clock(j.start, zone)} to ${U.clock(j.end, zone)}" + (who?.let { " · for ${db.buck(it)?.let { b -> if (b.pool) "young bucks" else b.name }}" } ?: ""), color = KairosColors.Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StandWindDial(null, sectors, h.windDir, h.windMph, ok, h.altDir, h.altMph, split, size = 200.dp)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DialKey(sectors.isNotEmpty(), !calm, ok, false) }
        Text(
            listOfNotNull(
                "At ${U.clock(h.t, zone).replace(":00", "")}: " + (if (calm) "calm" else "${U.compass(h.windDir!!)} ${U.speed(h.windMph!!)}"),
                h.tempF?.let { U.temp(it) }, h.precipProb?.let { "${it.roundToInt()}% rain" },
            ).joinToString("  ·  "),
            color = KairosColors.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
        )

        // The checklist.
        Spacer(Modifier.height(14.dp))
        j.checks.forEach { c ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                val (mark, col) = when (c.met) { true -> "✓" to Accent.fern; false -> "✗" to Accent.brick; null -> "–" to KairosColors.Faint }
                Text(mark, color = col, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.label, color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(c.detail, color = KairosColors.Dim, style = MaterialTheme.typography.bodySmall)
                    if (c.label == "Your wind" && sectors.isEmpty() && cam != null) Text(
                        "Set it", color = Accent.teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSetWind(cam.id) }.padding(vertical = 4.dp),
                    )
                }
            }
        }
        // The photos behind it.
        if (j.proof.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(if (who == null) "Deer here on this wind" else "His visits on this wind", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(j.proof.size, key = { j.proof[it] }) { i ->
                    val id = j.proof[i]
                    val p = db.photo(id)
                    Column(Modifier.width(104.dp).clickable { nav.openPhotos(j.proof, i) }) {
                        Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(10.dp))) { PhotoThumb(id, Modifier.fillMaxSize()) }
                        Text(p?.let { whoLabel(it) } ?: "", color = KairosColors.Text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p?.epochSec?.let { DAY.format(Instant.ofEpochSecond(it).atZone(zone)) + " " + U.clock(it, zone) } ?: "", color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SecondaryPill("Watch this wind on the map", { onClose(); nav.openMap(h.t, who) }, Modifier.fillMaxWidth())
        if (sectors.isNotEmpty() && cam != null) Text(
            "Change your wind for ${cam.name}", color = Accent.teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).clickable { onSetWind(cam.id) }.padding(8.dp),
        )
    }
}

// ------------------------------------------------------------------ hour by hour

/** Under the hour's numbers: every deer this camera caught on this hour's wind, newest first. */
@Composable
private fun DeerOnThisWind(h: WxHour, camName: String, deer: List<DeerVisit>, zone: ZoneId) {
    if (deer.isEmpty()) return
    if (WindCheck.of(h) == ModelCheck.SPLIT) {
        val noaa = CamActivity.windMatches(h.windDir, h.windMph, deer).sortedByDescending { it.t }
        val ecmwf = CamActivity.windMatches(h.altDir, h.altMph, deer).sortedByDescending { it.t }
        Spacer(Modifier.height(12.dp))
        Text("If NOAA is right (${com.kairos.ui.windWords(h.windDir, h.windMph)}): ${noaa.size} of ${deer.size} deer visits at $camName", color = KairosColors.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        DeerThumbs(noaa, zone)
        Spacer(Modifier.height(10.dp))
        Text("If ECMWF is right (${com.kairos.ui.windWords(h.altDir, h.altMph)}): ${ecmwf.size} of ${deer.size}", color = KairosColors.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        DeerThumbs(ecmwf, zone)
        return
    }
    val onWind = CamActivity.windMatches(h.windDir, h.windMph, deer).sortedByDescending { it.t }
    Spacer(Modifier.height(12.dp))
    Text(
        if (onWind.isEmpty()) "No deer at $camName on this wind yet (${deer.size} visits on other winds)" else "Deer at $camName on this wind: ${onWind.size} of ${deer.size} visits",
        color = KairosColors.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
    )
    DeerThumbs(onWind, zone)
}

@Composable
private fun DeerThumbs(visits: List<DeerVisit>, zone: ZoneId) {
    if (visits.isEmpty()) return
    val nav = com.kairos.ui.LocalAppNav.current
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(visits.size, key = { visits[it].t }) { i ->
            val v = visits[i]
            Column(Modifier.width(104.dp).clickable { nav.openPhotos(visits.map { it.photoIds.first() }, i) }) {
                Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(10.dp))) { PhotoThumb(v.photoIds.first(), Modifier.fillMaxSize()) }
                Text(v.labels.joinToString(", "), color = KairosColors.Text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(DAY.format(Instant.ofEpochSecond(v.t).atZone(zone)) + " " + U.clock(v.t, zone), color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
