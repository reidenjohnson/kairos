package com.kairos.ui.bucks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kairos.bucks.BuckStore
import com.kairos.data.LocationProvider
import com.kairos.engine.bucks.compass8
import com.kairos.ui.BaseMap
import com.kairos.ui.KairosColors
import com.kairos.ui.baseStyleUri
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rough trail-cam view cone drawn on the map: a guide for the eye, not the camera's spec. */
private const val CONE_DEG = 55.0
private const val CONE_M = 30.0

/**
 * Drop a camera's pin on satellite imagery and turn its view cone to where the lens points.
 * The pin stays in the middle; drag the map under it. Turn the dial to aim.
 * Other cameras show as small labeled dots so their spacing reads at a glance.
 */
@Composable
fun CameraPinScreen(cameraId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cam = BuckStore.db.camera(cameraId) ?: return
    remember { MapLibre.getInstance(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var facing by remember { mutableDoubleStateOf(cam.facingDeg ?: 0.0) }
    var moved by remember { mutableIntStateOf(0) } // bumps on camera move so the overlay redraws
    var locating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PinMapView { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isAttributionEnabled = true
            m.setStyle(Style.Builder().fromUri(baseStyleUri(context, BaseMap.AERIAL)))
            val others = BuckStore.db.cameras.filter { it.lat != null && it.lon != null }
            val start = when {
                cam.lat != null && cam.lon != null -> LatLng(cam.lat, cam.lon)
                others.isNotEmpty() -> LatLng(others.first().lat!!, others.first().lon!!)
                else -> LatLng(43.8500, -70.5800)
            }
            m.cameraPosition = CameraPosition.Builder().target(start).zoom(17.5).build()
            m.addOnCameraMoveListener { moved++ }
            if (cam.lat == null && others.isEmpty()) scope.launch {
                LocationProvider.current(context)?.let { m.moveCamera(CameraUpdateFactory.newLatLng(LatLng(it.lat, it.lon))) }
            }
        }

        // Overlay: other cameras, the view cone, and the pin. Drags on the cone aim it; everything
        // else falls through to the map.
        val tm = rememberTextMeasurer()
        val pine = KairosColors.Pine
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val tick = moved // redraw as the map moves
            val m = map ?: return@Canvas
            val c = Offset(size.width / 2, size.height / 2)
            val target = m.cameraPosition.target ?: return@Canvas
            val mpp = m.projection.getMetersPerPixelAtLatitude(target.latitude)
            val len = (CONE_M / mpp).toFloat().coerceIn(60.dp.toPx(), size.minDimension * 0.42f)
            val path = Path().apply {
                moveTo(c.x, c.y)
                val steps = 16
                for (k in 0..steps) {
                    val a = Math.toRadians(facing - CONE_DEG / 2 + CONE_DEG * k / steps - 90)
                    lineTo(c.x + len * cos(a).toFloat(), c.y + len * sin(a).toFloat())
                }
                close()
            }
            drawPath(path, pine.copy(alpha = 0.35f))
            drawPath(path, pine, style = Stroke(2.dp.toPx()))
            // Other cameras.
            BuckStore.db.cameras.filter { it.id != cam.id && it.lat != null && it.lon != null }.forEach { o ->
                val p = m.projection.toScreenLocation(LatLng(o.lat!!, o.lon!!))
                val pt = Offset(p.x, p.y)
                drawCircle(Color.White, 6.dp.toPx(), pt)
                drawCircle(Color.Black, 4.dp.toPx(), pt)
                drawText(tm, o.name, pt + Offset(9.dp.toPx(), -8.dp.toPx()), TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
            // The pin.
            drawCircle(Color.White, 11.dp.toPx(), c)
            drawCircle(pine, 8.dp.toPx(), c)
        }
        // Top label.
        Column(Modifier.align(Alignment.TopCenter).padding(top = 16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Drag the map to put the pin on ${cam.name}", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }

        // Bottom sheet: dial + actions.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(12.dp).clip(RoundedCornerShape(28.dp)).background(KairosColors.Surface).padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dial(facing) { facing = it }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lens points", style = MaterialTheme.typography.labelLarge, color = KairosColors.Dim)
                    Text("${compass8(facing)}  ${facing.roundToInt()}°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Turn the dial. The cone is a rough view, not the camera's exact reach.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(KairosColors.Surface2).clickable {
                        locating = true
                        scope.launch {
                            LocationProvider.current(context)?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 18.0)) }
                            locating = false
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.MyLocation, "I'm standing at it", tint = if (locating) KairosColors.Pine else KairosColors.Text) }
                PrimaryPill("Save ${cam.name}", accent = Accent.fern, onClick = {
                    val t = map?.cameraPosition?.target
                    if (t != null) BuckStore.saveCamera(cam.copy(lat = t.latitude, lon = t.longitude, facingDeg = (facing % 360 + 360) % 360))
                    onDone()
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Compass bearing (0 = up/north, since rotation is off) from [c] to [p]. */
private fun bearingTo(c: Offset, p: Offset): Double =
    (Math.toDegrees(atan2((p.x - c.x).toDouble(), (c.y - p.y).toDouble())) + 360) % 360

/** A round dial: drag anywhere on it to point the lens; N at top. */
@Composable
private fun Dial(value: Double, onChange: (Double) -> Unit) {
    val tm = rememberTextMeasurer()
    val pine = KairosColors.Pine
    val line = KairosColors.Line
    val dim = KairosColors.Dim
    val text = KairosColors.Text
    Canvas(
        Modifier.size(112.dp)
            .pointerInput(Unit) {
                detectDragGestures { ch, _ -> onChange(bearingTo(Offset(size.width / 2f, size.height / 2f), ch.position)) }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos -> onChange(bearingTo(Offset(size.width / 2f, size.height / 2f), pos)) }
            },
    ) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 4.dp.toPx()
        drawCircle(line, r, c, style = Stroke(2.dp.toPx()))
        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (n, a) ->
            val rad = Math.toRadians(a - 90)
            val ms = tm.measure(n, TextStyle(fontSize = 10.sp, color = dim, fontWeight = FontWeight.Bold))
            val rr = r - 10.dp.toPx()
            drawText(ms, topLeft = Offset(c.x + rr * cos(rad).toFloat() - ms.size.width / 2, c.y + rr * sin(rad).toFloat() - ms.size.height / 2))
        }
        val cone = Path().apply {
            moveTo(c.x, c.y)
            for (k in 0..12) {
                val a = Math.toRadians(value - CONE_DEG / 2 + CONE_DEG * k / 12 - 90)
                lineTo(c.x + (r - 20.dp.toPx()) * cos(a).toFloat(), c.y + (r - 20.dp.toPx()) * sin(a).toFloat())
            }
            close()
        }
        drawPath(cone, pine.copy(alpha = 0.45f))
        drawPath(cone, pine, style = Stroke(2.dp.toPx()))
        drawCircle(text, 4.dp.toPx(), c)
    }
}

@Composable
private fun PinMapView(onMapReady: (MapLibreMap) -> Unit) {
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
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onDestroy() }
    }
}
