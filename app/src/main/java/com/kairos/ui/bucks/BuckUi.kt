package com.kairos.ui.bucks

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.bucks.BuckPrefs
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Compass
import com.kairos.bucks.Photo
import com.kairos.bucks.PhotoImporter
import com.kairos.bucks.PhotoLabel
import com.kairos.bucks.PressUnit
import com.kairos.bucks.RainUnit
import com.kairos.bucks.TempUnit
import com.kairos.bucks.TimeSource
import com.kairos.bucks.U
import com.kairos.bucks.WindUnit
import com.kairos.ui.KairosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------------ takeover

/**
 * A pop-up over the screen with everything behind it grayed out: for anything that doesn't need
 * its own page (sorting a photo, fixing its time, a weather close-up). Tap outside or X to close.
 */
@Composable
internal fun Takeover(onDismiss: () -> Unit, title: String? = null, maxHeight: Dp = 720.dp, content: @Composable ColumnScope.() -> Unit) {
    // A card opened from inside this pop-up (a photo's details) goes over it; back brings it back,
    // scrolled where it was (the scroll lives out here, so hiding doesn't reset it).
    val scroll = rememberScrollState()
    if (com.kairos.ui.LocalCovered.current) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onDismiss() }.systemBarsPadding().padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = maxHeight).clip(RoundedCornerShape(28.dp)).background(KairosColors.Surface)
                    .pointerInput(Unit) { detectTapGestures { } } // taps inside don't close it
                    .verticalScroll(scroll).padding(20.dp),
            ) {
                // A Dialog starts a new composition: without this, untinted text falls back to black
                // (seen: the Similar weather title, opened over the black photo viewer).
                CompositionLocalProvider(LocalContentColor provides KairosColors.Text) {
                    if (title != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, color = KairosColors.Text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = KairosColors.Dim) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    content()
                }
            }
        }
    }
}

// ------------------------------------------------------------------ wind arrow

/**
 * Which way the wind blows, drawn the way it moves: a small open circle on the side it comes
 * FROM, a line, and the point on the side it blows TOWARD. A north wind = circle at the top,
 * point at the bottom. Direction is meteorological (from), as Open-Meteo / HRRR give it.
 */
@Composable
internal fun WindArrow(fromDeg: Double, modifier: Modifier = Modifier.size(22.dp), color: Color = KairosColors.Text) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 1.dp.toPx()
        val to = Math.toRadians(fromDeg + 180)
        val ux = sin(to).toFloat(); val uy = -cos(to).toFloat() // screen vector toward where it blows
        val dot = 2.6.dp.toPx()
        val tail = Offset(c.x - ux * (r - dot), c.y - uy * (r - dot))
        val tip = Offset(c.x + ux * r, c.y + uy * r)
        val headLen = r * 0.62f
        val back = Offset(tip.x - ux * headLen, tip.y - uy * headLen)
        val px = -uy; val py = ux
        val w = headLen * 0.52f
        val stroke = 1.7.dp.toPx()
        drawLine(color, Offset(tail.x + ux * dot, tail.y + uy * dot), back, stroke)
        drawCircle(color, dot, tail, style = Stroke(stroke))
        drawPath(Path().apply {
            moveTo(tip.x, tip.y); lineTo(back.x + px * w, back.y + py * w); lineTo(back.x - px * w, back.y - py * w); close()
        }, color)
    }
}

/** Arrow + "N 5 mph". "Calm" with no arrow under 2 mph. */
@Composable
internal fun WindTag(dir: Double?, mph: Double?, color: Color = KairosColors.Text, big: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dir != null && mph != null && mph >= 2) {
            WindArrow(dir, Modifier.size(if (big) 26.dp else 18.dp), color)
            Spacer(Modifier.width(6.dp))
        }
        Text(U.wind(dir, mph), color = color, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ hold to act

/** Tap opens, press-and-hold acts (with a buzz), so a quick tap can't mis-sort anything. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.tapHold(onTap: () -> Unit, onHold: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.combinedClickable(onClick = onTap, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onHold() })
}

// ------------------------------------------------------------------ sort a photo

/**
 * The rest of this visit: the SAME deer (same tag) at the same camera, each photo within the visit
 * window of the next. Two deer minutes apart are two visits; time alone never joins them.
 */
internal fun visitMates(p: Photo): List<Photo> {
    val t = p.epochSec ?: return emptyList()
    val cam = BuckStore.db.photos.filter {
        it.cameraId == p.cameraId && it.epochSec != null && it.id != p.id && it.label == p.label && it.buckIds == p.buckIds
    }.sortedBy { it.epochSec }
    val out = ArrayList<Photo>()
    var lo = t; var hi = t
    var grew = true
    while (grew) {
        grew = false
        for (q in cam) if (q !in out && q.epochSec!! in (lo - BuckPrefs.visitGapSec)..(hi + BuckPrefs.visitGapSec)) {
            out += q; lo = minOf(lo, q.epochSec!!); hi = maxOf(hi, q.epochSec!!); grew = true
        }
    }
    return out
}

/**
 * "Who's in it?" for one photo (or several picked in the Gallery). Every tap applies right away
 * and shows what changed; tap again to undo. A buck chip toggles him in or out (two bucks in one
 * photo is fine); a photo left with no buck lands in Not sure.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SortSheet(ids: List<String>, onClose: () -> Unit, onSelectMore: (() -> Unit)? = null) {
    val db = BuckStore.db
    val photos = ids.mapNotNull { db.photo(it) }
    if (photos.isEmpty()) { onClose(); return }
    val one = photos.singleOrNull()
    val mates = remember(ids) { one?.let { visitMates(it) }.orEmpty() }
    // The whole visit, oldest first, so every photo it would touch is on screen before he picks.
    val visit = remember(ids) { (listOfNotNull(one) + mates).sortedBy { it.epochSec ?: 0 }.map { it.id } }
    var sel by remember(ids) { mutableStateOf(setOfNotNull(one?.id)) }
    var peek by remember(ids) { mutableStateOf(one?.id) }
    var note by remember(ids) { mutableStateOf<String?>(null) }
    val target = if (one != null) sel else photos.map { it.id }.toSet()
    val cur = target.mapNotNull { db.photo(it) }

    Takeover(onClose, title = if (one != null) "Who's in this photo?" else "Sort ${photos.size} photos") {
        if (one != null) {
            // Big enough to judge antlers: pinch to zoom.
            val shown = peek?.let { db.photo(it) } ?: one
            ZoomPhoto(shown.id, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(photoWhen(shown), db.camera(shown.cameraId)?.name, "now: " + whoLabel(shown)).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim,
            )
            if (visit.size > 1) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "THIS VISIT · SAME DEER, ${visit.size} PHOTOS", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold,
                )
                Text("Tap a photo to see it big. Tap its circle to include it.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val w = (maxWidth - 14.dp) / 3
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visit.forEach { id ->
                            val q = db.photo(id) ?: return@forEach
                            val inSel = id in sel
                            Column(Modifier.width(w)) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                                        .border(if (id == peek) 2.5.dp else 0.dp, if (id == peek) Accent.teal else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable { peek = id },
                                ) {
                                    PhotoThumb(id, Modifier.fillMaxSize())
                                    if (!inSel) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                                    Box(
                                        Modifier.align(Alignment.TopEnd).size(34.dp).clickable { sel = if (inSel) (sel - id).ifEmpty { sel } else sel + id }.padding(6.dp)
                                            .clip(CircleShape).background(if (inSel) Accent.fern else Color.Black.copy(alpha = 0.4f)).border(1.5.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { if (inSel) Icon(Icons.Filled.Check, "Included", tint = Accent.on(Accent.fern), modifier = Modifier.size(14.dp)) }
                                }
                                Text(
                                    q.capture?.let { U.clock(it) } ?: "?", style = MaterialTheme.typography.labelSmall,
                                    color = if (inSel) KairosColors.Text else KairosColors.Dim, fontWeight = if (id == one.id) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(whoLabel(q).replace("Young bucks (spikes & small racks)", "Young bucks"), style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val idx = when (sel) { setOf(one.id) -> 0; visit.toSet() -> 1; else -> -1 }
                Seg(listOf("Just this one", "All ${visit.size}"), idx) { sel = if (it == 0) setOf(one.id) else visit.toSet() }
                if (idx == -1) Text("${sel.size} of ${visit.size} selected", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("BUCKS", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val bucks = db.bucks.filter { !it.pool } + listOfNotNull(db.bucks.firstOrNull { it.pool })
            bucks.forEach { b ->
                val on = cur.isNotEmpty() && cur.all { b.id in it.buckIds }
                val name = if (b.pool) "Young bucks" else b.name
                ToggleChip(name, on, buckColor(b.colorIdx)) {
                    val added = BuckStore.toggleBuck(target, b.id)
                    note = if (added) "Added to $name." else "Not $name." + if (BuckStore.db.photos.filter { it.id in target }.any { it.label == PhotoLabel.DEER_UNKNOWN }) " Now in Not sure." else ""
                }
            }
            if (db.bucks.none { it.pool }) ToggleChip("Young bucks", false, null) {
                val p = BuckStore.pool(); BuckStore.toggleBuck(target, p.id); note = "Added to Young bucks."
            }
            ActionChip("New buck", Icons.Outlined.Add) {
                val b = BuckStore.addBuck(); BuckStore.setWho(target, PhotoLabel.BUCK, listOf(b.id)); note = "Started ${b.name}. Rename him on his page."
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("OR", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fun all(l: PhotoLabel) = cur.isNotEmpty() && cur.all { it.label == l && (l != PhotoLabel.DEER_UNKNOWN || it.buckIds.isEmpty()) }
            ToggleChip("A buck, not sure which", all(PhotoLabel.DEER_UNKNOWN), null) { BuckStore.setWho(target, PhotoLabel.DEER_UNKNOWN, emptyList()); note = "In Not sure." }
            ToggleChip("Doe / fawn", all(PhotoLabel.DOE), null) { BuckStore.setWho(target, PhotoLabel.DOE, emptyList()); note = "Marked doe / fawn." }
            ToggleChip("Nothing", all(PhotoLabel.NO_DEER), null) { BuckStore.setWho(target, PhotoLabel.NO_DEER, emptyList()); note = "Marked nothing." }
            ToggleChip("Back to Sort", all(PhotoLabel.UNSORTED), null) { BuckStore.setWho(target, PhotoLabel.UNSORTED, emptyList()); note = "Back in Sort." }
        }
        note?.let {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Accent.soft(Accent.fern)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = Accent.fern, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("$it Tap again to undo.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            onSelectMore?.let { SecondaryPill("Select more", it, Modifier.weight(1f)) }
            PrimaryPill("Done", onClose, Modifier.weight(1f))
        }
    }
}

internal fun whoLabel(p: Photo): String {
    val db = BuckStore.db
    val names = p.buckIds.mapNotNull { db.buck(it) }.map { if (it.pool) "Young bucks" else it.name }
    return names.joinToString(" + ").ifEmpty {
        when (p.label) {
            PhotoLabel.DEER_UNKNOWN -> "Not sure"
            else -> p.label.label
        }
    }
}

@Composable
internal fun ToggleChip(text: String, on: Boolean, dot: Color?, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (on) Accent.fern else KairosColors.Surface2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) { Icon(Icons.Filled.Check, null, tint = Accent.on(Accent.fern), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        else dot?.let { Box(Modifier.size(9.dp).background(it, CircleShape)); Spacer(Modifier.width(7.dp)) }
        Text(text, color = if (on) Accent.on(Accent.fern) else KairosColors.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ActionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).border(1.dp, KairosColors.Line, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Accent.teal, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
        Text(text, color = KairosColors.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** A small segmented control. */
@Composable
internal fun Seg(options: List<String>, selected: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().clip(CircleShape).background(KairosColors.Surface2).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (on) Accent.fern else Color.Transparent).clickable { onPick(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Accent.on(Accent.fern) else KairosColors.Dim, maxLines = 1) }
        }
    }
}

// ------------------------------------------------------------------ fix a photo's info

private val DATE = DateTimeFormatter.ofPattern("EEE MMM d, yyyy")

/**
 * Fix what was read off a photo: date, time, the strip temperature, the camera. The strip is
 * shown blown up right above, so it can be read by eye. Saved values are marked "set by hand"
 * and kept for good (a later re-read never touches them).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditPhotoSheet(id: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = BuckStore.db
    val p = db.photo(id) ?: run { onClose(); return }
    var date by remember(id) { mutableStateOf(p.capture?.toLocalDate() ?: LocalDate.now()) }
    var time by remember(id) { mutableStateOf(p.capture?.toLocalTime()?.withSecond(0) ?: LocalTime.of(12, 0)) }
    val toUnit = { f: Int -> if (BuckPrefs.temp == TempUnit.C) ((f - 32) * 5 / 9.0).roundToInt() else f }
    val fromUnit = { v: Int -> if (BuckPrefs.temp == TempUnit.C) (v * 9 / 5.0 + 32).roundToInt() else v }
    var temp by remember(id) { mutableStateOf(p.stripTempF?.let { toUnit(it).toString() } ?: "") }
    var cam by remember(id) { mutableStateOf(p.cameraId) }
    var picking by remember { mutableStateOf(0) } // 1 date, 2 time
    var reading by remember { mutableStateOf<String?>(null) }

    Takeover(onClose, title = "Fix this photo's info") {
        Text("The strip, up close", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        // Bottom ~11% of the frame, full width: the camera's own date / time / temp.
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 1.7f).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
            PhotoThumb(id, Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth, alignment = Alignment.BottomCenter, full = true)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Date", DATE.format(date), Modifier.weight(1.4f)) { picking = 1 }
            Field("Time", U.clock(time), Modifier.weight(1f)) { picking = 2 }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            temp, { temp = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
            label = { Text("Temp on the strip (${BuckPrefs.temp.label})") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        if (db.cameras.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Camera", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                db.cameras.forEach { c -> ToggleChip(c.name, cam == c.id, null) { cam = c.id } }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = {
            reading = "Reading…"
            scope.launch {
                val r = withContext(Dispatchers.IO) { PhotoImporter.readOne(context, id) }
                if (r?.time != null) { date = r.time!!.toLocalDate(); time = r.time!!.toLocalTime().withSecond(0) }
                r?.tempF?.let { temp = toUnit(it).toString() }
                reading = if (r?.time == null) "Couldn't read the strip. Type it in from the close-up." else "Filled in from a fresh read. Check it against the close-up, then Save."
            }
        }) { Text("Read the strip again", color = Accent.teal) }
        reading?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Was: ${photoWhen(p)}" + (p.stripTempF?.let { ", ${U.temp(it.toDouble())}" } ?: "") + "  ·  ${p.timeSource.label.lowercase()}",
            style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryPill("Cancel", onClose, Modifier.weight(1f))
            PrimaryPill("Save", {
                val newT = LocalDateTime.of(date, time)
                val newTemp = temp.toIntOrNull()?.let(fromUnit)
                BuckStore.editPhotos(setOf(id)) {
                    val timeChanged = it.capture?.withSecond(0) != newT
                    val tempChanged = newTemp != it.stripTempF
                    it.copy(
                        capture = if (timeChanged) newT else it.capture,
                        timeSource = if (timeChanged) TimeSource.MANUAL else it.timeSource,
                        timeCheck = false,
                        stripTempF = newTemp,
                        tempManual = it.tempManual || tempChanged,
                        cameraId = cam, cameraAuto = if (cam != it.cameraId) false else it.cameraAuto,
                    )
                }
                onClose()
            }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text("Saved changes are kept for good. A re-read never overwrites them.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
    }

    if (picking == 1) {
        val ds = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = 0 },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = 0
                }) { Text("OK") }
            },
        ) { DatePicker(ds) }
    }
    if (picking == 2) {
        val ts = rememberTimePickerState(time.hour, time.minute, is24Hour = BuckPrefs.clock24)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = 0 },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(ts.hour, ts.minute); picking = 0 }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picking = 0 }) { Text("Cancel") } },
            text = { TimePicker(ts) },
        )
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, KairosColors.Line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ units

/** Every unit choice in one place: first open asks once, Settings uses the same rows. */
@Composable
internal fun UnitRows() {
    UnitRow("Temperature", TempUnit.entries.map { it.label }, BuckPrefs.temp.ordinal) { BuckPrefs.pickTemp(TempUnit.entries[it]) }
    UnitRow("Wind speed", WindUnit.entries.map { it.label }, BuckPrefs.wind.ordinal) { BuckPrefs.pickWind(WindUnit.entries[it]) }
    UnitRow("Pressure", PressUnit.entries.map { it.label }, BuckPrefs.press.ordinal) { BuckPrefs.pickPress(PressUnit.entries[it]) }
    UnitRow("Rain", RainUnit.entries.map { it.label }, BuckPrefs.rain.ordinal) { BuckPrefs.pickRain(RainUnit.entries[it]) }
    UnitRow("Wind direction", listOf("N, NW (8)", "N, NNW (16)"), BuckPrefs.compass.ordinal) { BuckPrefs.pickCompass(Compass.entries[it]) }
    UnitRow("Clock", listOf("1:30 PM", "13:30"), if (BuckPrefs.clock24) 1 else 0) { BuckPrefs.pickClock24(it == 1) }
}

@Composable
internal fun UnitRow(label: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = KairosColors.Dim, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Seg(options, selected, onPick = onPick)
    }
}

/** First open of Bucks: pick units once (defaults already set; one tap to keep them). */
@Composable
internal fun UnitsFirstRun(onDone: () -> Unit) {
    Takeover({ BuckPrefs.markUnitsAsked(); onDone() }, title = "Your units") {
        Text("How should Kairos show the weather? Change it anytime in Bucks settings.", color = KairosColors.Dim)
        Spacer(Modifier.height(10.dp))
        UnitRows()
        Spacer(Modifier.height(16.dp))
        PrimaryPill("Looks good", { BuckPrefs.markUnitsAsked(); onDone() }, Modifier.fillMaxWidth())
    }
}

