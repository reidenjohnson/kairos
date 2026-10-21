package com.kairos.ui.bucks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kairos.bucks.Buck
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Face
import com.kairos.ui.KairosColors
import kotlin.math.roundToInt

/** The short mark on a buck's color circle when he has no photo yet: "1", "2", "Y". */
internal fun buckMark(b: Buck) = if (b.pool) "Y" else b.name.filter { it.isDigit() }.ifEmpty { b.name.take(1).uppercase() }

/**
 * A buck's face: the circle he drew on one of his photos, or his color with a short mark until
 * then. [face] overrides the saved one (the cropper's live preview).
 */
@Composable
internal fun BuckFace(b: Buck, size: Dp, modifier: Modifier = Modifier, face: Face? = b.face, dim: Boolean = false) {
    val color = buckColor(b.colorIdx)
    val bmp = face?.let { rememberPhotoBitmap(it.photoId, full = true) ?: rememberPhotoBitmap(it.photoId, full = false) }
    Box(modifier.size(size).clip(CircleShape).background(if (bmp == null) (if (dim) Accent.soft(color) else color) else Color.Black), contentAlignment = Alignment.Center) {
        if (face != null && bmp != null) {
            Canvas(Modifier.fillMaxSize()) {
                val side = (2 * face.r * bmp.height).coerceIn(4f, minOf(bmp.width, bmp.height).toFloat())
                val left = (face.x * bmp.width - side / 2).coerceIn(0f, bmp.width - side)
                val top = (face.y * bmp.height - side / 2).coerceIn(0f, bmp.height - side)
                drawImage(
                    bmp, srcOffset = IntOffset(left.roundToInt(), top.roundToInt()), srcSize = IntSize(side.roundToInt(), side.roundToInt()),
                    dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()), alpha = if (dim) 0.6f else 1f,
                )
            }
        } else {
            Text(buckMark(b), style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = if (dim) KairosColors.Text else Accent.on(color))
        }
    }
}

/**
 * Set a buck's profile photo: pick one of his photos (skipped when opened from a photo), then
 * drag the circle onto him and pinch to size it. The preview is exactly what every screen shows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FaceSheet(buckId: String, startPhoto: String?, onClose: () -> Unit) {
    val db = BuckStore.db
    val b = db.buck(buckId) ?: run { onClose(); return }
    var photo by remember(buckId) { mutableStateOf(startPhoto ?: b.face?.photoId) }
    var x by remember(photo) { mutableFloatStateOf(if (photo == b.face?.photoId) b.face?.x ?: 0.5f else 0.5f) }
    var y by remember(photo) { mutableFloatStateOf(if (photo == b.face?.photoId) b.face?.y ?: 0.45f else 0.45f) }
    var r by remember(photo) { mutableFloatStateOf(if (photo == b.face?.photoId) b.face?.r ?: 0.22f else 0.22f) }
    val name = if (b.pool) "Young bucks" else b.name

    Takeover(onClose, title = "$name's photo") {
        val p = photo
        if (p == null) {
            Text("Pick a clear look at him.", color = KairosColors.Dim)
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val w = (maxWidth - 14.dp) / 3
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    db.photosOf(buckId).forEach { q ->
                        Box(Modifier.width(w).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).clickable { photo = q.id }) { PhotoThumb(q.id, Modifier.fillMaxSize()) }
                    }
                }
            }
            return@Takeover
        }
        Text("Drag the circle onto him. Pinch to size it.", color = KairosColors.Dim)
        Spacer(Modifier.height(10.dp))
        val bmp = rememberPhotoBitmap(p, full = true) ?: rememberPhotoBitmap(p, full = false)
        val aspect = bmp?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
        val pine = Accent.fern
        Box(
            Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                .pointerInput(p, aspect) {
                    // One finger moves the circle; only a real two-finger pinch sizes it. It stays
                    // inside the photo and above the camera's info strip (bottom ~6%).
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val ev = awaitPointerEvent()
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            if (ev.changes.count { it.pressed } >= 2) r = (r * ev.calculateZoom()).coerceIn(0.06f, 0.45f)
                            val pan = ev.calculatePan()
                            x = (x + pan.x / w).coerceIn(r * h / w, 1f - r * h / w)
                            y = (y + pan.y / h).coerceIn(r, (0.94f - r).coerceAtLeast(r))
                            ev.changes.forEach { it.consume() }
                        } while (ev.changes.any { it.pressed })
                    }
                },
        ) {
            PhotoThumb(p, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, full = true)
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(x * size.width, y * size.height)
                val rad = r * size.height
                // Everything outside the circle dimmed, so it reads like a profile crop.
                val shade = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    addOval(androidx.compose.ui.geometry.Rect(c.x - rad, c.y - rad, c.x + rad, c.y + rad))
                }
                drawPath(shade, Color.Black.copy(alpha = 0.55f))
                drawCircle(Color.White, rad, c, style = Stroke(2.5.dp.toPx()))
                drawCircle(pine, rad + 2.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BuckFace(b, 64.dp, face = Face(p, x, y, r))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("How he'll look", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("On the map, your Bucks list and his page.", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryPill("Other photo", { photo = null }, Modifier.weight(1f))
            PrimaryPill("Save", {
                BuckStore.db.buck(buckId)?.let { BuckStore.saveBuck(it.copy(face = Face(p, x, y, r))) }
                onClose()
            }, Modifier.weight(1f))
        }
        if (b.face != null) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.TextButton(onClick = { BuckStore.saveBuck(b.copy(face = null)); onClose() }) { Text("Remove photo", color = KairosColors.Dim) }
        }
    }
}

