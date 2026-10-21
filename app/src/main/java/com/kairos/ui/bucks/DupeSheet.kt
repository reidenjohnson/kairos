package com.kairos.ui.bucks

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.bucks.Dupes
import com.kairos.ui.KairosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SAVED = DateTimeFormatter.ofPattern("EEE M/d, h:mm a")

/**
 * Duplicate files, one pair at a time: the original and the copy side by side (tap one to see it
 * big), where each lives and when it was saved, then Keep both or hold Delete copy.
 */
@Composable
internal fun DupeSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pairs = Dupes.pairs
    val p = pairs.firstOrNull() ?: run { onClose(); return }
    var big by remember(p) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Takeover(onClose, title = "Duplicate photo") {
        Text(
            if (pairs.size == 1) "The same file twice: every byte matches." else "${pairs.size} to check. The same file twice: every byte matches.",
            color = KairosColors.Dim, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        key(p.drop.uri) {
            // Tapped: that one, big. Otherwise both, side by side.
            val shown = big
            if (shown != null) {
                // Tight corners: the strip's date sits in the bottom-left corner and a round one cut its first digit.
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)).clickable { big = null }) {
                    UriImage(shown, Modifier.fillMaxSize())
                    Text(
                        if (shown == p.keep.uri) "ORIGINAL" else "COPY", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text("Tap to see both again", color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Side("Original · keep", Accent.fern, p.keep, big == p.keep.uri, Modifier.weight(1f)) { big = if (big == p.keep.uri) null else p.keep.uri }
                Side("Copy · newer", Accent.brick, p.drop, big == p.drop.uri, Modifier.weight(1f)) { big = if (big == p.drop.uri) null else p.drop.uri }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryPill("Keep both", { Dupes.keepBoth(context, p) }, Modifier.weight(1f))
            // Hold, so a stray tap can't delete a file off his phone.
            Box(
                Modifier.weight(1f).height(56.dp).clip(CircleShape).background(Accent.brush(Accent.brick)).tapHold(
                    onTap = { Toast.makeText(context, "Hold to delete the copy", Toast.LENGTH_SHORT).show() },
                    onHold = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                val err = Dupes.delete(context, p)
                                busy = false
                                if (err == null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, err ?: "Deleted ${p.drop.name}", if (err == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ),
                contentAlignment = Alignment.Center,
            ) { Text("Hold to delete copy", color = Accent.on(Accent.brick), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Side(label: String, color: Color, f: Dupes.FileInfo, on: Boolean, modifier: Modifier, onTap: () -> Unit) {
    Column(modifier) {
        Text(label.uppercase(), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp))
                .then(if (on) Modifier.border(2.dp, color, RoundedCornerShape(4.dp)) else Modifier).clickable(onClick = onTap),
        ) { UriImage(f.uri, Modifier.fillMaxSize()) }
        Spacer(Modifier.height(6.dp))
        Text(f.name, color = KairosColors.Text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(f.folder, color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Saved ${SAVED.format(Instant.ofEpochMilli(f.modified).atZone(ZoneId.systemDefault()))}", color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall)
    }
}

/** A photo file on the phone, shrunk to about screen width. Keyed on the uri, so a slot never shows another file. */
@Composable
private fun UriImage(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = context.contentResolver
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                r.openInputStream(Uri.parse(uri))!!.use { BitmapFactory.decodeStream(it, null, b) }
                var s = 1
                while (b.outWidth / (s * 2) >= 1200) s *= 2
                r.openInputStream(Uri.parse(uri))!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.background(KairosColors.Surface2)) {
        // Fit, never crop: the whole frame shows, strip edges included.
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}
