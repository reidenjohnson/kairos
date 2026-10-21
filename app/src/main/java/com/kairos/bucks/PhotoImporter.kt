package com.kairos.bucks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kairos.engine.bucks.PhotoMath
import com.kairos.engine.bucks.LightCheck
import com.kairos.engine.bucks.StripConsensus
import com.kairos.engine.bucks.StripParser
import com.kairos.engine.bucks.StripRead
import com.kairos.engine.bucks.StripVote
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * Brings trail-cam photos in. Per file:
 *  1. Exact copy already in? (same bytes) -> skip, note it.
 *  2. Capture time: the photo's hidden EXIF time first; the burned-in strip read on the phone
 *     (ML Kit, offline) as the cross-check and as the fallback for app saves/screenshots that
 *     carry no EXIF. Disagreement over 2 min, or no time at all -> flagged for a check.
 *  3. Same capture already in at another size (original + SPYPOINT app save + screenshot)?
 *     -> one photo; the sharpest copy is kept and the others are listed on it.
 *  4. Which camera: matched on the view's edge map against photos already confirmed per
 *     camera (day and IR night kept apart). Only an unambiguous match is filled in, marked
 *     "auto"; anything else is asked in Sort.
 * Must run off the main thread.
 */
object PhotoImporter {
    private const val WORK_PX = 2400
    private const val THUMB_PX = 420
    private const val STRIP_FRACTION = 0.10f

    data class Progress(val done: Int, val total: Int)

    fun listFolder(context: Context, tree: Uri): List<Uri> {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val out = ArrayList<Uri>()
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(1) ?: continue
                if (mime.startsWith("image/")) out += DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return out
    }

    fun import(context: Context, uris: List<Uri>, onProgress: (Progress) -> Unit): ImportLog {
        BuckStore.ensureLoaded(context)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var added = 0; var dups = 0; var exact = 0; var failed = 0; var skipped = 0
        val notes = ArrayList<String>()
        val zone = ZoneId.systemDefault().id
        try {
            uris.forEachIndexed { i, uri ->
                onProgress(Progress(i, uris.size))
                val name = displayName(context, uri) ?: uri.lastPathSegment ?: "photo"
                try {
                    when (val r = importOne(context, uri, name, zone, recognizer)) {
                        is One.Added -> { added++; r.note?.let { notes += it } }
                        is One.Duplicate -> { dups++; notes += r.note }
                        is One.Exact -> { exact++ }
                        is One.Skipped -> { skipped++; notes += r.note }
                    }
                } catch (e: Exception) {
                    failed++
                    notes += "$name: couldn't read (${e.javaClass.simpleName})"
                    android.util.Log.w("Bucks", "import $name", e)
                }
            }
            onProgress(Progress(uris.size, uris.size))
        } finally {
            recognizer.close()
        }
        val log = ImportLog(System.currentTimeMillis(), added, dups, exact, failed, notes, skipped)
        BuckStore.update { it.copy(log = it.log + log) }
        return log
    }

    private sealed interface One {
        data class Added(val note: String?) : One
        data class Duplicate(val note: String) : One
        data object Exact : One
        data class Skipped(val note: String) : One
    }

    private fun importOne(
        context: Context,
        uri: Uri,
        name: String,
        zone: String,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
    ): One {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val sha = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        // Already in. A photo that came in without a readable time is read again instead of skipped.
        val existing = BuckStore.db.photos.firstOrNull { it.sha1 == sha || sha in it.copies }
        if (existing != null && existing.timeSource != TimeSource.NONE) return One.Exact

        // ---- time from EXIF
        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
        val exifTime = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?.let { runCatching { LocalDateTime.parse(it.trim(), EXIF_FMT) }.getOrNull() }
        val model = listOfNotNull(exif?.getAttribute(ExifInterface.TAG_MAKE), exif?.getAttribute(ExifInterface.TAG_MODEL))
            .joinToString(" ").trim().ifEmpty { null }

        // ---- decode a working copy (EXIF rotation applied)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val fullW = bounds.outWidth; val fullH = bounds.outHeight
        var sample = 1
        while (max(fullW, fullH) / (sample * 2) >= WORK_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("not an image")
        val raw = scaleTo(decoded, WORK_PX)
        // A screenshot of the SPYPOINT viewer: cut the photo out of the app's black frame first.
        val screenshot = raw.height > raw.width
        val cut = if (screenshot) photoRegion(raw) else null
        val work = cut ?: raw
        val zoomed = screenshot && (cut == null || (work.width.toFloat() / work.height) !in 1.70f..1.85f)

        // ---- strip: several crops and a high-contrast pass, bottom first, then top
        val ocr = readStrip(work, recognizer)
        val strip = ocr.text
        val vote = ocr.vote
        val read = vote?.read
        val night0 = Bitmap.createBitmap(work, 0, 0, work.width, (work.height * (1 - STRIP_FRACTION)).toInt()).let { b -> isNight(b).also { if (b !== work) b.recycle() } }
        // The read must fit the photo: an IR shot at 1 PM or a color shot at 2 AM is a misread
        // (AM/PM, a slipped digit). Then the next-best read that fits wins, else he's asked.
        val (stripTime, lightMiss) = pickFitting(context, vote, night0, zone)

        val (capture, source, check) = when {
            exifTime != null && stripTime != null ->
                Triple(exifTime, TimeSource.EXIF, Duration.between(stripTime, exifTime).abs().toMinutes() > 2)
            exifTime != null -> Triple(exifTime, TimeSource.EXIF, false)
            stripTime != null -> Triple(stripTime, TimeSource.STRIP, lightMiss)
            else -> Triple(null, TimeSource.NONE, true)
        }

        if (screenshot && capture == null) {
            recycle(decoded, raw, work)
            return One.Skipped("$name: a screenshot with no trail-cam strip; skipped")
        }

        // ---- fingerprints, from the scene above the strip
        val sceneH = (work.height * (1 - STRIP_FRACTION)).toInt()
        val scene = Bitmap.createBitmap(work, 0, 0, work.width, sceneH)
        val dHash = PhotoMath.dHash(gray(scene, 9, 8))
        val edges = PhotoMath.sceneSignature(gray(scene, PhotoMath.SCENE_W, PhotoMath.SCENE_H))
        val night = isNight(scene)
        val grid = PhotoMath.pixelGrid(gray(scene, PhotoMath.GRID_W, PhotoMath.GRID_H))

        if (existing != null) {
            // Re-read of a photo that had no time: update it in place, keep its tags.
            BuckStore.editPhotos(setOf(existing.id)) {
                it.copy(capture = capture ?: it.capture, timeSource = source, timeCheck = check, stripText = (strip ?: ocr.raw)?.take(160), stripTempF = read?.tempF)
            }
            recycle(decoded, raw, work, scene)
            return if (capture != null) One.Added(null) else One.Added("$name: still no time read; set it in Sort")
        }

        // ---- the same frame already in (original, app download, screenshot)? Pixel-level check, so
        // two shots of one encounter both stay. A zoomed screenshot can't be compared pixel for
        // pixel; it joins the closest frame of the same minute and temperature.
        val candidates = BuckStore.db.photos
            .filter { p -> sameCapture(p, capture, exifTime != null, read?.tempF) && p.grid.isNotEmpty() }
            .map { it to Base64.decode(it.grid, Base64.NO_WRAP) }
        val twin = candidates.filter { PhotoMath.sameFrame(it.second, grid) }.minByOrNull { PhotoMath.frameDiff(it.second, grid) }?.first
            ?: if (zoomed) candidates.minByOrNull { PhotoMath.frameDiff(it.second, grid) }?.first else null
        if (twin != null) {
            val bigger = fullW * fullH > twin.width * twin.height
            if (bigger) {
                saveImages(twin.id, work)
                BuckStore.editPhotos(setOf(twin.id)) {
                    it.copy(
                        srcName = name, sha1 = sha, width = fullW, height = fullH, copies = it.copies + it.sha1,
                        srcUri = uri.toString(), copyUris = it.copyUris + listOfNotNull(it.srcUri),
                        capture = if (source == TimeSource.EXIF) capture else it.capture,
                        timeSource = if (source == TimeSource.EXIF) source else it.timeSource,
                        timeCheck = if (source == TimeSource.EXIF) check else it.timeCheck,
                        hasSeconds = it.hasSeconds || exifTime != null, model = model ?: it.model,
                    )
                }
            } else {
                BuckStore.editPhotos(setOf(twin.id)) { it.copy(copies = it.copies + sha, copyUris = it.copyUris + uri.toString()) }
            }
            recycle(decoded, raw, work, scene)
            val keep = if (bigger) name else twin.srcName
            return One.Duplicate("$name = same capture as ${twin.srcName}; kept $keep (${max(fullW, twin.width)} px)")
        }

        val id = BuckStore.newId()
        saveImages(id, work)
        val sceneB64 = Base64.encodeToString(edges, Base64.NO_WRAP)
        val (camId, auto) = suggestCamera(sceneB64, night, model)
        val photo = Photo(
            id = id, srcName = name, sha1 = sha, srcUri = uri.toString(), capture = capture, zone = zone, timeSource = source,
            timeCheck = check, stripText = (strip ?: ocr.raw)?.take(160), stripTempF = read?.tempF, model = model,
            width = fullW, height = fullH, hasSeconds = exifTime != null || read?.hasSeconds == true,
            dHash = dHash, scene = sceneB64, grid = Base64.encodeToString(grid, Base64.NO_WRAP), night = night, cameraId = if (auto) camId else null, cameraAuto = auto,
        )
        BuckStore.update { it.copy(photos = it.photos + photo) }
        recycle(decoded, raw, work, scene)
        val note = when {
            check && capture == null -> "$name: no time found; set it in Sort"
            check -> "$name: photo data and strip disagree on the time; check it in Sort"
            else -> null
        }
        return One.Added(note)
    }

    /**
     * Could be the same capture: same minute (to the second when both carry EXIF seconds, so burst
     * frames stay separate) and the same strip temperature when both were read (two cameras firing
     * the same minute read different temps: HD 54°F vs PICT 56°F on 9/23 18:51). The caller then
     * takes the closest-looking candidate within dHash 18 (720-px night saves measured 14 from
     * their 8064-px original on his phone).
     */
    private fun sameCapture(p: Photo, t: LocalDateTime?, hasExif: Boolean, tempF: Int?): Boolean {
        val pt = p.capture ?: return false
        if (t == null) return false
        if (hasExif && p.timeSource == TimeSource.EXIF && p.hasSeconds) {
            if (pt != t) return false
        } else if (pt.withSecond(0) != t.withSecond(0)) return false
        if (tempF != null && p.stripTempF != null && kotlin.math.abs(tempF - p.stripTempF) > 1) return false
        return true
    }

    /** Asked as a yes/no ("Camera 2?") only at or above this; below it, "which camera / new?". */
    const val ASK_AT = 0.15

    /**
     * Best camera by view. Auto-filled (marked "auto") when the best match is >= 0.30 and beats the
     * runner-up by 0.15. Set on his phone 2026-10-07: replaying his 32 photos in time order, that
     * filled 25 automatically with 0 wrong; unrelated views never passed 0.12.
     */
    fun suggestCamera(sceneB64: String, night: Boolean, model: String?): Pair<String?, Boolean> {
        val ranked = rankCameras(sceneB64, night)
        val best = ranked.firstOrNull() ?: return null to false
        val second = ranked.getOrNull(1)?.second ?: 0.0
        return best.first to (best.second >= 0.30 && best.second - second >= 0.15)
    }

    /** Cameras by their best-matching confirmed photo (best first), with the score. */
    fun rankCameras(sceneB64: String, @Suppress("UNUSED_PARAMETER") night: Boolean): List<Pair<String, Double>> {
        val me = Base64.decode(sceneB64, Base64.NO_WRAP)
        return BuckStore.db.photos.filter { it.cameraId != null && !it.cameraAuto }
            .groupBy { it.cameraId!! }
            .map { (cam, ps) -> cam to ps.takeLast(40).maxOf { PhotoMath.sceneSimilarity(me, Base64.decode(it.scene, Base64.NO_WRAP)) } }
            .sortedByDescending { it.second }
    }

    /** What the strip passes agreed on. [text] = the winning pass's text, [raw] = the longest text seen (for diagnosis). */
    private class StripOcr(val text: String?, val raw: String?, val vote: StripConsensus?)

    /**
     * OCR the info strip several ways: the bottom 6/10/14% of the frame (then the top), each
     * enlarged to at least 140 px tall, plain and high-contrast. Every pass is parsed and they
     * vote (StripVote); it stops as soon as two passes agree on the time and the temperature.
     * ML Kit's lines are put back in left-to-right order first, so pieces read out of order
     * ("°F" before the time) still parse.
     */
    private fun readStrip(work: Bitmap, recognizer: com.google.mlkit.vision.text.TextRecognizer): StripOcr {
        var raw: String? = null
        val reads = ArrayList<StripRead>()
        val texts = ArrayList<String>()
        for (bottom in listOf(true, false)) {
            for (contrast in listOf(false, true)) {
                for (frac in listOf(0.06f, 0.10f, 0.14f)) {
                    val h = (work.height * frac).toInt().coerceAtLeast(20)
                    val cut = Bitmap.createBitmap(work, 0, if (bottom) work.height - h else 0, work.width, h)
                    val big = if (h < 140) Bitmap.createScaledBitmap(cut, cut.width * 140 / h, 140, true) else cut
                    val img = if (contrast) highContrast(big) else big
                    val res = runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(img, 0))) }.getOrNull()
                    listOf(cut, big, img).distinct().forEach { if (!it.isRecycled) it.recycle() }
                    val text = res?.text
                    if (text.isNullOrBlank()) continue
                    if (raw == null || text.length > raw.length) raw = text
                    val ordered = res.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text }
                    val r = StripParser.parse(ordered).takeIf { it.time != null } ?: StripParser.parse(text)
                    if (r.time == null) continue
                    reads += r; texts += ordered
                    if (StripVote.settled(reads)) break
                }
                if (StripVote.settled(reads)) break
            }
            if (reads.isNotEmpty()) break // the top strip is only for cameras that print it there
        }
        val vote = StripVote.pick(reads)
        val winner = vote?.let { v -> texts[reads.indexOfFirst { it.time == v.read.time }] }
        return StripOcr(winner, raw, vote)
    }

    /** Where the light check is judged: a pinned camera, else the last place the app knew. */
    private fun place(context: Context): Pair<Double, Double>? =
        BuckStore.db.cameras.firstOrNull { it.lat != null && it.lon != null }?.let { it.lat!! to it.lon!! }
            ?: com.kairos.notify.NotifyPrefsBridge.lastPlace(context)?.let { it.lat to it.lon }

    /** The voted time if it fits the photo's light, else the best alternate that does: (time, nothing fit). */
    private fun pickFitting(context: Context, vote: StripConsensus?, night: Boolean, zone: String): Pair<LocalDateTime?, Boolean> {
        val best = vote?.read?.time ?: return null to false
        val (lat, lon) = place(context) ?: return best to false
        val z = ZoneId.of(zone)
        fun fits(t: LocalDateTime) = LightCheck.fits(night, t.atZone(z).toEpochSecond(), lat, lon, z)
        if (fits(best)) return best to false
        vote.alternates.firstOrNull { fits(it) }?.let { return it to false }
        return best to true
    }

    data class RereadResult(val checked: Int, val timesFixed: Int, val tempsFixed: Int, val flagged: Int)

    /**
     * Reads every stored photo's strip again with the voting reader (from the app's own working
     * copy, so the phone files aren't needed). Only strip-read values change: EXIF and hand-set
     * times, and hand-typed temperatures, are never touched. A new time that doesn't fit the
     * photo's light is not applied; the photo is flagged for a time check instead.
     */
    fun rereadAll(context: Context, onProgress: (Progress) -> Unit = {}): RereadResult {
        BuckStore.ensureLoaded(context)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var times = 0; var temps = 0; var flagged = 0
        val photos = BuckStore.db.photos.filter { !(it.timeSource == TimeSource.MANUAL && it.tempManual) }
        try {
            photos.forEachIndexed { i, p ->
                onProgress(Progress(i, photos.size))
                val bmp = runCatching { BitmapFactory.decodeFile(BuckStore.imageFile(p.id).path) }.getOrNull() ?: return@forEachIndexed
                val ocr = readStrip(bmp, recognizer)
                bmp.recycle()
                val vote = ocr.vote ?: return@forEachIndexed
                val (t, miss) = pickFitting(context, vote, p.night, p.zone)
                val newTemp = vote.read.tempF
                BuckStore.editPhotos(setOf(p.id)) { cur ->
                    var c = cur.copy(stripText = (ocr.text ?: ocr.raw)?.take(160) ?: cur.stripText)
                    if (!cur.tempManual && newTemp != null && newTemp != cur.stripTempF) { c = c.copy(stripTempF = newTemp); temps++ }
                    if (cur.timeSource == TimeSource.STRIP && t != null && t != cur.capture) {
                        if (miss) { if (!cur.timeCheck) { c = c.copy(timeCheck = true); flagged++ } }
                        else { c = c.copy(capture = t); times++ }
                    }
                    c
                }
            }
            onProgress(Progress(photos.size, photos.size))
        } finally {
            recognizer.close()
        }
        return RereadResult(photos.size, times, temps, flagged)
    }

    /** Reads one photo's strip again (the editor's "Read it again"); returns what it found, applies nothing. */
    fun readOne(context: Context, id: String): StripRead? {
        BuckStore.ensureLoaded(context)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val bmp = BitmapFactory.decodeFile(BuckStore.imageFile(id).path) ?: return null
            readStrip(bmp, recognizer).vote?.read.also { bmp.recycle() }
        } finally {
            recognizer.close()
        }
    }

    /** Gray, then hard black/white at the mean: the strip is white text on black. */
    private fun highContrast(b: Bitmap): Bitmap {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val g = IntArray(px.size) { (Color.red(px[it]) * 299 + Color.green(px[it]) * 587 + Color.blue(px[it]) * 114) / 1000 }
        val mean = g.average()
        val out = IntArray(px.size) { if (g[it] > mean + 20) Color.WHITE else Color.BLACK }
        return Bitmap.createBitmap(out, b.width, b.height, Bitmap.Config.ARGB_8888)
    }

    /**
     * The photo inside a screenshot of the SPYPOINT viewer: the longest run of rows, then columns,
     * where most pixels aren't the viewer's black. Null if nothing photo-like is found.
     */
    private fun photoRegion(b: Bitmap): Bitmap? {
        val sw = 180; val sh = (b.height * sw.toFloat() / b.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(b, sw, sh, true)
        val px = IntArray(sw * sh); small.getPixels(px, 0, sw, 0, 0, sw, sh); small.recycle()
        fun lum(p: Int) = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        fun longestRun(flags: BooleanArray): IntRange? {
            var best: IntRange? = null; var start = -1
            for (i in 0..flags.size) {
                val on = i < flags.size && flags[i]
                if (on && start < 0) start = i
                if (!on && start >= 0) { if (best == null || i - start > best.count()) best = start until i; start = -1 }
            }
            return best
        }
        val rows = longestRun(BooleanArray(sh) { y -> (0 until sw).count { lum(px[y * sw + it]) > 12 } > sw * 0.3 }) ?: return null
        val cols = longestRun(BooleanArray(sw) { x -> rows.count { lum(px[it * sw + x]) > 12 } > rows.count() * 0.3 }) ?: return null
        if (rows.count() < sh * 0.12 || cols.count() < sw * 0.5) return null
        val k = b.width.toFloat() / sw
        val x0 = (cols.first * k).toInt(); val y0 = (rows.first * k).toInt()
        val w = ((cols.last + 1) * k).toInt().coerceAtMost(b.width) - x0
        val h = ((rows.last + 1) * k).toInt().coerceAtMost(b.height) - y0
        return Bitmap.createBitmap(b, x0, y0, w, h)
    }

    private fun saveImages(id: String, work: Bitmap) {
        FileOutputStream(BuckStore.imageFile(id)).use { work.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val thumb = scaleTo(work, THUMB_PX)
        FileOutputStream(BuckStore.thumbFile(id)).use { thumb.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (thumb !== work) thumb.recycle()
    }

    private fun scaleTo(b: Bitmap, longPx: Int): Bitmap {
        val long = max(b.width, b.height)
        if (long <= longPx) return b
        val s = longPx.toFloat() / long
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
    }

    private fun gray(b: Bitmap, w: Int, h: Int): IntArray {
        val small = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== b) small.recycle()
        return IntArray(px.size) { (Color.red(px[it]) * 299 + Color.green(px[it]) * 587 + Color.blue(px[it]) * 114) / 1000 }
    }

    /** IR night shots carry (almost) no color. */
    private fun isNight(b: Bitmap): Boolean {
        val small = Bitmap.createScaledBitmap(b, 32, 18, true)
        val px = IntArray(32 * 18)
        small.getPixels(px, 0, 32, 0, 0, 32, 18)
        small.recycle()
        val hsv = FloatArray(3)
        val sat = px.map { Color.colorToHSV(it, hsv); hsv[1] }.average()
        return sat < 0.08
    }

    private fun recycle(vararg bs: Bitmap) = bs.distinct().forEach { if (!it.isRecycled) it.recycle() }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private val EXIF_FMT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
}
