package com.kairos.bucks

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kairos.engine.bucks.AgeAnswer
import com.kairos.engine.bucks.AgeQ
import com.kairos.engine.bucks.Heading
import com.kairos.engine.bucks.RutCalendar
import java.time.MonthDay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Everything the Bucks feature keeps: cameras, bucks, and every imported trail-cam photo. One
 * JSON file plus app-private image copies (a 2400 px working copy and a thumbnail per photo), so
 * it all works offline and survives the originals being deleted off the phone.
 *
 * State is a Compose snapshot ([db]); every change goes through [update], which saves to disk.
 */

enum class TimeSource(val label: String) { EXIF("Photo data"), STRIP("Read from strip"), MANUAL("Set by hand"), NONE("Unknown") }

enum class PhotoLabel(val label: String) { UNSORTED("Unsorted"), BUCK("Buck"), DOE("Doe / fawn"), DEER_UNKNOWN("Buck, can't tell"), NO_DEER("No deer / other") }

data class Camera(
    val id: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val facingDeg: Double? = null,
    val model: String? = null,
    val notes: String = "",
    /** His wind for the stand here: 16-point sectors (0 = N, clockwise), see StandWind. */
    val windSectors: Set<Int> = emptySet(),
)

/** His profile photo: a circle he drew on one photo. [x], [y] = circle center, [r] = radius, all 0..1 of the photo's width / height / height. */
data class Face(val photoId: String, val x: Float, val y: Float, val r: Float)

data class Buck(
    val id: String,
    val name: String,
    val colorIdx: Int,
    val notes: String = "",
    /** Photos picked as his best look, shown first when comparing. */
    val refPhotoIds: List<String> = emptyList(),
    /** true = the shared pool of spikes / small young bucks, patterned as one group. */
    val pool: Boolean = false,
    /** His #1 buck: front and center on the Bucks home. */
    val star: Boolean = false,
    /** Field-judging age answers (QDMA guide). */
    val age: Map<AgeQ, AgeAnswer> = emptyMap(),
    val face: Face? = null,
)

data class Photo(
    val id: String,
    val srcName: String,
    val sha1: String,
    val capture: LocalDateTime?,   // camera's wall clock
    val zone: String,              // zone that wall clock is in
    val timeSource: TimeSource,
    val timeCheck: Boolean,        // EXIF and strip disagree, or no time found
    val stripText: String?,
    val stripTempF: Int?,
    val model: String?,
    val width: Int,
    val height: Int,
    val hasSeconds: Boolean,
    val dHash: Long,
    val scene: String,             // view signature (base64) for matching a camera's view
    val grid: String = "",         // 96x54 frame (base64) for exact-duplicate checks
    val srcUri: String? = null,    // the file on the phone this came from (for delete-from-phone)
    val copyUris: List<String> = emptyList(), // other files on the phone that are this same frame
    val night: Boolean,            // IR (no color) shot
    val cameraId: String? = null,
    val cameraAuto: Boolean = false,
    val label: PhotoLabel = PhotoLabel.UNSORTED,
    val buckIds: List<String> = emptyList(),
    val heading: Heading? = null,
    val copies: List<String> = emptyList(), // other files that were the same capture
    val importedAt: Long = System.currentTimeMillis(),
    /** He typed the temperature himself: a strip re-read never overwrites it. */
    val tempManual: Boolean = false,
) {
    val epochSec: Long? get() = capture?.atZone(ZoneId.of(zone))?.toEpochSecond()
}

data class ImportLog(val at: Long, val added: Int, val duplicates: Int, val exact: Int, val failed: Int, val notes: List<String>, val skipped: Int = 0)

data class BuckDb(
    val cameras: List<Camera> = emptyList(),
    val bucks: List<Buck> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val log: List<ImportLog> = emptyList(),
    /** Phone folders checked for new photos every time the app opens. */
    val folderUris: List<String> = emptyList(),
    val rut: RutCalendar = RutCalendar(),
) {
    fun camera(id: String?) = cameras.firstOrNull { it.id == id }
    fun buck(id: String?) = bucks.firstOrNull { it.id == id }
    fun photo(id: String?) = photos.firstOrNull { it.id == id }
    /** The #1 buck: the one he starred, else the named buck with the most photos. */
    val mainBuck: Buck? get() = bucks.firstOrNull { it.star && !it.pool }
        ?: bucks.filter { !it.pool }.maxByOrNull { b -> photos.count { b.id in it.buckIds } }
    fun photosOf(buckId: String) = photos.filter { buckId in it.buckIds }.sortedByDescending { it.epochSec ?: 0 }
    val unsorted get() = photos.filter { it.label == PhotoLabel.UNSORTED || it.cameraId == null || it.timeCheck }
        .sortedBy { it.epochSec ?: Long.MAX_VALUE }
}

object BuckStore {
    var db by mutableStateOf(BuckDb())
        private set
    private var loaded = false
    private lateinit var dir: File

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            dir = File(context.filesDir, "bucks").apply { mkdirs() }
            File(dir, "img").mkdirs(); File(dir, "thumb").mkdirs()
            db = runCatching { File(dir, "db.json").takeIf { it.exists() }?.readText()?.let(::parse) }.getOrNull() ?: BuckDb()
            loaded = true
        }
    }

    fun imageFile(id: String) = File(dir, "img/$id.jpg")
    fun thumbFile(id: String) = File(dir, "thumb/$id.jpg")
    fun wxDir() = File(dir, "wx").apply { mkdirs() }

    @Synchronized
    fun update(change: (BuckDb) -> BuckDb) {
        db = change(db)
        save()
    }

    private fun save() {
        val tmp = File(dir, "db.json.tmp")
        tmp.writeText(toJson(db).toString())
        tmp.renameTo(File(dir, "db.json")).let { ok -> if (!ok) { File(dir, "db.json").delete(); tmp.renameTo(File(dir, "db.json")) } }
    }

    fun newId() = UUID.randomUUID().toString().take(12)

    // ---- edits used by the screens

    fun addCamera(name: String): Camera {
        val c = Camera(newId(), name)
        update { it.copy(cameras = it.cameras + c) }
        return c
    }

    fun saveCamera(c: Camera) = update { d -> d.copy(cameras = d.cameras.map { if (it.id == c.id) c else it }) }

    fun addBuck(): Buck {
        val n = db.bucks.count { !it.pool }
        val b = Buck(newId(), "Buck ${n + 1}", colorIdx = db.bucks.size % 8)
        update { it.copy(bucks = it.bucks + b) }
        return b
    }

    /** The young-buck pool (spikes, forks, small racks), created on first use. */
    fun pool(): Buck = db.bucks.firstOrNull { it.pool } ?: Buck(newId(), "Young bucks (spikes & small racks)", colorIdx = db.bucks.size % 8, pool = true)
        .also { b -> update { it.copy(bucks = it.bucks + b) } }

    /** Makes [id] the #1 buck (only one at a time). */
    fun starBuck(id: String) = update { d -> d.copy(bucks = d.bucks.map { it.copy(star = it.id == id) }) }

    fun saveBuck(b: Buck) = update { d -> d.copy(bucks = d.bucks.map { if (it.id == b.id) b else it }) }

    /** Deletes the buck; his photos go back to Unsorted. */
    fun deleteBuck(id: String) = update { d ->
        d.copy(
            bucks = d.bucks.filterNot { it.id == id },
            photos = d.photos.map { p ->
                if (id !in p.buckIds) p else {
                    val left = p.buckIds - id
                    p.copy(buckIds = left, label = if (left.isEmpty()) PhotoLabel.UNSORTED else p.label)
                }
            },
        )
    }

    /**
     * Who's in these photos. [buckIds] empty with [label] BUCK means "a buck, can't tell which"
     * (the Not sure pile). Removing a buck also drops the photo from his comparison set; adding one
     * seeds it (up to 4).
     */
    fun setWho(ids: Set<String>, label: PhotoLabel, buckIds: List<String>) = update { d ->
        val lbl = if (label == PhotoLabel.BUCK && buckIds.isEmpty()) PhotoLabel.DEER_UNKNOWN else label
        d.copy(
            photos = d.photos.map { if (it.id in ids) it.copy(label = lbl, buckIds = buckIds) else it },
            bucks = d.bucks.map { b ->
                if (b.id in buckIds) b.copy(refPhotoIds = (b.refPhotoIds + ids.take((4 - b.refPhotoIds.size).coerceAtLeast(0))).distinct())
                else b.copy(refPhotoIds = b.refPhotoIds - ids)
            },
        )
    }

    /**
     * Puts [buckId] in these photos, or takes him out when every one already has him. A photo
     * left with no buck goes to the Not sure pile (a buck, can't tell which). Returns true if added.
     */
    fun toggleBuck(ids: Set<String>, buckId: String): Boolean {
        val ps = db.photos.filter { it.id in ids }
        val remove = ps.isNotEmpty() && ps.all { buckId in it.buckIds }
        update { d ->
            d.copy(
                photos = d.photos.map { p ->
                    if (p.id !in ids) p else {
                        val ids2 = if (remove) p.buckIds - buckId else (p.buckIds + buckId).distinct()
                        p.copy(buckIds = ids2, label = if (ids2.isEmpty()) PhotoLabel.DEER_UNKNOWN else PhotoLabel.BUCK)
                    }
                },
                bucks = d.bucks.map { b ->
                    if (b.id != buckId) b
                    else if (remove) b.copy(refPhotoIds = b.refPhotoIds - ids)
                    else b.copy(refPhotoIds = (b.refPhotoIds + ids.take((4 - b.refPhotoIds.size).coerceAtLeast(0))).distinct())
                },
            )
        }
        return !remove
    }

    fun editPhotos(ids: Set<String>, change: (Photo) -> Photo) = update { d ->
        d.copy(photos = d.photos.map { if (it.id in ids) change(it) else it })
    }

    /** Start over: every camera, buck, photo copy and cached weather in Bucks. Originals on the phone are untouched. */
    fun reset() {
        update { BuckDb() }
        listOf("img", "thumb", "wx").forEach { File(dir, it).deleteRecursively(); File(dir, it).mkdirs() }
    }

    /**
     * Deletes photos from Kairos and, when [fromPhone], their files on the phone too (the original
     * file and every same-frame copy that was merged into it). Returns how many phone files
     * couldn't be deleted (no permission, already gone).
     */
    fun deletePhotos(context: android.content.Context, ids: Set<String>, fromPhone: Boolean): Int {
        var failed = 0
        if (fromPhone) {
            db.photos.filter { it.id in ids }.flatMap { listOfNotNull(it.srcUri) + it.copyUris }.forEach { u ->
                val ok = runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, android.net.Uri.parse(u)) }.getOrDefault(false)
                if (!ok) failed++
            }
        }
        update { d -> d.copy(photos = d.photos.filterNot { it.id in ids }, bucks = d.bucks.map { it.copy(refPhotoIds = it.refPhotoIds - ids, face = it.face?.takeIf { f -> f.photoId !in ids }) }) }
        ids.forEach { imageFile(it).delete(); thumbFile(it).delete() }
        return failed
    }

    fun deletePhoto(id: String) {
        update { d -> d.copy(photos = d.photos.filterNot { it.id == id }, bucks = d.bucks.map { it.copy(refPhotoIds = it.refPhotoIds - id, face = it.face?.takeIf { f -> f.photoId != id }) }) }
        imageFile(id).delete(); thumbFile(id).delete()
    }

    // ---- JSON

    private fun toJson(d: BuckDb) = JSONObject().apply {
        put("v", 1)
        put("folders", JSONArray(d.folderUris))
        put("rut", JSONObject().put("start", d.rut.rutStart.toString()).put("end", d.rut.rutEnd.toString()).put("pre", d.rut.preRutStart.toString()))
        put("cameras", JSONArray(d.cameras.map { c ->
            JSONObject().put("id", c.id).put("name", c.name).putOpt("lat", c.lat).putOpt("lon", c.lon)
                .putOpt("facing", c.facingDeg).putOpt("model", c.model).put("notes", c.notes).put("wind", JSONArray(c.windSectors.sorted()))
        }))
        put("bucks", JSONArray(d.bucks.map { b ->
            JSONObject().put("id", b.id).put("name", b.name).put("color", b.colorIdx).put("notes", b.notes)
                .put("refs", JSONArray(b.refPhotoIds)).put("pool", b.pool).put("star", b.star)
                .put("age", JSONObject().apply { b.age.forEach { (q, a) -> put(q.name, a.name) } })
                .putOpt("face", b.face?.let { f -> JSONObject().put("p", f.photoId).put("x", f.x.toDouble()).put("y", f.y.toDouble()).put("r", f.r.toDouble()) })
        }))
        put("photos", JSONArray(d.photos.map { p ->
            JSONObject().put("id", p.id).put("src", p.srcName).put("sha1", p.sha1)
                .putOpt("capture", p.capture?.toString()).put("zone", p.zone).put("timeSource", p.timeSource.name)
                .put("timeCheck", p.timeCheck).putOpt("strip", p.stripText).putOpt("stripTemp", p.stripTempF)
                .putOpt("model", p.model).put("w", p.width).put("h", p.height).put("secs", p.hasSeconds)
                .put("dhash", p.dHash).put("scene", p.scene).put("grid", p.grid).putOpt("uri", p.srcUri).put("copyUris", JSONArray(p.copyUris))
                .put("night", p.night).putOpt("cam", p.cameraId).put("camAuto", p.cameraAuto).put("label", p.label.name)
                .put("bucks", JSONArray(p.buckIds)).putOpt("heading", p.heading?.name).put("copies", JSONArray(p.copies))
                .put("importedAt", p.importedAt).put("tempManual", p.tempManual)
        }))
        put("log", JSONArray(d.log.takeLast(30).map { l ->
            JSONObject().put("at", l.at).put("added", l.added).put("dups", l.duplicates).put("exact", l.exact)
                .put("failed", l.failed).put("skipped", l.skipped).put("notes", JSONArray(l.notes.take(200)))
        }))
    }

    private fun parse(text: String): BuckDb {
        val o = JSONObject(text)
        fun JSONArray.objs() = (0 until length()).map { getJSONObject(it) }
        fun JSONArray.strs() = (0 until length()).map { getString(it) }
        fun JSONObject.dbl(k: String) = if (has(k) && !isNull(k)) getDouble(k) else null
        fun JSONObject.str(k: String) = if (has(k) && !isNull(k)) getString(k) else null
        return BuckDb(
            // "folderUri" was a single folder (before 2026-10-14).
            folderUris = (o.optJSONArray("folders")?.strs().orEmpty() + listOfNotNull(o.str("folderUri"))).distinct(),
            rut = o.optJSONObject("rut")?.let { r ->
                runCatching { RutCalendar(MonthDay.parse(r.getString("start")), MonthDay.parse(r.getString("end")), MonthDay.parse(r.getString("pre"))) }.getOrNull()
            } ?: RutCalendar(),
            cameras = o.optJSONArray("cameras")?.objs().orEmpty().map {
                Camera(
                    it.getString("id"), it.getString("name"), it.dbl("lat"), it.dbl("lon"), it.dbl("facing"), it.str("model"), it.optString("notes"),
                    windSectors = it.optJSONArray("wind")?.let { a -> (0 until a.length()).map { i -> a.getInt(i) }.filter { s -> s in 0..15 }.toSet() }.orEmpty(),
                )
            },
            bucks = o.optJSONArray("bucks")?.objs().orEmpty().map {
                val age = it.optJSONObject("age")
                Buck(
                    it.getString("id"), it.getString("name"), it.optInt("color"), it.optString("notes"),
                    it.optJSONArray("refs")?.strs().orEmpty(), it.optBoolean("pool"),
                    star = it.optBoolean("star"),
                    face = it.optJSONObject("face")?.let { f -> Face(f.getString("p"), f.getDouble("x").toFloat(), f.getDouble("y").toFloat(), f.getDouble("r").toFloat()) },
                    age = age?.keys()?.asSequence()?.mapNotNull { k -> runCatching { AgeQ.valueOf(k) to AgeAnswer.valueOf(age.getString(k)) }.getOrNull() }?.toMap().orEmpty(),
                )
            },
            photos = o.optJSONArray("photos")?.objs().orEmpty().map {
                Photo(
                    id = it.getString("id"), srcName = it.getString("src"), sha1 = it.getString("sha1"),
                    capture = it.str("capture")?.let(LocalDateTime::parse), zone = it.getString("zone"),
                    timeSource = TimeSource.valueOf(it.getString("timeSource")), timeCheck = it.optBoolean("timeCheck"),
                    stripText = it.str("strip"), stripTempF = if (it.has("stripTemp")) it.getInt("stripTemp") else null,
                    model = it.str("model"), width = it.optInt("w"), height = it.optInt("h"), hasSeconds = it.optBoolean("secs"),
                    dHash = it.optLong("dhash"),
                    scene = it.optString("scene"), grid = it.optString("grid"), srcUri = it.str("uri"), copyUris = it.optJSONArray("copyUris")?.strs().orEmpty(),
                    night = it.optBoolean("night"), cameraId = it.str("cam"), cameraAuto = it.optBoolean("camAuto"),
                    label = PhotoLabel.valueOf(it.getString("label")), buckIds = it.optJSONArray("bucks")?.strs().orEmpty(),
                    heading = it.str("heading")?.let(Heading::valueOf), copies = it.optJSONArray("copies")?.strs().orEmpty(),
                    importedAt = it.optLong("importedAt"), tempManual = it.optBoolean("tempManual"),
                )
            },
            log = o.optJSONArray("log")?.objs().orEmpty().map {
                ImportLog(it.getLong("at"), it.getInt("added"), it.getInt("dups"), it.optInt("exact"), it.getInt("failed"), it.optJSONArray("notes")?.strs().orEmpty(), it.optInt("skipped"))
            },
        )
    }
}
