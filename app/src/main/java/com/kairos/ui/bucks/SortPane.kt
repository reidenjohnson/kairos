package com.kairos.ui.bucks

import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairos.bucks.Buck
import com.kairos.bucks.BuckDb
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Photo
import com.kairos.bucks.PhotoImporter
import com.kairos.bucks.PhotoLabel
import com.kairos.bucks.TimeSource
import com.kairos.engine.bucks.PhotoMath
import com.kairos.engine.bucks.StripParser
import com.kairos.ui.KairosColors
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Sort by VISIT, not by photo: photos from one camera within 30 minutes are one card and one
 * answer. Each card asks a single bold yes/no question with its photos as a swipeable hero:
 *   time (only if the photo data and strip disagree) -> camera (only until the view is learned)
 *   -> "Buck, doe, or nothing?" -> "Is this Buck 1?" with his best photo right under it.
 * Grid mode tags many at once.
 */

/** Unsorted photos grouped into visits: same camera (or, before a camera is known, the same view) within 30 min. */
internal fun visitGroups(db: BuckDb): List<List<Photo>> {
    val out = ArrayList<MutableList<Photo>>()
    for (p in db.unsorted) {
        val last = out.lastOrNull()?.last()
        val t = p.epochSec
        val sameVisit = last != null && t != null && last.epochSec != null && t - last.epochSec!! <= com.kairos.bucks.BuckPrefs.visitGapSec &&
            !p.timeCheck && !last.timeCheck &&
            (if (p.cameraId != null || last.cameraId != null) p.cameraId == last.cameraId else similar(p, last))
        if (sameVisit) out.last() += p else out += mutableListOf(p)
    }
    return out
}

private fun similar(a: Photo, b: Photo): Boolean =
    PhotoMath.sceneSimilarity(Base64.decode(a.scene, Base64.NO_WRAP), Base64.decode(b.scene, Base64.NO_WRAP)) >= 0.2

@Composable
internal fun SortPane(onDone: () -> Unit) {
    val db = BuckStore.db
    val skipped = remember { mutableStateListOf<String>() }
    val undo = remember { mutableStateListOf<BuckDb>() }
    var toast by remember { mutableStateOf<String?>(null) }
    var grid by remember { mutableStateOf(false) }
    // "Not all the same deer?" splits a visit into one card per photo.
    var singles by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(listOf<String>()) }
    val groups = visitGroups(db).flatMap { g -> if (g.size > 1 && g.any { it.id in singles }) g.map { listOf(it) } else listOf(g) }
        .filter { g -> g.none { it.id in skipped } }
    val total = remember { mutableIntStateOf(0) }.also { if (groups.size > it.intValue) it.intValue = groups.size }
    val snapshot = { undo += BuckStore.db; if (undo.size > 30) undo.removeAt(0) }

    Column(Modifier.fillMaxSize()) {
        // Top bar: progress, undo, grid toggle.
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (groups.isEmpty()) "All sorted" else "${groups.size} visit${if (groups.size == 1) "" else "s"} to sort", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (total.intValue > 0) {
                    Spacer(Modifier.height(6.dp))
                    val done = 1f - groups.size.toFloat() / total.intValue
                    Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(CircleShape).background(KairosColors.Surface2)) {
                        Box(Modifier.fillMaxWidth(done.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(Accent.amber))
                    }
                }
            }
            if (undo.isNotEmpty()) IconButton(onClick = { val prev = undo.removeAt(undo.lastIndex); BuckStore.update { prev }; toast = "Undone." }) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
            }
            IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView, if (grid) "Cards" else "Grid") }
        }
        toast?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

        if (grid) {
            GridMode(db, before = snapshot) { toast = it }
            return@Column
        }
        val g = groups.firstOrNull()
        if (g == null) {
            Done(skipped.size, onDone)
            return@Column
        }
        AnimatedContent(g, contentKey = { it.first().id }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "visit") { g ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                key(g.first().id) {
                    val p = g.first()
                    when {
                        p.timeCheck -> TimeStep(p, g, before = snapshot)
                        g.any { it.cameraId == null } -> CameraStep(g, before = snapshot)
                        else -> WhoStep(
                            g, db, before = snapshot,
                            onSplit = { singles = singles + g.map { it.id } },
                        ) { toast = it }
                    }
                }
                TextButton(onClick = { skipped += g.map { it.id } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Skip for now", color = KairosColors.Dim)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Done(skipped: Int, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(Accent.brush(Accent.fern), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Accent.on(Accent.fern), modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("All sorted.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            if (skipped == 0) "Your bucks are patterned and ready." else "$skipped skipped; they'll be back next time.",
            color = KairosColors.Dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryPill("See my bucks", onDone)
    }
}

// ---------------------------------------------------------------- building blocks

/**
 * A set of photos as one swipeable hero: swipe (or tap a thumbnail) to see every shot in the
 * set. [label] names what you're looking at ("THIS VISIT", "BUCK 1"); the caption follows the
 * photo on screen.
 */
@Composable
internal fun VisitHero(
    g: List<Photo>,
    db: BuckDb,
    label: String? = null,
    labelColor: Color = Accent.teal,
    /** Tap a photo: open its page. */
    onOpen: ((String) -> Unit)? = null,
    /** Hold a photo, or the Sort button: sort that photo. */
    onSort: ((String) -> Unit)? = null,
) {
    val pager = rememberPagerState { g.size }
    val scope = rememberCoroutineScope()
    val cur = g.getOrElse(pager.currentPage) { g.first() }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                ZoomPhoto(g[i].id, Modifier.fillMaxSize(), onTap = onOpen?.let { f -> { f(g[i].id) } }, onHold = onSort?.let { f -> { f(g[i].id) } })
            }
            if (onSort != null) {
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onSort(cur.id) }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Not right? Sort", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            // Caption at the top: the camera's own info strip runs along the bottom.
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label?.let {
                    Box(Modifier.clip(CircleShape).background(labelColor).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(it, color = Accent.on(labelColor), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(photoWhen(cur), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    val bits = listOfNotNull(db.camera(cur.cameraId)?.name, cur.stripTempF?.let { com.kairos.bucks.U.temp(it.toDouble()) })
                    if (bits.isNotEmpty()) Text(bits.joinToString("  ·  "), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
                if (g.size > 1) {
                    Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("${pager.currentPage + 1} / ${g.size}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (g.size > 1) {
            Spacer(Modifier.height(8.dp))
            // Fixed-size thumbs that scroll: never squeezed, however many photos he has.
            val strip = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(pager.currentPage) { strip.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0)) }
            androidx.compose.foundation.lazy.LazyRow(state = strip, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(g.size, key = { g[it].id }) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier.width(72.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                            .then(if (on) Modifier.border(2.dp, labelColor, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                    ) { PhotoThumb(g[i].id, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

@Composable
internal fun Ask(title: String, sub: String? = null) {
    Spacer(Modifier.height(22.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = KairosColors.Text)
    sub?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim) }
    Spacer(Modifier.height(16.dp))
}

@Composable
internal fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color? = null) {
    Box(
        modifier.height(56.dp).clip(CircleShape).background(accent?.let { Accent.brush(it) } ?: Accent.brush(Accent.fern)).clickable(onClick = onClick).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = accent?.let { Accent.on(it) } ?: Accent.on(Accent.fern), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

@Composable
internal fun SecondaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(56.dp).clip(CircleShape).background(KairosColors.Surface2).clickable(onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = KairosColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}

@Composable
internal fun YesNo(onYes: () -> Unit, onNo: () -> Unit, yes: String = "Yes", no: String = "No") {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryPill(yes, onYes, Modifier.weight(1f))
        SecondaryPill(no, onNo, Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- time (rare)

private val DAY = DateTimeFormatter.ofPattern("EEE MMM d")
private fun fmt(t: java.time.LocalDateTime) = DAY.format(t) + ", " + com.kairos.bucks.U.clock(t)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeStep(p: Photo, g: List<Photo>, before: () -> Unit) {
    val db = BuckStore.db
    VisitHero(listOf(p), db, label = "THIS PHOTO")
    val strip = p.stripText?.let { StripParser.parse(it).time }
    var manual by remember { mutableStateOf(false) }
    if (p.capture != null && strip != null && !manual) {
        Ask("Which time is right?", "The photo's data and the strip on the image disagree. Pinch the strip to check.")
        YesNo(
            yes = fmt(p.capture), no = fmt(strip),
            onYes = { before(); BuckStore.editPhotos(setOf(p.id)) { it.copy(timeCheck = false) } },
            onNo = { before(); BuckStore.editPhotos(setOf(p.id)) { it.copy(capture = strip, timeSource = TimeSource.STRIP, timeCheck = false) } },
        )
        TextButton(onClick = { manual = true }) { Text("Neither", color = KairosColors.Dim) }
        return
    }
    Ask("When was this taken?", "No time on this one. Pinch the strip at the bottom to read it.")
    var step by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val ds = rememberDatePickerState()
    val ts = rememberTimePickerState()
    PrimaryPill("Set date and time", { step = 1 }, Modifier.fillMaxWidth())
    if (step == 1) {
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    date = ds.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    step = if (date != null) 2 else 0
                }) { Text("Next") }
            },
        ) { DatePicker(ds) }
    }
    if (step == 2) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalDateTime.of(date!!, java.time.LocalTime.of(ts.hour, ts.minute))
                    before()
                    BuckStore.editPhotos(setOf(p.id)) { it.copy(capture = t, timeSource = TimeSource.MANUAL, timeCheck = false) }
                    step = 0
                }) { Text("Save") }
            },
            text = { TimePicker(ts) },
        )
    }
}

// ---------------------------------------------------------------- camera (until learned)

@Composable
private fun CameraStep(g: List<Photo>, before: () -> Unit) {
    val db = BuckStore.db
    val p = g.first()
    VisitHero(g, db, label = "THIS VISIT")
    val ranked = remember(p.id, db.cameras.size) { PhotoImporter.rankCameras(p.scene, p.night).filter { db.camera(it.first) != null } }
    var i by remember(p.id) { mutableIntStateOf(0) }
    fun assign(camId: String) {
        before()
        BuckStore.editPhotos(g.map { it.id }.toSet()) { it.copy(cameraId = camId, cameraAuto = false) }
        // Anything the learned view now matches clearly is filled in too.
        val auto = BuckStore.db.photos.filter { it.cameraId == null }.mapNotNull { q ->
            val (cam, ok) = PhotoImporter.suggestCamera(q.scene, q.night, q.model)
            if (ok && cam != null) q.id to cam else null
        }.toMap()
        if (auto.isNotEmpty()) BuckStore.editPhotos(auto.keys) { it.copy(cameraId = auto[it.id], cameraAuto = true) }
    }
    // Yes/no only for a real look-alike; otherwise one tap on the right camera's picture.
    val cand = ranked.filter { it.second >= PhotoImporter.ASK_AT }.getOrNull(i)
    if (cand != null) {
        val cam = db.camera(cand.first)!!
        Ask("${cam.name}?", "Same view as this one from ${cam.name}.")
        val ref = db.photos.filter { it.cameraId == cam.id && !it.cameraAuto && it.night == p.night }.ifEmpty { db.photos.filter { it.cameraId == cam.id } }.lastOrNull()
        ref?.let { Spacer(Modifier.height(4.dp)); VisitHero(listOf(it), db, label = cam.name.uppercase(), labelColor = Accent.fern); Spacer(Modifier.height(16.dp)) }
        YesNo(onYes = { assign(cam.id) }, onNo = { i++ })
        return
    }
    val next = "Camera ${db.cameras.size + 1}"
    if (db.cameras.isEmpty()) {
        Ask("Your first camera", "Pin it later in Cameras. After this, its view is learned and photos sort themselves.")
        PrimaryPill("Add $next", { assign(BuckStore.addCamera(next).id) }, Modifier.fillMaxWidth())
        return
    }
    Ask("Which camera?", "Night shots and a moved camera look different, so pick it once. Moved a camera? Make the new spot its own camera.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        db.cameras.forEach { c ->
            val ref = db.photos.filter { it.cameraId == c.id && !it.cameraAuto && it.night == p.night }.ifEmpty { db.photos.filter { it.cameraId == c.id } }.lastOrNull()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KairosColors.Surface).clickable { assign(c.id) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(120.dp).aspectRatio(16f / 9f)) { ref?.let { PhotoThumb(it.id, Modifier.fillMaxSize()) } }
                Spacer(Modifier.width(14.dp))
                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        PrimaryPill("New camera ($next)", { assign(BuckStore.addCamera(next).id) }, Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipRow(items: List<Pair<String, () -> Unit>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, onClick) ->
            Box(Modifier.clip(CircleShape).background(KairosColors.Surface2).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = KairosColors.Text)
            }
        }
    }
}

// ---------------------------------------------------------------- who

/** Applies one answer to photos; seeds the buck's comparison photos. */
internal fun tag(ids: Set<String>, label: PhotoLabel, buck: Buck?) {
    BuckStore.editPhotos(ids) { it.copy(label = label, buckIds = listOfNotNull(buck?.id)) }
    if (buck != null && buck.refPhotoIds.size < 4) {
        val fresh = BuckStore.db.buck(buck.id) ?: return
        BuckStore.saveBuck(fresh.copy(refPhotoIds = (fresh.refPhotoIds + ids.take(4 - fresh.refPhotoIds.size)).distinct()))
    }
}

@Composable
private fun WhoStep(
    g: List<Photo>,
    db: BuckDb,
    before: () -> Unit,
    onSplit: () -> Unit = {},
    onToast: (String) -> Unit,
) {
    val p = g.first()
    var isBuck by remember(p.id) { mutableStateOf(false) }
    var i by remember(p.id) { mutableIntStateOf(0) }
    // One answer covers the visit; if two deer are in it, the link under the photos splits it.
    VisitHero(g, db, label = "THIS VISIT")
    if (g.size > 1) TextButton(onClick = onSplit) { Text("Not all the same deer? Sort each photo", color = Accent.teal) }
    val ids = g.map { it.id }.toSet()
    fun commit(label: PhotoLabel, buck: Buck?) {
        before()
        tag(ids, label, buck)
        onToast("${buck?.name ?: label.label}: ${ids.size} photo${if (ids.size == 1) "" else "s"} saved.")
    }

    if (!isBuck) {
        Ask("What's in this visit?", if (g.size > 1) "Swipe through all ${g.size} photos first." else null)
        PrimaryPill("A buck", { isBuck = true }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryPill("Doe or fawn", { commit(PhotoLabel.DOE, null) }, Modifier.weight(1f))
            SecondaryPill("Nothing", { commit(PhotoLabel.NO_DEER, null) }, Modifier.weight(1f))
        }
        return
    }

    // Best guess first: bucks seen most at this camera, then most recently anywhere.
    val named = db.bucks.filter { !it.pool }
    val order = named.sortedWith(
        compareByDescending<Buck> { b -> db.photos.count { b.id in it.buckIds && it.cameraId == p.cameraId } }
            .thenByDescending { b -> db.photos.filter { b.id in it.buckIds }.maxOfOrNull { it.epochSec ?: 0 } ?: 0 },
    )
    val cand = order.getOrNull(i)
    when {
        cand != null -> {
            Ask("Is this ${cand.name}?", "Top is this visit, bottom is ${cand.name}. Compare brow tines, tine count and length, spread, and his body.")
            val refs = (cand.refPhotoIds + BuckStore.db.photosOf(cand.id).map { it.id }).distinct().filter { it !in ids }.take(8)
            val refPhotos = refs.mapNotNull { db.photo(it) }
            if (refPhotos.isNotEmpty()) {
                VisitHero(refPhotos, db, label = cand.name.uppercase(), labelColor = buckColor(cand.colorIdx))
                Spacer(Modifier.height(16.dp))
            }
            YesNo(yes = "Yes, ${cand.name}", no = "No", onYes = { commit(PhotoLabel.BUCK, cand) }, onNo = { i++ })
            TextButton(onClick = { commit(PhotoLabel.DEER_UNKNOWN, null) }) { Text("Can't tell", color = KairosColors.Dim) }
        }
        i == order.size -> {
            Ask("A small buck?", "Spike, fork, or a small rack. These go in your Young bucks group and get patterned together.")
            YesNo(onYes = { commit(PhotoLabel.BUCK, BuckStore.pool()) }, onNo = { i++ })
            TextButton(onClick = { commit(PhotoLabel.DEER_UNKNOWN, null) }) { Text("Can't tell", color = KairosColors.Dim) }
        }
        else -> {
            val next = "Buck ${named.size + 1}"
            Ask("A new buck worth tracking?", "He'll be $next on his own page. Rename him anytime.")
            YesNo(yes = "Yes, $next", no = "Can't tell", onYes = { commit(PhotoLabel.BUCK, BuckStore.addBuck()) }, onNo = { commit(PhotoLabel.DEER_UNKNOWN, null) })
            TextButton(onClick = { i = 0 }) { Text("Start over", color = KairosColors.Dim) }
        }
    }
}

// ---------------------------------------------------------------- grid: tag many at once

@Composable
private fun GridMode(db: BuckDb, before: () -> Unit, onToast: (String) -> Unit) {
    val sel = remember { mutableStateListOf<String>() }
    val photos = db.unsorted
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(110.dp), Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(photos, key = { it.id }) { p ->
                val on = p.id in sel
                Box(Modifier.aspectRatio(16f / 9f).clickable { if (on) sel -= p.id else sel += p.id }) {
                    PhotoThumb(p.id, Modifier.fillMaxSize())
                    if (on) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(3.dp, Accent.teal, RoundedCornerShape(8.dp)))
                    if (on) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Accent.teal, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, tint = Accent.on(Accent.teal), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        if (sel.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(KairosColors.Surface).padding(16.dp)) {
                Text("Tag ${sel.size} as", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val ids = sel.toSet()
                fun go(label: PhotoLabel, b: Buck?) { before(); tag(ids, label, b); onToast("${b?.name ?: label.label}: ${ids.size} saved."); sel.clear() }
                val missingCam = ids.any { id -> db.photo(id)?.cameraId == null }
                if (missingCam) Text("Some have no camera yet; set it in card view first for weather.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                ChipRow(
                    db.bucks.filter { !it.pool }.map { b -> b.name to { go(PhotoLabel.BUCK, b) } } +
                        listOf(
                            "Young buck" to { go(PhotoLabel.BUCK, BuckStore.pool()) },
                            "New buck" to { go(PhotoLabel.BUCK, BuckStore.addBuck()) },
                            "Doe / fawn" to { go(PhotoLabel.DOE, null) },
                            "Nothing" to { go(PhotoLabel.NO_DEER, null) },
                        ),
                )
            }
        }
    }
}

