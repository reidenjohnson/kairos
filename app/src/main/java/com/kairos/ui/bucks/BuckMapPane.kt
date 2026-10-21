package com.kairos.ui.bucks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kairos.bucks.BuckAnalysis
import com.kairos.bucks.BuckPrefs
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Camera
import com.kairos.bucks.Photo
import com.kairos.bucks.PhotoLabel
import com.kairos.bucks.Quarters
import com.kairos.bucks.U
import com.kairos.data.WindMath
import com.kairos.data.WindSampler
import com.kairos.engine.bucks.Sun
import com.kairos.ui.BaseMap
import com.kairos.ui.KairosColors
import com.kairos.ui.WIND_BUCKETS_MPH
import com.kairos.ui.WIND_COLORS_ON_DARK
import com.kairos.ui.WindParticleOverlay
import com.kairos.ui.baseStyleUri
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One pin on the timeline: a visit (or a single photo) of the filtered deer at one camera. */
private data class Ev(val t: Long, val camId: String, val photoIds: List<String>, val color: Color, val who: String)

/**
 * The buck map: satellite with every pinned camera and its view cone, the wind drifting across it
 * for the moment on the timeline (HRRR every 15 min at each camera, blended between cameras and
 * between steps), the weather for that moment top right, and a timeline of his visits along the
 * bottom. Scrub the timeline, tap a pin to jump to it, or press play to watch the wind turn.
 */
@Composable
internal fun BuckMapPane(
    startTime: Long?,
    startBuck: String?,
    onOpenPhoto: (String) -> Unit,
    onPinCamera: (String) -> Unit,
    onGallery: (String) -> Unit,
    onCamerasTab: () -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) }
    val db = BuckStore.db
    val res = BuckAnalysis.state
    val zone = ZoneId.systemDefault()
    val pinned = db.cameras.filter { it.lat != null && it.lon != null }
    if (pinned.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pin a camera first", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("The map shows your cameras, the wind at them, and every visit on a timeline.", color = KairosColors.Dim)
            Spacer(Modifier.height(20.dp))
            PrimaryPill("Back to your cameras", onCamerasTab)
        }
        return
    }

    // ---- filters: which deer, visits or single photos (saved, so back returns to the same view)
    var picked by rememberSaveable { mutableStateOf(startBuck?.let { listOf("b:$it") } ?: emptyList()) }
    var perPhoto by rememberSaveable { mutableStateOf(false) }
    val events = remember(db.photos, db.bucks, picked, perPhoto, BuckPrefs.visitMin) { buildEvents(picked.toSet(), perPhoto) }

    val now = System.currentTimeMillis() / 1000
    val first = minOf(events.minOfOrNull { it.t } ?: now, db.photos.mapNotNull { it.epochSec }.minOrNull() ?: now)
    val rangeStart = first - 12 * 3600
    val axis0 = remember(rangeStart) { rangeStart }
    // The forecast carries the wind on past now (16 days), so the map can play what's coming.
    val end = maxOf(now, res?.cams?.values?.maxOfOrNull { it.end } ?: now)
    var t by rememberSaveable { mutableLongStateOf(startTime ?: events.lastOrNull()?.t ?: now) }
    t = t.coerceIn(rangeStart, end)
    // His sits ahead on the timeline: the buck shown alone, else your #1.
    val sitBuck = picked.singleOrNull()?.removePrefix("b:")?.takeIf { picked.single().startsWith("b:") } ?: db.mainBuck?.id
    val sits = sitBuck?.let { id -> res?.windows?.get(id)?.let { ws -> com.kairos.engine.bucks.HuntWindows.ranked(ws).take(6) } }.orEmpty()
    val sitColor = sitBuck?.let { db.buck(it) }?.let { buckColor(it.colorIdx) } ?: Accent.fern
    var playing by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableIntStateOf(900) } // seconds of time per real second
    var focusCam by rememberSaveable { mutableStateOf<String?>(null) }
    // Tapped camera's pop-up; saved so back from one of its photos returns to it.
    var sheetCam by rememberSaveable { mutableStateOf<String?>(null) }
    var sortId by rememberSaveable { mutableStateOf<String?>(null) }
    var filterOpen by remember { mutableStateOf(false) }
    // Bottom sheet height in dp: any height you drag it to, 0 = tucked away. Saved for back.
    var sheetH by rememberSaveable { mutableFloatStateOf(250f) }
    // The two stops, measured from the content itself so each shows exactly its rows.
    var minH by rememberSaveable { mutableFloatStateOf(84f) }
    var medH by rememberSaveable { mutableFloatStateOf(250f) }
    var sheetSet by rememberSaveable { mutableStateOf(false) }
    var wxOpen by remember { mutableStateOf(false) }

    // Visits within the window around the playhead pop up on the map.
    val live = events.filter { abs(it.t - t) <= maxOf(BuckPrefs.visitGapSec, 15 * 60L) }
    val camForWx = live.firstOrNull()?.camId ?: focusCam ?: pinned.first().id
    val bundle = res?.cams?.get(camForWx)

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        while (playing) {
            withFrameNanos { n ->
                if (last != 0L) t = (t + ((n - last) / 1e9 * speed).toLong()).coerceAtMost(end)
                last = n
            }
            if (t >= end) playing = false
        }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var moved by remember { mutableIntStateOf(0) }
    var idle by remember { mutableIntStateOf(0) }
    val sampler = remember(res, axis0) { res?.let { CamWindSampler(pinned.mapNotNull { c -> it.cams[c.id]?.let { b -> c to b } }, axis0) } }

    Box(Modifier.fillMaxSize()) {
        BuckMapView { m ->
            map = m
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            // Logo + (i) under the back button, clear of the weather card and the timeline.
            m.uiSettings.logoGravity = android.view.Gravity.TOP or android.view.Gravity.START
            m.uiSettings.attributionGravity = android.view.Gravity.TOP or android.view.Gravity.START
            val dp = context.resources.displayMetrics.density
            m.uiSettings.setLogoMargins((14 * dp).toInt(), (68 * dp).toInt(), 0, 0)
            m.uiSettings.setAttributionMargins((100 * dp).toInt(), (68 * dp).toInt(), 0, 0)
            m.setStyle(Style.Builder().fromUri(baseStyleUri(context, BaseMap.AERIAL)))
            if (pinned.size == 1) {
                m.cameraPosition = CameraPosition.Builder().target(LatLng(pinned[0].lat!!, pinned[0].lon!!)).zoom(16.5).padding(0.0, 0.0, 0.0, 300.0 * dp).build()
            } else {
                val b = LatLngBounds.Builder().apply { pinned.forEach { include(LatLng(it.lat!!, it.lon!!)) } }.build()
                // Clear of the weather card (top right) and the timeline panel (bottom).
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(b, (56 * dp).toInt(), (120 * dp).toInt(), (56 * dp).toInt(), (330 * dp).toInt()))
                if (m.cameraPosition.zoom > 17.5) m.moveCamera(CameraUpdateFactory.zoomTo(17.5))
            }
            m.addOnCameraMoveListener { moved++ }
            m.addOnCameraIdleListener { idle++ }
            m.addOnMapClickListener { ll ->
                // Tap near a camera: its weather in the card.
                val p = m.projection.toScreenLocation(ll)
                val hit = pinned.minByOrNull { c -> m.projection.toScreenLocation(LatLng(c.lat!!, c.lon!!)).let { (it.x - p.x) * (it.x - p.x) + (it.y - p.y) * (it.y - p.y) } }
                if (hit != null) {
                    val q = m.projection.toScreenLocation(LatLng(hit.lat!!, hit.lon!!))
                    if ((q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y) < (48 * dp) * (48 * dp)) { focusCam = hit.id; playing = false; sheetCam = hit.id }
                }
                true
            }
        }

        WindParticleOverlay(map, sampler, ((t - axis0) / 3600.0).toFloat(), darkGround = true, settleKey = idle)

        // Cameras: view cones, dots, names; the one in the weather card is ringed.
        val tm = rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val tick = moved
            val m = map ?: return@Canvas
            pinned.forEach { c ->
                val sp = m.projection.toScreenLocation(LatLng(c.lat!!, c.lon!!))
                val pt = Offset(sp.x, sp.y)
                c.facingDeg?.let { f ->
                    val len = 46.dp.toPx()
                    val cone = Path().apply {
                        moveTo(pt.x, pt.y)
                        for (k in 0..10) {
                            val a = Math.toRadians(f - 27.5 + 55.0 * k / 10 - 90)
                            lineTo(pt.x + len * cos(a).toFloat(), pt.y + len * sin(a).toFloat())
                        }
                        close()
                    }
                    drawPath(cone, Color.White.copy(alpha = 0.18f))
                    drawPath(cone, Color.White.copy(alpha = 0.6f), style = Stroke(1.2.dp.toPx()))
                }
                if (c.id == camForWx) drawCircle(Color(0xFFF2B068), 11.dp.toPx(), pt, style = Stroke(2.dp.toPx()))
                // His wind for this stand: Fern arcs on a ring, where those winds come from.
                if (c.windSectors.isNotEmpty()) {
                    val rr = 22.dp.toPx()
                    drawCircle(Color.Black.copy(alpha = 0.35f), rr, pt, style = Stroke(5.dp.toPx()))
                    c.windSectors.forEach { s ->
                        drawArc(Accent.fernOnPhoto, (s * 22.5 - 11.25 - 90).toFloat() + 1f, 20.5f, false,
                            Offset(pt.x - rr, pt.y - rr), androidx.compose.ui.geometry.Size(2 * rr, 2 * rr), style = Stroke(5.dp.toPx()))
                    }
                }
                drawCircle(Color.White, 6.dp.toPx(), pt)
                drawCircle(Color.Black, 4.dp.toPx(), pt)
                drawText(tm, c.name, pt + Offset(10.dp.toPx(), -20.dp.toPx()), TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 6f)))
            }
        }

        // Live visits pop up over their camera.
        val density = LocalDensity.current
        map?.let { m ->
            @Suppress("UNUSED_VARIABLE") val tick = moved
            live.groupBy { it.camId }.forEach { (camId, evs) ->
                val c = db.camera(camId) ?: return@forEach
                if (c.lat == null || c.lon == null) return@forEach
                val sp = m.projection.toScreenLocation(LatLng(c.lat, c.lon))
                val e = evs.minBy { abs(it.t - t) }
                val w = 116.dp; val h = 66.dp
                androidx.compose.runtime.key(e.photoIds.first()) { Box(
                    Modifier.offset { IntOffset((sp.x - with(density) { w.toPx() } / 2).roundToInt(), (sp.y - with(density) { (h + 18.dp).toPx() }).roundToInt()) }
                        .width(w).height(h).clip(RoundedCornerShape(10.dp)).border(2.5.dp, e.color, RoundedCornerShape(10.dp))
                        .clickable { e.photoIds.firstOrNull()?.let(onOpenPhoto) },
                ) {
                    PhotoThumb(e.photoIds.first(), Modifier.fillMaxSize())
                    Text(
                        e.who, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                } }
            }
        }

        // The only chrome up top: back.
        Box(
            Modifier.align(Alignment.TopStart).padding(12.dp).size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }

        // Weather for this moment, top right.
        WeatherCard(camName = db.camera(camForWx)?.name, bundle = bundle, t = t, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) { wxOpen = true }

        // Bottom: a sheet you drag to any height. Most useful first, so whatever height it's at,
        // what shows is usable: time + play, then the timeline, the filters, then the details.
        // Three stops: MINIMAL (time + play), MEDIUM (+ timeline + filters), TALL (most of the page:
        // + wind every 15 min + every visit). Release snaps to the nearest; a flick goes one stop
        // further. Below minimal it tucks away and the little tab brings it back.
        val maxH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.86f
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        fun animateTo(target: Float) {
            scope.launch { androidx.compose.animation.core.animate(sheetH, target) { v, _ -> sheetH = v } }
        }
        val drag = androidx.compose.foundation.gestures.rememberDraggableState { d -> sheetH = (sheetH - d / density.density).coerceIn(0f, maxH) }
        val dragMod = Modifier.draggable(
            drag, androidx.compose.foundation.gestures.Orientation.Vertical,
            onDragStopped = { v ->
                val stops = listOf(0f, minH, medH, maxH)
                val target = when {
                    sheetH < minH * 0.6f -> 0f
                    v > 1500f -> stops.lastOrNull { it < sheetH - 4f } ?: 0f          // flick down: next stop below
                    v < -1500f -> stops.firstOrNull { it > sheetH + 4f } ?: maxH      // flick up: next stop above
                    else -> stops.drop(1).minBy { abs(it - sheetH) }                  // nearest stop
                }
                animateTo(target)
            },
        )
        if (sheetH < 1f) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).clip(CircleShape).background(KairosColors.Surface.copy(alpha = 0.92f))
                    .then(dragMod).clickable { animateTo(medH) }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, null, tint = KairosColors.Text)
                Spacer(Modifier.width(6.dp))
                val z = Instant.ofEpochSecond(t).atZone(zone)
                Text("Timeline  ·  " + DateTimeFormatter.ofPattern("EEE MMM d").format(z) + ", " + U.clock(z), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        } else Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(sheetH.dp).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(KairosColors.Surface),
        ) {
            var topPx by remember { mutableIntStateOf(0) }
            Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
                Column(Modifier.onSizeChanged {
                    topPx = it.height
                    medH = it.height / density.density
                    if (!sheetSet) { sheetH = medH; sheetSet = true } // first open: exactly medium
                }) {
                    // Grab here to drag: the handle and the time row.
                    Column(dragMod.fillMaxWidth().onSizeChanged { minH = it.height / density.density }.padding(top = 8.dp, bottom = 6.dp)) {
                        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(CircleShape).background(KairosColors.Line))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val z = Instant.ofEpochSecond(t).atZone(zone)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(DateTimeFormatter.ofPattern("EEE MMM d").format(z) + "  ·  " + U.clock(z), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    if (t > now + 15 * 60) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.clip(CircleShape).background(Accent.soft(Accent.teal)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                            Text("FORECAST", color = Accent.teal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                val near = events.minByOrNull { abs(it.t - t) }
                                Text(
                                    if (events.isEmpty()) "No visits for this filter" else near?.let { "${events.size} ${if (perPhoto) "photos" else "visits"} · nearest: ${it.who}, " + gap(it.t - t) } ?: "",
                                    style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, maxLines = 1,
                                )
                            }
                            RoundBtn(Icons.AutoMirrored.Filled.ArrowBack, "Previous visit") { events.lastOrNull { it.t < t - 60 }?.let { playing = false; t = it.t } }
                            Spacer(Modifier.width(6.dp))
                            RoundBtn(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", primary = true) {
                                if (!playing && t >= end - 60) t = (events.firstOrNull()?.t ?: rangeStart)
                                playing = !playing
                            }
                            Spacer(Modifier.width(6.dp))
                            RoundBtn(Icons.AutoMirrored.Filled.ArrowForward, "Next visit") { events.firstOrNull { it.t > t + 60 }?.let { playing = false; t = it.t } }
                        }
                    }
                    Box {
                        Timeline(
                            events = events, t = t, rangeStart = rangeStart, now = now, end = end,
                            sits = sits.map { it.start to it.end }, sitColor = sitColor,
                            lat = pinned.first().lat!!, lon = pinned.first().lon!!, zone = zone,
                            onScrub = { playing = false; t = it.coerceIn(rangeStart, end) },
                            onPick = { e -> playing = false; t = e.t },
                        )
                        // Jump to right now (hidden when already there).
                        if (abs(now - t) > 30 * 60) Row(
                            Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = 6.dp).clip(CircleShape).background(Accent.teal)
                                .clickable { playing = false; t = now }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (t > now) Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Accent.on(Accent.teal), modifier = Modifier.size(16.dp))
                            Text("Now", color = Accent.on(Accent.teal), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (t < now) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent.on(Accent.teal), modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Who's on the timeline: one tap on a buck's circle shows just him (tap again for
                    // all), like People in a photos app. The filter button holds the rarer choices
                    // (several at once, Not sure, does, every photo) and shows a dot when one is on.
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("All", "All", null, picked.isEmpty()) { picked = emptyList() }
                        db.bucks.sortedBy { it.pool }.forEach { b ->
                            val k = "b:${b.id}"
                            val only = picked == listOf(k)
                            val mark = if (b.pool) "Y" else b.name.filter { it.isDigit() }.ifEmpty { b.name.take(1).uppercase() }
                            Avatar(if (b.pool) "Young" else b.name, mark, buckColor(b.colorIdx), k in picked, b) { picked = if (only) emptyList() else listOf(k) }
                        }
                        val extra = perPhoto || picked.size > 1 || "unsure" in picked || "doe" in picked
                        Box(
                            Modifier.padding(start = 4.dp).size(40.dp).clip(CircleShape).background(KairosColors.Surface2).clickable { filterOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.FilterList, "More filters", tint = KairosColors.Text, modifier = Modifier.size(20.dp))
                            if (extra) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(8.dp).background(Accent.amber, CircleShape))
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.clip(CircleShape).background(KairosColors.Surface2).clickable { speed = when (speed) { 900 -> 3600; 3600 -> 4 * 3600; else -> 900 } }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Speed, "Playback speed", tint = KairosColors.Text, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(speedLabel(speed), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // Pull higher for the details: they scroll in whatever room is left.
                val room = with(density) { (sheetH.dp.toPx() - topPx).coerceAtLeast(0f).toDp() }
                Column(Modifier.fillMaxWidth().height(room).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    bundle?.let { b ->
                        val qs = Quarters.between(b.quarters, t - 2 * 3600, t + 2 * 3600)
                        if (qs.size > 2) {
                            Text("WIND AROUND THIS MOMENT · ${db.camera(camForWx)?.name ?: ""}", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            val strip = rememberScrollState()
                            val nowIdx = qs.indexOfFirst { abs(it.t - t) < 450 }.coerceAtLeast(0)
                            LaunchedEffect(nowIdx, qs.size) { strip.scrollTo(((nowIdx - 3) * 52 * density.density).toInt().coerceAtLeast(0)) }
                            Row(Modifier.horizontalScroll(strip), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                qs.forEach { x ->
                                    val on = abs(x.t - t) < 450
                                    Column(
                                        Modifier.width(50.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Accent.soft(Accent.teal) else Color.Transparent)
                                            .clickable { playing = false; t = x.t }.padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(U.clock(x.t, zone).replace(" AM", "a").replace(" PM", "p"), style = MaterialTheme.typography.labelSmall, color = if (on) Accent.teal else KairosColors.Dim)
                                        Spacer(Modifier.height(4.dp))
                                        if (x.dir != null && (x.mph ?: 0.0) >= 2) WindArrow(x.dir, Modifier.size(20.dp)) else Box(Modifier.size(20.dp))
                                        Text(x.mph?.let { "${U.speedNum(it)}" } ?: "?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                    Text((if (perPhoto) "PHOTOS" else "VISITS") + " · newest first", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    events.asReversed().forEach { e ->
                        val z = Instant.ofEpochSecond(e.t).atZone(zone)
                        val cb = res?.cams?.get(e.camId)
                        val w = cb?.let { Quarters.at(it.quarters, e.t) }
                        val here = abs(e.t - t) < 60
                        androidx.compose.runtime.key(e.photoIds.first()) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (here) Accent.soft(Accent.amber) else Color.Transparent)
                                    .tapHold(onTap = { playing = false; t = e.t }, onHold = { sortId = e.photoIds.first() }).padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).border(2.dp, e.color, RoundedCornerShape(6.dp))) { PhotoThumb(e.photoIds.first(), Modifier.fillMaxSize()) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.who, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        listOfNotNull(DateTimeFormatter.ofPattern("EEE MMM d").format(z) + ", " + U.clock(z), db.camera(e.camId)?.name, w?.let { U.wind(it.dir, it.mph) }).joinToString("  ·  "),
                                        style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim,
                                    )
                                }
                                Box(Modifier.size(36.dp).clip(CircleShape).clickable { onOpenPhoto(e.photoIds.first()) }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Open photo", tint = KairosColors.Dim, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap a visit to jump to it, hold to sort it. Wind: NOAA HRRR every 15 min at your cameras, blended between them; particles drift the way the air moved.",
                        style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    sortId?.let { SortSheet(listOf(it), onClose = { sortId = null }) }
    if (filterOpen) Takeover({ filterOpen = false }, title = "Show on the timeline") {
        Text("DEER", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("All bucks", picked.isEmpty(), null) { picked = emptyList() }
            db.bucks.sortedBy { it.pool }.forEach { b ->
                val k = "b:${b.id}"
                ToggleChip(if (b.pool) "Young bucks" else b.name, k in picked, buckColor(b.colorIdx)) { picked = if (k in picked) picked - k else picked + k }
            }
            ToggleChip("Not sure", "unsure" in picked, null) { picked = if ("unsure" in picked) picked - "unsure" else picked + "unsure" }
            ToggleChip("Does", "doe" in picked, null) { picked = if ("doe" in picked) picked - "doe" else picked + "doe" }
        }
        Spacer(Modifier.height(18.dp))
        Text("PINS", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Seg(listOf("One per visit", "Every photo"), if (perPhoto) 1 else 0) { perPhoto = it == 1 }
        Spacer(Modifier.height(6.dp))
        Text(
            "A visit = photos at one camera within ${com.kairos.bucks.BuckPrefs.visitMin} min (change it in Bucks settings).",
            style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryPill("Show ${events.size} ${if (perPhoto) "photo" else "visit"}${if (events.size == 1) "" else "s"}", { filterOpen = false }, Modifier.fillMaxWidth())
    }
    sheetCam?.let { id -> CameraSheet(id, onClose = { sheetCam = null }, onOpenPhoto = onOpenPhoto, onGallery = onGallery, onPin = onPinCamera) }

    if (wxOpen) {
        val f = bundle?.at(t)
        if (f != null) WeatherTakeover(null, f, bundle, t) { wxOpen = false } else wxOpen = false
    }
}

private fun speedLabel(s: Int) = when (s) { 900 -> "15m/s"; 3600 -> "1h/s"; else -> "4h/s" }

private fun gap(sec: Long): String {
    val a = abs(sec)
    val s = when {
        a < 90 -> return "now"
        a < 3600 -> "${a / 60} min"
        a < 48 * 3600 -> "${(a / 3600.0).roundToInt()} h"
        else -> "${(a / 86400.0).roundToInt()} days"
    }
    return if (sec > 0) "in $s" else "$s ago"
}

/** Visits (or photos) of the picked deer, oldest first. Empty pick = every buck + the Not sure pile. */
private fun buildEvents(picked: Set<String>, perPhoto: Boolean): List<Ev> {
    val db = BuckStore.db
    fun wanted(p: Photo): Boolean = when {
        p.epochSec == null || p.cameraId == null -> false
        picked.isEmpty() -> p.label == PhotoLabel.BUCK || p.label == PhotoLabel.DEER_UNKNOWN
        else -> p.buckIds.any { "b:$it" in picked } ||
            ("unsure" in picked && p.label == PhotoLabel.DEER_UNKNOWN) || ("doe" in picked && p.label == PhotoLabel.DOE)
    }
    fun colorOf(p: Photo) = p.buckIds.firstNotNullOfOrNull { db.buck(it) }?.let { buckColor(it.colorIdx) } ?: Color(0xFFB8BDB5)
    val ps = db.photos.filter(::wanted).sortedBy { it.epochSec }
    if (perPhoto) return ps.map { Ev(it.epochSec!!, it.cameraId!!, listOf(it.id), colorOf(it), whoLabel(it)) }
    val out = ArrayList<MutableList<Photo>>()
    for (p in ps) {
        val g = out.lastOrNull { it.last().cameraId == p.cameraId && it.last().buckIds == p.buckIds && it.last().label == p.label }
        if (g != null && p.epochSec!! - g.last().epochSec!! <= BuckPrefs.visitGapSec && g === out.last()) g += p else out += mutableListOf(p)
    }
    return out.map { g -> Ev(g.first().epochSec!!, g.first().cameraId!!, g.map { it.id }, colorOf(g.first()), whoLabel(g.first()) + if (g.size > 1) " ·${g.size}" else "") }
}

/**
 * The wind field the particles read: each camera's own 15-minute HRRR wind at the moment (hourly
 * if a quarter-hour is missing), blended between cameras by inverse distance. Over a property the
 * cameras sit inside one or two 3 km model cells, so this is what the model says there, no more.
 */
private class CamWindSampler(private val cams: List<Pair<Camera, BuckAnalysis.CamBundle>>, private val axis0: Long) : WindSampler(null, emptyList()) {
    private val n = cams.size
    private val la = DoubleArray(n) { cams[it].first.lat!! }
    private val lo = DoubleArray(n) { cams[it].first.lon!! }
    private val u = FloatArray(n)
    private val v = FloatArray(n)
    private val ok = BooleanArray(n)
    private var lastT = Long.MIN_VALUE

    override fun setHour(hour: Float) {
        val t = axis0 + (hour * 3600.0).toLong()
        if (abs(t - lastT) < 20) return
        lastT = t
        for (i in 0 until n) {
            val b = cams[i].second
            val q = Quarters.at(b.quarters, t)
            val dir = q?.dir ?: b.at(t)?.windDir
            val mph = q?.mph ?: b.at(t)?.windMph
            ok[i] = dir != null && mph != null
            if (ok[i]) { u[i] = WindMath.u(mph!!, dir!!).toFloat(); v[i] = WindMath.v(mph, dir).toFloat() }
        }
    }

    override fun sample(lat: Double, lon: Double, out: FloatArray): Float {
        var su = 0.0; var sv = 0.0; var sw = 0.0
        for (i in 0 until n) {
            if (!ok[i]) continue
            val d2 = (lat - la[i]) * (lat - la[i]) + (lon - lo[i]) * (lon - lo[i]) + 1e-9
            val w = 1.0 / d2
            su += u[i] * w; sv += v[i] * w; sw += w
        }
        if (sw == 0.0) return -1f
        out[0] = (su / sw).toFloat(); out[1] = (sv / sw).toFloat()
        return 0f
    }
}

/** Top-right: just what a hunter needs at a glance. Tap for the full close-up. */
@Composable
private fun WeatherCard(camName: String?, bundle: BuckAnalysis.CamBundle?, t: Long, modifier: Modifier, onTap: () -> Unit) {
    val f = bundle?.at(t)
    val q = bundle?.let { Quarters.at(it.quarters, t) }
    Column(
        modifier.width(176.dp).clip(RoundedCornerShape(20.dp)).background(KairosColors.Surface.copy(alpha = 0.66f)).clickable(onClick = onTap).padding(14.dp),
    ) {
        if (f == null && q == null) {
            Text("No weather yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Needs signal once to download.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text((q?.tempF ?: f?.tempF)?.let { U.temp(it) } ?: "?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            f?.departureF?.let { Text((if (it >= 0) "+" else "−") + U.tempDelta(abs(it)) + " norm", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, modifier = Modifier.padding(bottom = 6.dp)) }
        }
        WindTag(q?.dir ?: f?.windDir, q?.mph ?: f?.windMph, big = true)
        (q?.gust ?: f?.gustMph)?.let { Text("gusts ${U.speed(it)}", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim) }
        Spacer(Modifier.height(6.dp))
        f?.pressureHpa?.let { p ->
            val arrow = f.pressTrend3h?.let { if (it <= -1) " ↓" else if (it >= 1) " ↑" else " →" } ?: ""
            Text(U.pressure(p) + arrow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        f?.let {
            Text("Moon ${(it.moonIllum * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(lightLine(it).substringAfter(" · ").ifEmpty { it.light.label }, style = MaterialTheme.typography.labelSmall, color = if (it.legal) Accent.fern else KairosColors.Dim)
        }
        camName?.let { Text("at $it", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint) }
        Spacer(Modifier.height(6.dp))
        // Particle colors, in his units.
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..4) {
                Box(Modifier.size(7.dp).background(Color(WIND_COLORS_ON_DARK[i]).let { if (i == 1) Color(0xFFE6E6E6) else it }, CircleShape).border(0.5.dp, Color.Black.copy(alpha = 0.3f), CircleShape))
                Spacer(Modifier.width(2.dp))
                Text("${U.speedNum(WIND_BUCKETS_MPH[i - 1].toDouble())}" + if (i == 4) "+" else "", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                Spacer(Modifier.width(5.dp))
            }
        }
    }
}

/**
 * Days run left to right with nights shaded; his visits are pins. Drag to scrub (the line in the
 * middle is the moment on the map), pinch to zoom, tap a pin to jump to it.
 */
@Composable
private fun Timeline(
    events: List<Ev>, t: Long, rangeStart: Long, now: Long, end: Long,
    sits: List<Pair<Long, Long>>, sitColor: Color,
    lat: Double, lon: Double, zone: ZoneId,
    onScrub: (Long) -> Unit, onPick: (Ev) -> Unit,
) {
    var dpPerHour by rememberSaveable { mutableFloatStateOf(9f) }
    val density = LocalDensity.current
    val tm = rememberTextMeasurer()
    val dim = KairosColors.Dim
    val night = KairosColors.Surface2
    val line = KairosColors.Line
    val head = Accent.amber
    val fcTint = Accent.soft(Accent.teal)
    val fcLine = Accent.teal
    val tNow by androidx.compose.runtime.rememberUpdatedState(t)
    val scrub by androidx.compose.runtime.rememberUpdatedState(onScrub)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(92.dp).pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                dpPerHour = (dpPerHour * zoom).coerceIn(1.5f, 90f)
                val pxPerHour = dpPerHour * density.density
                scrub(tNow - (pan.x / pxPerHour * 3600).toLong())
            }
        },
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val pxPerSec = dpPerHour * density.density / 3600f
        fun x(at: Long) = wPx / 2 + (at - t) * pxPerSec
        Canvas(Modifier.fillMaxSize()) {
            val axisY = size.height - 22.dp.toPx()
            // Nights shaded, days labeled at local midnight.
            val firstDay = Instant.ofEpochSecond(t - (wPx / 2 / pxPerSec).toLong()).atZone(zone).toLocalDate().minusDays(1)
            val lastDay = Instant.ofEpochSecond(t + (wPx / 2 / pxPerSec).toLong()).atZone(zone).toLocalDate().plusDays(1)
            var d = firstDay
            while (!d.isAfter(lastDay)) {
                val sun = Sun.day(d, lat, lon)
                val next = Sun.day(d.plusDays(1), lat, lon)
                val ss = sun.sunset; val sr = next.sunrise
                if (ss != null && sr != null) {
                    val a = x(ss); val b = x(sr)
                    drawRect(night, Offset(a, 0f), Size(b - a, axisY))
                }
                val mid = d.atStartOfDay(zone).toEpochSecond()
                val mx = x(mid)
                drawLine(line, Offset(mx, 0f), Offset(mx, axisY + 4.dp.toPx()), 1.dp.toPx())
                val label = tm.measure(DateTimeFormatter.ofPattern("EEE d").format(d), TextStyle(fontSize = 11.sp, color = dim, fontWeight = FontWeight.Bold))
                drawText(label, topLeft = Offset(mx + 4.dp.toPx(), axisY + 4.dp.toPx()))
                if (dpPerHour >= 14f) for (h in listOf(6, 12, 18)) {
                    val hx = x(mid + h * 3600L)
                    drawLine(line, Offset(hx, axisY - 4.dp.toPx()), Offset(hx, axisY), 1.dp.toPx())
                    val l2 = tm.measure(if (BuckPrefs.clock24) "%02d".format(h) else listOf("6a", "12p", "6p")[h / 6 - 1], TextStyle(fontSize = 10.sp, color = dim))
                    drawText(l2, topLeft = Offset(hx - l2.size.width / 2f, axisY + 4.dp.toPx()))
                }
                d = d.plusDays(1)
            }
            // Forecast (after now) tinted; out of range (before the cameras, past the forecast) grayed.
            drawRect(fcTint, Offset(x(now), 0f), Size(maxOf(0f, x(end) - x(now)), axisY))
            drawLine(fcLine, Offset(x(now), 0f), Offset(x(now), axisY), 1.5.dp.toPx())
            drawRect(line.copy(alpha = 0.5f), Offset(x(end), 0f), Size(maxOf(0f, size.width - x(end)), axisY))
            // His best sits ahead: bars along the axis.
            sits.forEach { (a, b) -> drawRect(sitColor, Offset(x(a), axisY - 6.dp.toPx()), Size(maxOf(2f, x(b) - x(a)), 6.dp.toPx())) }
            drawLine(line, Offset(0f, axisY), Offset(size.width, axisY), 1.dp.toPx())
            if (x(rangeStart) > 0) drawRect(line.copy(alpha = 0.5f), Offset(0f, 0f), Size(x(rangeStart), axisY))
            // Pin stems.
            events.forEach { e -> val ex = x(e.t); if (ex in -40f..size.width + 40f) drawLine(e.color, Offset(ex, 34.dp.toPx()), Offset(ex, axisY), 2.dp.toPx()) }
            // The playhead.
            drawLine(head, Offset(size.width / 2, 0f), Offset(size.width / 2, axisY), 2.5.dp.toPx())
            drawPath(Path().apply { moveTo(size.width / 2 - 6.dp.toPx(), 0f); lineTo(size.width / 2 + 6.dp.toPx(), 0f); lineTo(size.width / 2, 8.dp.toPx()); close() }, head)
        }
        // Pins: thumbnails, two lanes so close ones don't cover each other.
        var lastX = -1e9f; var lane = 0
        events.forEach { e ->
            val ex = x(e.t)
            if (ex < -60f || ex > wPx + 60f) return@forEach
            lane = if (ex - lastX < with(density) { 46.dp.toPx() }) 1 - lane else 0
            lastX = ex
            val w = 44.dp; val h = 26.dp
            androidx.compose.runtime.key(e.photoIds.first()) { Box(
                Modifier.offset { IntOffset((ex - with(density) { w.toPx() } / 2).roundToInt(), with(density) { (4.dp + if (lane == 1) 14.dp else 0.dp).toPx() }.roundToInt()) }
                    .width(w).height(h).clip(RoundedCornerShape(6.dp)).border(2.dp, e.color, RoundedCornerShape(6.dp)).clickable { onPick(e) },
            ) { PhotoThumb(e.photoIds.first(), Modifier.fillMaxSize()) } }
        }
    }
}

/** A buck as a round photo with his name under it; ringed in his color when shown. */
@Composable
private fun Avatar(name: String, mark: String, color: Color?, on: Boolean, buck: com.kairos.bucks.Buck? = null, onClick: () -> Unit) {
    Column(
        Modifier.width(62.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = color ?: Accent.fern
        val ring = if (on) c else Color.Transparent
        // His color, solid when shown and soft when not; a ring marks the selection.
        if (buck?.face != null) {
            Box(Modifier.size(44.dp).border(2.5.dp, ring, CircleShape).padding(4.dp)) { BuckFace(buck, 36.dp, dim = !on) }
        } else Box(
            Modifier.size(44.dp).border(2.5.dp, ring, CircleShape).padding(4.dp).clip(CircleShape).background(if (on) c else Accent.soft(c)),
            contentAlignment = Alignment.Center,
        ) {
            Text(mark, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = if (on) Accent.on(c) else KairosColors.Text)
        }
        Spacer(Modifier.height(3.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) KairosColors.Text else KairosColors.Dim, maxLines = 1)
    }
}

@Composable
private fun MiniChip(text: String, on: Boolean, dot: Color?, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (on) Accent.fern else KairosColors.Surface2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dot?.let { Box(Modifier.size(8.dp).background(it, CircleShape).border(1.dp, Color.White, CircleShape)); Spacer(Modifier.width(5.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) Accent.on(Accent.fern) else KairosColors.Text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(if (primary) 46.dp else 38.dp).clip(CircleShape).background(if (primary) Accent.fern else KairosColors.Surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (primary) Accent.on(Accent.fern) else KairosColors.Text) }
}

@Composable
private fun BuckMapView(onMapReady: (MapLibreMap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context).apply { onCreate(null); getMapAsync { onMapReady(it) } } }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer) // replays ON_START / ON_RESUME when already resumed
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onDestroy() }
    }
}
