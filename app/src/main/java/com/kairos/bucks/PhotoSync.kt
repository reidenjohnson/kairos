package com.kairos.bucks

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Photos in, from anywhere in the app: picked by hand, or new files in his linked folders, which
 * are checked every time the app comes to the front (the SPYPOINT app saves to
 * Pictures/Spypoint). Files already in, or already looked at, are skipped without being read, so
 * a check with nothing new costs one folder listing. One import at a time.
 */
object PhotoSync {
    var progress by mutableStateOf<PhotoImporter.Progress?>(null)
        private set
    /** What the last import did, in his words; null = nothing to say. */
    var lastLog by mutableStateOf<String?>(null)

    private val lock = Mutex()
    private fun sp(c: Context) = c.getSharedPreferences("bucks_sync", Context.MODE_PRIVATE)

    suspend fun import(context: Context, uris: List<Uri>): ImportLog? {
        if (uris.isEmpty()) return null
        return lock.withLock {
            val log = withContext(Dispatchers.IO) { PhotoImporter.import(context, uris) { progress = it } }
            progress = null
            lastLog = words(log)
            log
        }
    }

    /** New files in every linked folder; returns how many were added. */
    suspend fun checkFolders(context: Context): Int {
        val folders = BuckStore.db.folderUris
        if (folders.isEmpty() || lock.isLocked) return 0
        val fresh = withContext(Dispatchers.IO) {
            val known = BuckStore.db.photos.flatMap { listOfNotNull(it.srcUri) + it.copyUris }.toHashSet() +
                sp(context).getStringSet("seen", emptySet()).orEmpty()
            folders.flatMap { f -> runCatching { PhotoImporter.listFolder(context, Uri.parse(f)) }.getOrDefault(emptyList()) }
                .filter { it.toString() !in known }
        }
        if (fresh.isEmpty()) return 0
        val log = import(context, fresh) ?: return 0
        Dupes.scan(context)
        // Looked at once is enough: skipped or unreadable files aren't read again on every open.
        val seen = sp(context).getStringSet("seen", emptySet()).orEmpty() + fresh.map { it.toString() }
        sp(context).edit().putStringSet("seen", seen).apply()
        if (log.added == 0) lastLog = null // only duplicates of photos already in: nothing to tell him
        return log.added
    }

    private fun words(log: ImportLog) = buildString {
        append("${log.added} new photo${if (log.added == 1) "" else "s"}")
        if (log.duplicates > 0) append(" · ${log.duplicates} duplicate copies merged")
        if (log.exact > 0) append(" · ${log.exact} already in")
        if (log.skipped > 0) append(" · ${log.skipped} skipped (not trail-cam photos)")
        if (log.failed > 0) append(" · ${log.failed} couldn't be read")
    }
}
