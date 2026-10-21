package com.kairos.ui.bucks

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Photo
import com.kairos.engine.bucks.compass8
import com.kairos.ui.KairosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * Buck identity colors, from the brand palette only (Theme.kt Brand): Teal, Amber, Fern, then a
 * deep Teal / light Fern. Checked 2026-10-07 with the dataviz validator on the Kairos surfaces:
 * buck-to-buck separation passes for colorblind and normal vision; the brand hues are muted by
 * design (they flag low chroma), so a buck's color always sits next to his name. Assigned in
 * creation order and stored on the buck, so a buck keeps his color.
 */
internal fun buckColor(idx: Int): Color {
    val light = longArrayOf(0xFF167C93, 0xFFDE8521, 0xFF566E3F, 0xFF074552, 0xFF8CA26E, 0xFFB86A1A)
    val dark = longArrayOf(0xFF57A6BA, 0xFFE89A45, 0xFF6E8F4E, 0xFFB4CF95, 0xFF2E7F93, 0xFFF2B068)
    return Color((if (KairosColors.dark) dark else light)[((idx % 6) + 6) % 6])
}

/** Neutral for "the camera's hours" (the baseline) in every comparison chart. */
internal val baselineColor: Color get() = KairosColors.Slow

private val thumbs = LruCache<String, ImageBitmap>(160)
private val fulls = LruCache<String, ImageBitmap>(6)

@Composable
internal fun rememberPhotoBitmap(id: String, full: Boolean): ImageBitmap? {
    val cache = if (full) fulls else thumbs
    // Every bitmap travels with the id it was loaded for, and is only returned when that id is the
    // one asked for. A reused slot (map bubble, timeline pin, pager page) can never show another
    // photo's picture: it shows nothing for a moment instead. produceState keeps its last value
    // across key changes, which is how a stale image once showed at the wrong camera.
    val v by produceState(cache.get(id)?.let { id to it }, id, full) {
        val bmp = cache.get(id) ?: withContext(Dispatchers.IO) {
            val f = if (full) BuckStore.imageFile(id) else BuckStore.thumbFile(id)
            runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()?.also { cache.put(id, it) }
        }
        value = bmp?.let { id to it }
    }
    return v?.takeIf { it.first == id }?.second
}

@Composable
internal fun PhotoThumb(id: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, alignment: Alignment = Alignment.Center, full: Boolean = false) {
    val bmp = rememberPhotoBitmap(id, full = full) ?: if (full) rememberPhotoBitmap(id, full = false) else null
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(KairosColors.Surface2)) {
        if (bmp != null) Image(bmp, null, contentScale = contentScale, alignment = alignment, modifier = Modifier.fillMaxSize())
    }
}

/** Full photo, pinch to zoom and drag; double-tap resets. */
@Composable
internal fun ZoomPhoto(id: String, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null, onHold: (() -> Unit)? = null) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bmp = rememberPhotoBitmap(id, full = true) ?: rememberPhotoBitmap(id, full = false)
    var scale by remember(id) { mutableFloatStateOf(1f) }
    var offset by remember(id) { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            // Pinch (two fingers) or a pan while zoomed in is ours; a one-finger swipe at normal size
            // is left alone so a pager around this photo can move to the next one.
            .pointerInput(id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(id, onTap, onHold) {
                detectTapGestures(
                    onDoubleTap = { scale = 1f; offset = Offset.Zero },
                    onTap = { if (scale == 1f) onTap?.invoke() },
                    onLongPress = { if (onHold != null) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onHold() } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bmp, null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
    }
}

@Composable
internal fun Pill(text: String, color: Color = KairosColors.Dim, modifier: Modifier = Modifier) {
    Box(modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = KairosColors.Text, fontWeight = FontWeight.Medium)
    }
}

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE MMM d")
internal fun photoWhen(p: Photo): String = p.capture?.let { DAY_FMT.format(it) + ", " + com.kairos.bucks.U.clock(it) } ?: "Time unknown"

/** "SW 6 mph" in his units; "Calm" under 2 mph. */
internal fun windText(dir: Double?, mph: Double?): String = com.kairos.bucks.U.wind(dir, mph)

/** The Kairos brand moment (Fern gradient, same as the drawer's active item): primary buttons, the plan, selections. */
internal val brandBrush: androidx.compose.ui.graphics.Brush
    get() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(KairosColors.SegTop, KairosColors.SegBottom))

/**
 * Bucks accents = the Kairos brand palette only (Theme.kt Brand): Fern and Teal carry most of the
 * accent work, Amber is a minority highlight for "needs you", Brick is only for destructive or
 * negative. Theme-aware tokens from KairosColors, so light and dark both stay on-brand.
 */
internal object Accent {
    /** Fern: primary actions, selection, "go". */
    val fern get() = KairosColors.Pine
    /** Teal (lifted for legibility): links, timing, info, active states. */
    val teal get() = KairosColors.Water
    /** Amber: highlights and things waiting on him. Used sparingly. */
    val amber get() = KairosColors.Fair
    /** Brick: delete / negative only. */
    val brick get() = KairosColors.Error
    /** The brand's deep Teal, for hero surfaces. */
    val deepTeal get() = if (KairosColors.dark) Color(0xFF0A4F5E) else Color(0xFF074552)
    /** Lifted brand hues for text over photos (always a dark scrim behind them). */
    val tealOnPhoto = Color(0xFF8FD0DE)
    val amberOnPhoto = Color(0xFFF2B068)
    val fernOnPhoto = Color(0xFFB4CF95)

    fun brush(c: Color) = androidx.compose.ui.graphics.Brush.linearGradient(listOf(c, androidx.compose.ui.graphics.lerp(c, Color.Black, 0.22f)))
    /** Readable text on a solid accent: dark ink on Amber, near-white on Fern / Teal. */
    fun on(c: Color): Color = if (c.luminance() > 0.33f) Color(0xFF1E201D) else KairosColors.OnSeg
    fun soft(c: Color) = c.copy(alpha = if (KairosColors.dark) 0.18f else 0.10f)
    /** The plan hero: deep brand Teal into Fern. */
    val planBrush get() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(deepTeal, androidx.compose.ui.graphics.lerp(fern, Color.Black, 0.30f)))
}

