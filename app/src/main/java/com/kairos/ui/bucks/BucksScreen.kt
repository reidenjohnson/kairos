package com.kairos.ui.bucks

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairos.bucks.Buck
import com.kairos.bucks.BuckAnalysis
import com.kairos.bucks.BuckStore
import com.kairos.bucks.BuckPrefs
import com.kairos.bucks.Camera
import com.kairos.bucks.PhotoImporter
import com.kairos.bucks.PhotoLabel
import com.kairos.data.LocationProvider
import com.kairos.engine.bucks.AgeQuiz
import com.kairos.engine.bucks.RutCalendar
import com.kairos.engine.bucks.compass8
import com.kairos.ui.KairosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.MonthDay

/** Top of the Bucks home: your newest buck photo full-bleed with the counts that matter. */
@Composable
private fun HomeHero(db: com.kairos.bucks.BuckDb, visits: Int) {
    val photo = db.bucks.flatMap { b -> db.photosOf(b.id) }.maxByOrNull { it.epochSec ?: 0 }?.id
        ?: db.photos.maxByOrNull { it.epochSec ?: 0 }?.id
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(28.dp)).background(KairosColors.Surface2)) {
        photo?.let { PhotoThumb(it, Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("YOUR GROUND", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("Bucks", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HeroStat("${db.bucks.count { !it.pool }}", "bucks", Color.White)
                HeroStat("${db.photos.size}", "photos", Accent.tealOnPhoto)
                HeroStat("$visits", "to sort", Accent.amberOnPhoto)
                HeroStat("${db.cameras.size}", "cameras", Accent.fernOnPhoto)
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, color: Color) {
    Column {
        Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

/** Setup checklist; disappears once everything is done. */
@Composable
private fun SetupCard(db: com.kairos.bucks.BuckDb, onPhotos: () -> Unit, onSort: () -> Unit, onCameras: () -> Unit) {
    val photosDone = db.photos.isNotEmpty()
    val toSort = visitGroups(db).size
    val sortDone = photosDone && toSort == 0
    val unpinned = db.cameras.count { it.lat == null || it.facingDeg == null }
    val camsDone = db.cameras.isNotEmpty() && unpinned == 0
    if (photosDone && sortDone && camsDone) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KairosColors.Surface).padding(14.dp)) {
        Text("Set up", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
        SetupRow(1, Accent.teal, "Bring in photos", if (photosDone) "${db.photos.size} photos in" else "Link your trail-cam folder", photosDone, onPhotos)
        SetupRow(2, Accent.amber, "Sort them", if (sortDone) "All sorted" else "$toSort visit${if (toSort == 1) "" else "s"} left, yes/no taps", sortDone, onSort)
        SetupRow(
            3, Accent.fern, "Place your cameras",
            when { db.cameras.isEmpty() -> "Cameras appear as you sort"; camsDone -> "All pinned"; else -> "$unpinned to pin on the map and aim" },
            camsDone, onCameras,
        )
    }
    Spacer(Modifier.height(16.dp))
}

private enum class Tab(val label: String) {
    BUCKS("Bucks"), GALLERY("Gallery"), SORT("Sort");
    // Selected tab = the app's Fern chrome everywhere (same as the drawer), not a color per tab.
    val accent: Color get() = Accent.fern
}

/** Drawer -> Bucks. Your bucks up top, photos in, sorting, and camera setup. */
@Composable
fun BucksScreen(
    onOpenBuck: (String) -> Unit,
    onPinCamera: (String) -> Unit = {},
    onOpenPhoto: (String) -> Unit = {},
    onReview: (String) -> Unit = {},
    onSettings: () -> Unit = {},
    onOpenGallery: (String) -> Unit = {},
    onOpenMap: () -> Unit = {},
    /** The buck map at a moment (past or forecast), optionally filtered to one buck. */
    onOpenMapAt: (Long, String?) -> Unit = { _, _ -> },
    startTab: String? = null,
    startFilter: String? = null,
    mapTime: Long? = null,
    mapBuck: String? = null,
) {
    val context = LocalContext.current
    remember { BuckPrefs.load(context) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { BuckStore.ensureLoaded(context) } }
    val db = BuckStore.db
    val scope = rememberCoroutineScope()
    // Saved, so coming back from a photo or a buck lands on the same tab.
    // ("CAMERAS" was a tab; cameras now live on the home page.)
    var tab by rememberSaveable { mutableStateOf(startTab?.let { runCatching { Tab.valueOf(it) }.getOrNull() } ?: Tab.BUCKS) }
    var rereading by remember { mutableStateOf<PhotoImporter.Progress?>(null) }
    val progress = com.kairos.bucks.PhotoSync.progress
    val lastLog = com.kairos.bucks.PhotoSync.lastLog
    var unlinking by remember { mutableStateOf<String?>(null) }
    var showIntro by remember { mutableStateOf(!BucksIntroPrefs.seen(context)) }
    // A camera's pop-up from the home page; saved so back from one of its photos returns to it.
    var camOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var dateFixOpen by rememberSaveable { mutableStateOf(false) }
    if (dateFixOpen) DateFixSheet { dateFixOpen = false }
    var dupesOpen by rememberSaveable { mutableStateOf(false) }
    if (dupesOpen) DupeSheet { dupesOpen = false }
    // Duplicate files in his folders: checked when this opens and after every folder check.
    LaunchedEffect(db.folderUris) { com.kairos.bucks.Dupes.scan(context) }

    // One time: read every strip again with the voting reader (fixes temps / times read by the
    // older single-pass reader). Never touches EXIF or hand-set values.
    LaunchedEffect(db.photos.isNotEmpty()) {
        if (BuckPrefs.rereadDone || BuckStore.db.photos.isEmpty()) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { PhotoImporter.rereadAll(context) { rereading = it } }
        rereading = null
        BuckPrefs.markRereadDone()
        com.kairos.bucks.PhotoSync.lastLog = "Re-read ${r.checked} photo strips: ${r.timesFixed} time${if (r.timesFixed == 1) "" else "s"} and ${r.tempsFixed} temp${if (r.tempsFixed == 1) "" else "s"} corrected" +
            if (r.flagged > 0) ", ${r.flagged} to check in Sort." else "."
    }

    fun runImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            com.kairos.bucks.PhotoSync.import(context, uris)
            if (BuckStore.db.unsorted.isNotEmpty()) tab = Tab.SORT
        }
    }
    // Read + write kept on what he picks, so the Gallery can delete the phone file too.
    val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u -> runCatching { context.contentResolver.takePersistableUriPermission(u, rw) } }
        runImport(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, rw) }
                .onFailure { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            BuckStore.update { it.copy(folderUris = (it.folderUris + uri.toString()).distinct()) }
            scope.launch {
                com.kairos.bucks.PhotoSync.checkFolders(context)
                if (BuckStore.db.unsorted.isNotEmpty()) tab = Tab.SORT
            }
        }
    }
    unlinking?.let { u ->
        Takeover({ unlinking = null }, title = "Stop checking ${folderName(u)}?") {
            Text("Photos already in Kairos stay. New photos saved there won't come in by themselves.", color = KairosColors.Dim, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryPill("Keep", { unlinking = null }, Modifier.weight(1f))
                PrimaryPill("Stop checking", { BuckStore.update { it.copy(folderUris = it.folderUris - u) }; unlinking = null }, Modifier.weight(1f), Accent.brick)
            }
        }
    }

    if (showIntro) {
        val close = { BucksIntroPrefs.markSeen(context); showIntro = false }
        BucksIntro(
            onFolder = { close(); pickFolder.launch(null) },
            onPhotos = { close(); pickPhotos.launch(arrayOf("image/*")) },
            onClose = close,
        )
        return
    }

    if (!BuckPrefs.unitsAsked) UnitsFirstRun {}
    camOpen?.let { id -> CameraSheet(id, onClose = { camOpen = null }, onOpenPhoto = onOpenPhoto, onGallery = onOpenGallery, onPin = { onPinCamera(it) }) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Segmented(tab, db.unsorted.size) { tab = it } }
            // The buck map: every visit on a timeline with the wind (past and forecast).
            androidx.compose.material3.IconButton(onClick = onOpenMap) {
                Icon(Icons.Outlined.Map, "Buck map", tint = KairosColors.Dim)
            }
            androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.padding(end = 4.dp)) {
                Icon(Icons.Outlined.Settings, "Bucks settings", tint = KairosColors.Dim)
            }
        }
        rereading?.let { pr ->
            Text("Re-reading photo strips ${pr.done} of ${pr.total}", style = MaterialTheme.typography.labelLarge, color = KairosColors.Dim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        progress?.let { pr ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Reading photos ${pr.done} of ${pr.total}", style = MaterialTheme.typography.labelLarge, color = KairosColors.Dim)
                Spacer(Modifier.height(6.dp))
                val f = if (pr.total == 0) 0f else pr.done.toFloat() / pr.total
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(KairosColors.Surface2)) {
                    Box(Modifier.fillMaxWidth(f).height(4.dp).clip(CircleShape).background(Accent.teal))
                }
            }
        }
        when (tab) {
            Tab.SORT -> SortPane(onDone = { tab = Tab.BUCKS })
            Tab.GALLERY -> GalleryPane(onOpenPhoto, startFilter)
            Tab.BUCKS -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (db.photos.isEmpty() && progress == null) {
                    EmptyState(onPhotos = { pickPhotos.launch(arrayOf("image/*")) }, onFolder = { pickFolder.launch(null) })
                    TextButton(onClick = { showIntro = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("How it works", color = Accent.teal) }
                    return@Column
                }
                Spacer(Modifier.height(4.dp))
                val toSort = visitGroups(db).size
                val main = db.mainBuck
                // 1. Your #1 buck, front and center (or the ground's latest photo until there is one).
                if (main != null) MainBuckHero(main) { onOpenBuck(main.id) } else HomeHero(db, toSort)
                Spacer(Modifier.height(14.dp))
                // 2. What needs you.
                SetupCard(
                    db = db,
                    onPhotos = { pickFolder.launch(null) },
                    onSort = { tab = Tab.SORT },
                    onCameras = { camOpen = db.cameras.firstOrNull { it.lat == null || it.facingDeg == null }?.id },
                )
                // Strip dates that don't fit their temperature: one tap each to fix.
                BuckAnalysis.state?.dateFixes?.takeIf { it.isNotEmpty() }?.let { fixes ->
                    ListRow(
                        lead = { Text("${fixes.size}", color = Accent.amber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) },
                        title = if (fixes.size == 1) "photo date looks misread" else "photo dates look misread", sub = "The strip's temperature doesn't fit that day. Check it in one tap.",
                    ) { dateFixOpen = true }
                    Spacer(Modifier.height(10.dp))
                }
                // The same file twice in his folders: one tap to compare them.
                com.kairos.bucks.Dupes.pairs.size.takeIf { it > 0 }?.let { n ->
                    ListRow(
                        lead = { Text("$n", color = Accent.amber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) },
                        title = if (n == 1) "duplicate photo in your folders" else "duplicate photos in your folders", sub = "The same file twice. Compare them and keep or delete the copy.",
                    ) { dupesOpen = true }
                    Spacer(Modifier.height(10.dp))
                }
                if (toSort > 0 && db.photos.size != db.unsorted.size) {
                    ListRow(
                        lead = { Text("$toSort", color = Accent.amber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) },
                        title = "visit${if (toSort == 1) "" else "s"} to sort", sub = "New photos. A few yes/no taps each.",
                    ) { tab = Tab.SORT }
                    Spacer(Modifier.height(10.dp))
                }
                // 3. Everything else on camera, compact.
                val res = BuckAnalysis.state
                val others = db.bucks.filter { it.id != main?.id }.sortedWith(compareBy<Buck> { it.pool }.thenByDescending { res?.reports?.get(it.id)?.lastSeen ?: 0 })
                val unsure = db.photos.filter { it.label == PhotoLabel.DEER_UNKNOWN }
                if (others.isNotEmpty() || unsure.isNotEmpty()) {
                    Text("Also on camera", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KairosColors.Surface).padding(vertical = 4.dp)) {
                        others.forEach { b ->
                            val r = res?.reports?.get(b.id)
                            val thumb = (b.refPhotoIds + db.photosOf(b.id).map { it.id }).firstOrNull()
                            ListRow(
                                lead = {
                                    if (b.face != null) Box(Modifier.size(64.dp, 40.dp), contentAlignment = Alignment.Center) { BuckFace(b, 40.dp) }
                                    else Box(Modifier.size(64.dp, 36.dp)) { thumb?.let { PhotoThumb(it, Modifier.fillMaxSize()) } }
                                },
                                title = b.name, dot = buckColor(b.colorIdx),
                                sub = listOfNotNull(r?.let { "${it.visits.size} visits" }, r?.lastSeen?.let { seenAgo(it) }).joinToString("  ·  ").ifEmpty { "${db.photosOf(b.id).size} photos" },
                                surface = false,
                            ) { onOpenBuck(b.id) }
                        }
                        if (unsure.isNotEmpty()) {
                            ListRow(
                                lead = { Box(Modifier.size(64.dp, 36.dp)) { PhotoThumb(unsure.first().id, Modifier.fillMaxSize()) } },
                                title = "Not sure", sub = "${unsure.size} photo${if (unsure.size == 1) "" else "s"} to look at again later", surface = false,
                            ) { onReview(UNSURE_ID) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                // 4. Cameras: one tap to each camera's pop-up.
                if (db.cameras.isNotEmpty()) {
                    Text("Your cameras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KairosColors.Surface).padding(vertical = 4.dp)) {
                        db.cameras.forEach { c ->
                            val photos = db.photos.filter { it.cameraId == c.id }
                            val missing = listOfNotNull(if (c.lat == null) "location" else null, if (c.facingDeg == null) "facing" else null)
                            ListRow(
                                lead = { Box(Modifier.size(64.dp, 36.dp)) { (photos.lastOrNull { !it.night } ?: photos.lastOrNull())?.let { PhotoThumb(it.id, Modifier.fillMaxSize()) } } },
                                title = c.name,
                                sub = "${photos.size} photos  ·  " + if (missing.isEmpty()) "faces ${com.kairos.bucks.U.compass(c.facingDeg!!)}" else "needs ${missing.joinToString(" and ")}",
                                surface = false,
                            ) { camOpen = c.id }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                // 5. Photos in, quietly.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    ActionPill(Icons.Outlined.AddPhotoAlternate, "Add photos") { pickPhotos.launch(arrayOf("image/*")) }
                    ActionPill(Icons.Outlined.Folder, if (db.folderUris.isEmpty()) "Link a folder" else "Add a folder") { pickFolder.launch(null) }
                }
                // Linked folders come in by themselves each time Kairos opens. Tap one to stop.
                if (db.folderUris.isNotEmpty()) {
                    Text("New photos in these folders come in each time you open Kairos:", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        db.folderUris.forEach { u -> ActionChip(folderName(u), Icons.Outlined.Folder) { unlinking = u } }
                    }
                }
                lastLog?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, modifier = Modifier.padding(vertical = 6.dp)) }
                res?.notes?.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Segmented(tab: Tab, unsorted: Int, onPick: (Tab) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(CircleShape).background(KairosColors.Surface2).padding(4.dp),
    ) {
        // Sort only shows when there's something to sort (or you're on it).
        Tab.entries.filter { it != Tab.SORT || unsorted > 0 || it == tab }.forEach { t ->
            val on = t == tab
            Box(
                Modifier.weight(1f).height(38.dp).clip(CircleShape).background(if (on) Accent.brush(t.accent) else androidx.compose.ui.graphics.SolidColor(Color.Transparent)).clickable { onPick(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.label + if (t == Tab.SORT && unsorted > 0) "  $unsorted" else "",
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Accent.on(t.accent) else KairosColors.Dim,
                )
            }
        }
    }
}

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(KairosColors.Surface2).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Accent.teal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = KairosColors.Text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(onPhotos: () -> Unit, onFolder: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 8.dp, end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).background(KairosColors.Surface2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CameraAlt, null, tint = KairosColors.Text, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Pattern your bucks.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Drop in your trail-cam photos. Kairos reads the time off each one, merges duplicates, pulls the weather for that hour, and asks you a few yes/no questions to tell your bucks apart.",
            color = KairosColors.Dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryPill("Choose photos", onPhotos, Modifier.fillMaxWidth(), Accent.teal)
        Spacer(Modifier.height(12.dp))
        SecondaryPill("Link a folder", onFolder, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("A linked folder (like DCIM/Trail cams) can be re-checked for new photos with one tap.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, textAlign = TextAlign.Center)
    }
}

/**
 * Your #1 buck: his whole photo on top (the camera's info strip trimmed off), then the call, his
 * numbers and the next sit on a clean panel. Tap anywhere for the game plan.
 */
@Composable
private fun MainBuckHero(b: Buck, onOpen: () -> Unit) {
    val res = BuckAnalysis.state
    val r = res?.reports?.get(b.id)
    val photo = b.refPhotoIds.firstOrNull() ?: BuckStore.db.photosOf(b.id).firstOrNull()?.id
    val zone = java.time.ZoneId.systemDefault()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(KairosColors.Surface).clickable(onClick = onOpen)) {
        // 16:9 frame with the bottom strip cropped away (top-aligned).
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 8.4f)) {
            photo?.let { PhotoThumb(it, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, full = true) }
        }
        Column(Modifier.padding(20.dp)) {
            Text("YOUR #1 BUCK", color = Accent.amber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(b.name, color = KairosColors.Text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
            r?.let {
                Spacer(Modifier.height(4.dp))
                Text(it.plan.headline, color = KairosColors.Dim, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    PanelStat("${it.visits.size}", "visits", KairosColors.Text)
                    PanelStat("${it.visits.count { v -> v.f?.legal == true }}", "in daylight", Accent.amber)
                    it.lastSeen?.let { t -> PanelStat(seenAgo(t).removePrefix("seen "), "last seen", Accent.teal) }
                }
            }
            Spacer(Modifier.height(16.dp))
            NextSitFor(b.id)
        }
    }
}

@Composable
private fun PanelStat(value: String, label: String, color: Color) {
    Column {
        Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall)
    }
}

/** One row in a list card: a lead (thumb or number), title, subtitle, chevron. */
@Composable
private fun ListRow(lead: @Composable () -> Unit, title: String, sub: String, dot: Color? = null, surface: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).then(if (surface) Modifier.background(KairosColors.Surface) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dot?.let { Box(Modifier.size(8.dp).background(it, CircleShape)); Spacer(Modifier.width(6.dp)) }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = KairosColors.Text)
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = KairosColors.Faint, modifier = Modifier.size(18.dp))
    }
}

private fun seenAgo(t: Long): String {
    // Calendar days, not 24-hour blocks: 3:41 AM yesterday is "yesterday".
    val z = java.time.ZoneId.systemDefault()
    val d = (java.time.LocalDate.now(z).toEpochDay() - java.time.Instant.ofEpochSecond(t).atZone(z).toLocalDate().toEpochDay()).toInt()
    return when (d) { 0 -> "seen today"; 1 -> "seen yesterday"; else -> "seen ${d}d ago" }
}

@Composable
internal fun RutRow(rut: RutCalendar) {
    var edit by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KairosColors.Surface).clickable { edit = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Your rut", style = MaterialTheme.typography.labelLarge, color = KairosColors.Dim)
            Text("${md(rut.rutStart)} – ${md(rut.rutEnd)}   ·   pre-rut from ${md(rut.preRutStart)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Text("Edit", color = KairosColors.Water, style = MaterialTheme.typography.labelLarge)
    }
    if (edit) {
        var s by remember { mutableStateOf("${rut.rutStart.monthValue}/${rut.rutStart.dayOfMonth}") }
        var e by remember { mutableStateOf("${rut.rutEnd.monthValue}/${rut.rutEnd.dayOfMonth}") }
        var p by remember { mutableStateOf("${rut.preRutStart.monthValue}/${rut.preRutStart.dayOfMonth}") }
        fun parse(x: String) = runCatching { val (m, d) = x.trim().split("/").map { it.toInt() }; MonthDay.of(m, d) }.getOrNull()
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text("Rut dates on your ground") },
            text = {
                Column {
                    OutlinedTextField(p, { p = it }, label = { Text("Pre-rut starts (M/D)") })
                    OutlinedTextField(s, { s = it }, label = { Text("Rut starts (M/D)") })
                    OutlinedTextField(e, { e = it }, label = { Text("Rut ends (M/D)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = parse(s); val b = parse(e); val c = parse(p)
                    if (a != null && b != null && c != null) BuckStore.update { it.copy(rut = RutCalendar(a, b, c)) }
                    edit = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Cancel") } },
        )
    }
}

private fun md(m: MonthDay) = "${java.time.Month.of(m.monthValue).name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${m.dayOfMonth}"

// ---------------------------------------------------------------- cameras

@Composable
private fun CompassPicker(value: Double?, onPick: (Double) -> Unit) {
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(listOf(7, 0, 1), listOf(6, -1, 2), listOf(5, 4, 3)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { i ->
                    if (i < 0) Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CameraAlt, null, tint = KairosColors.Faint) }
                    else {
                        val on = value != null && compass8(value) == names[i]
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(if (on) Accent.fern else KairosColors.Surface2).clickable { onPick(i * 45.0) },
                            contentAlignment = Alignment.Center,
                        ) { Text(names[i], color = if (on) Accent.on(Accent.fern) else KairosColors.Text, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/** "Deer Insight" from a folder link (content://.../tree/primary%3ADCIM%2FDeer%20Insight). */
internal fun folderName(uri: String): String =
    Uri.decode(uri.substringAfterLast("/tree/")).substringAfter(':').trimEnd('/').substringAfterLast('/').ifBlank { "Phone storage" }
