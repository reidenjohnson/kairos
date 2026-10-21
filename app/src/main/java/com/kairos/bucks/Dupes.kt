package com.kairos.bucks

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Duplicate files in his linked folders: files whose bytes are identical (SHA-1), so everything
 * in them matches, strip included. Certain, not a guess. Never by file name: SPYPOINT names a
 * file by when it was saved, so his one real duplicate had two different names. (The same photo
 * saved at another size isn't caught; none of his folders had one, 2026-10-14.)
 *
 * The oldest file is the original and stays; each newer copy is shown next to it, and goes only
 * when he holds Delete. "Keep both" is remembered.
 */
object Dupes {
    data class FileInfo(val uri: String, val name: String, val folder: String, val modified: Long, val sha: String)
    data class Pair(val keep: FileInfo, val drop: FileInfo)

    var pairs by mutableStateOf<List<Pair>>(emptyList())
        private set

    private val lock = Mutex()
    private fun sp(c: Context) = c.getSharedPreferences("bucks_dupes", Context.MODE_PRIVATE)
    private fun key(p: Pair) = listOf(p.keep.uri, p.drop.uri).sorted().joinToString("|")

    suspend fun scan(context: Context) = lock.withLock {
        pairs = withContext(Dispatchers.IO) { runCatching { find(context) }.getOrDefault(emptyList()) }
    }

    private fun find(context: Context): List<Pair> {
        val cache = loadCache(context)
        val files = ArrayList<FileInfo>()
        val sizes = HashMap<String, Long>()
        for (f in BuckStore.db.folderUris) {
            val tree = Uri.parse(f)
            val folder = Uri.decode(f.substringAfterLast("/tree/")).substringAfter(':').trimEnd('/').substringAfterLast('/')
            val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            runCatching {
                context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId), cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(1)?.startsWith("image/") != true) continue
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString()
                        val size = c.getLong(3); val mod = c.getLong(4)
                        // Read a file only when it's new or changed.
                        val sha = cache[uri]?.takeIf { it.first == "$size/$mod" }?.second ?: shaOf(context, uri) ?: continue
                        files += FileInfo(uri, c.getString(2), folder, mod, sha)
                        sizes[uri] = size
                    }
                }
            }
        }
        saveCache(context, files.associate { it.uri to ("${sizes[it.uri]}/${it.modified}" to it.sha) })
        val kept = sp(context).getStringSet("kept", emptySet()).orEmpty()
        return files.groupBy { it.sha }.values.filter { it.size > 1 }.flatMap { same ->
            val original = same.minBy { it.modified }
            same.filter { it !== original }.map { Pair(original, it) }
        }.filterNot { key(it) in kept }.sortedBy { it.drop.modified }
    }

    private fun shaOf(context: Context, uri: String): String? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))!!.use { s -> MessageDigest.getInstance("SHA-1").digest(s.readBytes()).joinToString("") { "%02x".format(it) } }
    }.getOrNull()

    /** "Keep both": never ask about these two again. */
    fun keepBoth(context: Context, p: Pair) {
        sp(context).edit().putStringSet("kept", sp(context).getStringSet("kept", emptySet()).orEmpty() + key(p)).apply()
        pairs = pairs - p
    }

    /**
     * Deletes the copy, after checking both files still have the same bytes as when compared.
     * Kairos keeps its own copy of every photo; one that pointed at the deleted file now points at
     * the original. Returns null when deleted, else why not, in his words.
     */
    suspend fun delete(context: Context, p: Pair): String? = withContext(Dispatchers.IO) {
        if (shaOf(context, p.keep.uri) != p.keep.sha) return@withContext "The original changed or is gone, so nothing was deleted."
        if (shaOf(context, p.drop.uri) != p.drop.sha) return@withContext "That copy changed since it was checked, so it wasn't deleted."
        val ok = runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(p.drop.uri)) }.getOrDefault(false)
        if (!ok) return@withContext "Android didn't let Kairos delete it. Link ${p.drop.folder} again with \"Add a folder\" so Kairos can change it."
        BuckStore.update { d ->
            d.copy(photos = d.photos.map { ph ->
                when {
                    ph.srcUri == p.drop.uri -> ph.copy(srcUri = p.keep.uri, copyUris = ph.copyUris - p.keep.uri)
                    p.drop.uri in ph.copyUris -> ph.copy(copyUris = ph.copyUris - p.drop.uri)
                    else -> ph
                }
            })
        }
        pairs = pairs.filterNot { it.drop.uri == p.drop.uri }
        null
    }

    // files/bucks/sha.json: uri -> ("size/modified", sha), so a check with nothing new reads no files.
    private fun cacheFile(c: Context) = File(c.filesDir, "bucks/sha.json")

    private fun loadCache(c: Context): Map<String, kotlin.Pair<String, String>> = runCatching {
        val o = JSONObject(cacheFile(c).readText())
        o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { it.getString("v") to it.getString("sha") } }
    }.getOrDefault(emptyMap())

    private fun saveCache(c: Context, m: Map<String, kotlin.Pair<String, String>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONObject().put("v", v.first).put("sha", v.second)) }
        runCatching { cacheFile(c).writeText(o.toString()) }
    }
}
