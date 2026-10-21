package com.kairos.ui.bucks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.bucks.BuckAnalysis
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Photo
import com.kairos.bucks.PhotoLabel
import com.kairos.bucks.U
import com.kairos.engine.bucks.SimilarHour
import com.kairos.engine.bucks.SimilarWeather
import com.kairos.ui.KairosColors
import com.kairos.ui.LocalAppNav
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Photos full screen, Photos-app style: black, edge to edge, swipe between them, pinch or
 * double-tap to zoom, swipe down to close, tap to hide the controls. Each photo carries its
 * details, "similar weather" and the map, one tap away.
 */
@Composable
fun PhotoViewer(ids: List<String>, start: Int, onClose: () -> Unit) {
    if (ids.isEmpty()) { onClose(); return }
    val nav = LocalAppNav.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, ids.lastIndex)) { ids.size }
    var chrome by remember { mutableStateOf(true) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var similarFor by remember { mutableStateOf<String?>(null) }
    val db = BuckStore.db
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        val fade = (1f - abs(dragY) / 900f).coerceIn(0.3f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade))) {
            HorizontalPager(pager, Modifier.fillMaxSize().graphicsLayer(translationY = dragY), key = { ids[it] }) { i ->
                FullPhoto(
                    ids[i],
                    onTap = { chrome = !chrome },
                    onDragDown = { dragY = it },
                    onRelease = { if (dragY > 220f) onClose() else dragY = 0f },
                )
            }
            val p = db.photo(ids[pager.currentPage])
            AnimatedVisibility(chrome && dragY == 0f, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    // Top: close, when and where, count.
                    Row(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                            .systemBarsPadding().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            p?.let { Text(photoWhen(it), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            p?.let { Text(listOfNotNull(db.camera(it.cameraId)?.name, who(it)).joinToString("  ·  "), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium) }
                        }
                        if (ids.size > 1) Text("${pager.currentPage + 1} / ${ids.size}", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    // Bottom: the actions for this photo.
                    if (p != null) Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                            .systemBarsPadding().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        ViewerButton(Icons.Outlined.WbCloudy, "Similar weather", primary = true) { similarFor = p.id }
                        ViewerButton(Icons.Outlined.Info, "Details") { onClose(); nav.openPhotoDetails(p.id) }
                        p.epochSec?.let { t -> ViewerButton(Icons.Outlined.Map, "Map") { onClose(); nav.openMap(t, p.buckIds.firstOrNull()) } }
                    }
                }
            }
        }
        similarFor?.let { id -> SimilarWeatherSheet(id, onClose = { similarFor = null }, onPicked = { onClose() }) }
    }
}

/** Who's in it, for the caption. */
private fun who(p: Photo): String? = when (p.label) {
    PhotoLabel.BUCK -> p.buckIds.mapNotNull { BuckStore.db.buck(it) }.joinToString(", ") { if (it.pool) "Young buck" else it.name }.ifEmpty { "Buck" }
    PhotoLabel.DOE -> "Doe"
    PhotoLabel.DEER_UNKNOWN -> "Not sure"
    PhotoLabel.NO_DEER -> "No deer"
    PhotoLabel.UNSORTED -> null
}

@Composable
private fun ViewerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, primary: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (primary) Accent.brush(Accent.fern) else androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.16f)))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) Accent.on(Accent.fern) else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (primary) Accent.on(Accent.fern) else Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * One photo at full size. Two fingers (or one, once zoomed) zoom and pan; double-tap zooms to 2.5x
 * at that spot and back; a downward drag at normal size closes the viewer; sideways is left to the pager.
 */
@Composable
private fun FullPhoto(id: String, onTap: () -> Unit, onDragDown: (Float) -> Unit, onRelease: () -> Unit) {
    val bmp = rememberPhotoBitmap(id, full = true) ?: rememberPhotoBitmap(id, full = false)
    var scale by remember(id) { mutableFloatStateOf(1f) }
    var offset by remember(id) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var down = 0f
                    var vertical: Boolean? = null
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        } else {
                            val pan = event.calculatePan()
                            if (vertical == null && (abs(pan.x) > 2f || abs(pan.y) > 2f)) vertical = abs(pan.y) > abs(pan.x) * 1.4f
                            if (vertical == true) {
                                down = (down + pan.y).coerceAtLeast(0f)
                                onDragDown(down)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (vertical == true) onRelease()
                }
            }
            .pointerInput(id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { at ->
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else {
                            scale = 2.5f
                            // Keep the tapped spot under the finger.
                            offset = Offset((size.width / 2f - at.x) * 1.5f, (size.height / 2f - at.y) * 1.5f)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(
            bmp, null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE M/d")

/**
 * "Show me similar weather": future hours at the photo's camera on the photo's wind, closest
 * first; tap one to open the forecast there. Pop-up over the screen.
 */
@Composable
internal fun SimilarWeatherSheet(photoId: String, onClose: () -> Unit, onPicked: () -> Unit = {}) {
    val nav = LocalAppNav.current
    val db = BuckStore.db
    val p = db.photo(photoId) ?: run { onClose(); return }
    val res = BuckAnalysis.state
    val f = res?.photoWx?.get(photoId)
    val camId = p.cameraId
    val bundle = camId?.let { res?.cams?.get(it) }
    val zone = ZoneId.systemDefault()
    val t = p.epochSec
    Takeover(onClose, title = "Similar weather") {
        if (f == null || t == null || bundle == null || bundle.raw.isEmpty()) {
            Text(
                when {
                    camId == null || db.camera(camId)?.lat == null -> "This photo's camera needs a pin on the map first: the weather is pulled for that spot."
                    else -> "No weather for this photo yet. It downloads with signal, then works offline."
                },
                color = KairosColors.Dim,
            )
            return@Takeover
        }
        val z = Instant.ofEpochSecond(t).atZone(zone)
        val minute = z.hour * 60 + z.minute
        val r = remember(photoId, bundle) {
            SimilarWeather.find(f.windDir, f.windMph, f.tempF, minute, bundle.forecast, bundle.raw, System.currentTimeMillis() / 1000, zone)
        }
        // The photo's moment, as the yardstick.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface2).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(8.dp))) { PhotoThumb(photoId, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("THIS PHOTO", color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("${windText(f.windDir, f.windMph)}  ·  ${f.tempF?.let { U.temp(it) } ?: "-"}  ·  ${U.clock(z)}", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(db.camera(camId)?.name.orEmpty(), color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall)
            }
            com.kairos.ui.WindArrow(f.windDir, 28.dp)
        }
        fun pick(s: SimilarHour) { onClose(); onPicked(); nav.openForecast(camId, s.h.t) }
        Spacer(Modifier.height(14.dp))
        if (r.anyOnWind == 0) {
            Text("No hour in the next 16 days brings this wind (within 30°) with the two models agreeing.", color = KairosColors.Text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Check back: the forecast refreshes every hour, and the far days change the most.", color = KairosColors.Dim, style = MaterialTheme.typography.bodySmall)
            return@Takeover
        }
        val night = !f.legal
        Text(if (night) "SAME WIND, SAME TIME OF NIGHT" else "SAME WIND, SAME TIME OF DAY", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (r.sameTime.isEmpty()) Text("None within ${SimilarWeather.SAME_TIME_MIN} min of ${U.clock(z)} in 16 days.", color = KairosColors.Faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        r.sameTime.forEach { s -> SimilarRow(s, zone, clock = true) { pick(s) } }
        if (night) {
            Spacer(Modifier.height(14.dp))
            Text("SAME WIND IN SHOOTING LIGHT", color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("This photo was after dark. These are sits (first or last 3 h of light) on that wind.", color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall)
            if (r.huntable.isEmpty()) Text("None in 16 days.", color = KairosColors.Faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            r.huntable.forEach { s -> SimilarRow(s, zone, clock = false) { pick(s) } }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Same wind = within 30° (or both calm), and the two models don't disagree (past about day 14 only one model reaches). Closest first, counting the wind's direction, the time of day, the temperature and the wind speed. One per day. Tap one to see that hour in Forecast.",
            color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun SimilarRow(s: SimilarHour, zone: ZoneId, clock: Boolean, onTap: () -> Unit) {
    val z = Instant.ofEpochSecond(s.h.t).atZone(zone)
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface2).clickable(onClick = onTap).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${DAY.format(z)}  ·  ${U.clock(z).replace(":00", "")} hour" + (s.sit?.let { " · ${it.label.lowercase()} sit" } ?: ""), color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    s.windDiff?.let { if (it < 8) "same wind" else "wind ${it.roundToInt()}° off" } ?: "calm, like the photo",
                    if (!clock) null else if (s.clockGapMin <= 15) "same time" else "${s.clockGapMin} min off",
                    s.tempDiff?.let { d -> if (abs(d) < 2) "same temp" else "${U.tempDelta(abs(d))} ${if (d < 0) "colder" else "warmer"}" },
                    if (com.kairos.engine.bucks.WindCheck.of(s.h) == com.kairos.engine.bucks.ModelCheck.ONE) "long range, one model" else null,
                ).joinToString("  ·  "),
                color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.kairos.ui.WindArrow(s.h.windDir, 24.dp)
            Text(listOfNotNull(s.h.windDir?.let { U.compass(it) }, s.h.windMph?.let { "${U.speedNum(it)}" }).joinToString(" "), color = KairosColors.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            s.h.tempF?.let { Text(U.temp(it), color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
