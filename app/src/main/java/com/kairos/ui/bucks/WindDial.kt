package com.kairos.ui.bucks

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.bucks.BuckAnalysis
import com.kairos.bucks.BuckStore
import com.kairos.engine.bucks.StandWind
import com.kairos.ui.KairosColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Compass degrees (0 = N, clockwise) to a screen point [r] from [c]. */
private fun at(c: Offset, deg: Double, r: Float): Offset {
    val a = Math.toRadians(deg)
    return Offset(c.x + sin(a).toFloat() * r, c.y - cos(a).toFloat() * r)
}

/** A wedge from [c] toward compass [deg], [half] degrees each side. */
private fun wedge(c: Offset, deg: Double, half: Double, r: Float) = Path().apply {
    moveTo(c.x, c.y)
    val steps = 12
    for (i in 0..steps) { val p = at(c, deg - half + 2 * half * i / steps, r); lineTo(p.x, p.y) }
    close()
}

/** The camera's view: a faint wedge the way it faces (±25°, about a trail cam's field of view). */
private fun DrawScope.cameraView(c: Offset, facing: Double?, r: Float) {
    if (facing == null) return
    drawPath(wedge(c, facing, 25.0, r), Brush.radialGradient(listOf(KairosColors.Text.copy(alpha = 0.20f), KairosColors.Text.copy(alpha = 0.04f)), c, r))
    drawCircle(KairosColors.Text, 4.dp.toPx(), c)
}

/** An arrow coming in from compass [from] toward the middle. */
private fun DrawScope.windIn(c: Offset, from: Double, outer: Float, inner: Float, color: Color, dashed: Boolean = false) {
    val tail = at(c, from, outer); val tip = at(c, from, inner)
    val w = 2.6.dp.toPx()
    drawLine(color, tail, tip, w, StrokeCap.Round, if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)
    // Head at the inner end, pointing to the middle.
    val ux = (tip.x - tail.x) / hypot(tip.x - tail.x, tip.y - tail.y); val uy = (tip.y - tail.y) / hypot(tip.x - tail.x, tip.y - tail.y)
    val hl = 11.dp.toPx(); val hw = 6.5.dp.toPx()
    val back = Offset(tip.x - ux * hl, tip.y - uy * hl)
    drawPath(Path().apply { moveTo(tip.x + ux * 2, tip.y + uy * 2); lineTo(back.x - uy * hw, back.y + ux * hw); lineTo(back.x + uy * hw, back.y - ux * hw); close() }, color)
}

/**
 * One sit's wind at a stand, drawn: the compass ring with his wind marked in Fern, the wind coming
 * in (white; ECMWF's reading dashed Amber when the models split), his scent drifting out the
 * other way (Fern on his wind, Brick off it, Teal when nothing's set or it's calm), and the
 * camera's view. Direction only: the shading's length is not a distance.
 */
@Composable
internal fun StandWindDial(
    facing: Double?, sectors: Set<Int>, dir: Double?, mph: Double?, verdict: Boolean?,
    altDir: Double? = null, altMph: Double? = null, split: Boolean = false, size: Dp = 230.dp, letters: Boolean = true,
) {
    val tm = rememberTextMeasurer()
    val ring = KairosColors.Line; val text = KairosColors.Dim; val fern = Accent.fern
    val scent = scentColor(verdict)
    val calm = mph == null || dir == null || mph < com.kairos.engine.bucks.HisWinds.CALM_MPH
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2 - (if (letters) 22.dp else 3.dp).toPx() // room for the letters
        // Ring, with his wind as Fern arcs.
        drawCircle(ring, r, c, style = Stroke(if (letters) 2.dp.toPx() else 1.dp.toPx()))
        val arcW = if (letters) 9.dp.toPx() else 4.dp.toPx()
        sectors.forEach { s ->
            drawArc(fern, (s * StandWind.SECTOR_DEG - StandWind.SECTOR_DEG / 2 - 90).toFloat() + 0.8f, StandWind.SECTOR_DEG.toFloat() - 1.6f, false,
                Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(arcW))
        }
        if (letters) listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (l, d) ->
            val m = tm.measure(l, TextStyle(color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            val p = at(c, d, r + 13.dp.toPx())
            drawText(m, topLeft = Offset(p.x - m.size.width / 2, p.y - m.size.height / 2))
        }
        cameraView(c, facing, if (letters) r * 0.42f else r * 0.9f)
        if (dir == null && mph == null) {
            // No wind to show (the settings row): just his pick and the camera.
        } else if (calm) {
            // No direction: a soft ring around the stand, nothing pointed.
            drawCircle(Brush.radialGradient(listOf(scent.copy(alpha = 0.35f), Color.Transparent), c, r * 0.5f), r * 0.5f, c)
        } else {
            drawPath(wedge(c, dir!! + 180, 22.5, r * 0.92f), Brush.radialGradient(listOf(scent.copy(alpha = 0.60f), scent.copy(alpha = 0.05f)), c, r * 0.92f))
            if (split && altDir != null && altMph != null && altMph >= com.kairos.engine.bucks.HisWinds.CALM_MPH) windIn(c, altDir, r * 0.98f, r * 0.30f, Accent.amber, dashed = true)
            windIn(c, dir, r * 0.98f, r * 0.30f, KairosColors.Text)
        }
    }
}

/** The scent's color: Fern on his wind, Brick off it, Teal (just information) otherwise. */
internal fun scentColor(verdict: Boolean?): Color = when (verdict) { true -> Accent.fern; false -> Accent.brick; null -> Accent.teal }

/** The dial's key, drawn the same way as the dial; only what's on it. */
@Composable
internal fun DialKey(hasPick: Boolean, hasWind: Boolean, verdict: Boolean?, hasFacing: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hasPick) KeyItem(Accent.fern, "your wind", bar = true)
        if (hasWind) KeyItem(KairosColors.Text, "wind")
        KeyItem(scentColor(verdict).copy(alpha = 0.7f), "your scent")
        if (hasFacing) KeyItem(KairosColors.Text.copy(alpha = 0.25f), "camera view")
    }
}

@Composable
private fun KeyItem(c: Color, label: String, bar: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (bar) 14.dp else 10.dp, if (bar) 5.dp else 10.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Spacer(Modifier.width(5.dp))
        Text(label, color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Tap the 16 compass points to pick his wind at a stand. Picked = Fern. The camera's view is in
 * the middle so he can see which way it looks.
 */
@Composable
private fun WindPicker(sectors: Set<Int>, facing: Double?, overMap: Boolean = false, onToggle: (Int) -> Unit) {
    val tm = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    // Over the map: see-through ring, white labels, so the ground shows through.
    val off = if (overMap) Color.Black.copy(alpha = 0.38f) else KairosColors.Surface2
    val on = if (overMap) Accent.fern.copy(alpha = 0.85f) else Accent.fern
    val text = if (overMap) Color.White else KairosColors.Text; val dim = if (overMap) Color.White.copy(alpha = 0.8f) else KairosColors.Dim
    // Keyed on the picks, so a tap always toggles against what's shown now.
    val toggle = androidx.compose.runtime.rememberUpdatedState(onToggle)
    Canvas(
        Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f).pointerInput(Unit) {
            detectTapGestures { p ->
                val c = Offset(size.width / 2f, size.height / 2f)
                val d = hypot(p.x - c.x, p.y - c.y)
                val r = size.width / 2f - 30.dp.toPx()
                // The ring and the letters around it both count as the point.
                if (d < r * 0.45f || d > r + 30.dp.toPx()) return@detectTapGestures
                val deg = (Math.toDegrees(atan2((p.x - c.x).toDouble(), (c.y - p.y).toDouble())) + 360) % 360
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                toggle.value(StandWind.sectorOf(deg))
            }
        },
    ) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 30.dp.toPx()
        val band = r * (if (overMap) 0.30f else 0.42f) // thinner over the map: more ground shows
        val mid = r - band / 2
        for (s in 0 until StandWind.SECTORS) {
            val picked = s in sectors
            drawArc(if (picked) on else off, (s * StandWind.SECTOR_DEG - StandWind.SECTOR_DEG / 2 - 90).toFloat() + 1f, StandWind.SECTOR_DEG.toFloat() - 2f, false,
                Offset(c.x - mid, c.y - mid), Size(2 * mid, 2 * mid), style = Stroke(band))
            val name = StandWind.NAMES[s]
            val major = s % 2 == 0
            val m = tm.measure(name, TextStyle(color = if (picked) Accent.on(on) else if (major) text else dim, fontSize = if (major) 14.sp else 11.sp, fontWeight = if (major) FontWeight.Bold else FontWeight.SemiBold))
            val p = at(c, s * StandWind.SECTOR_DEG, mid)
            drawText(m, topLeft = Offset(p.x - m.size.width / 2, p.y - m.size.height / 2))
        }
        cameraView(c, facing, r - band - 6.dp.toPx())
        if (overMap) { drawCircle(Color.Black.copy(alpha = 0.5f), 7.dp.toPx(), c); drawCircle(Color.White, 5.dp.toPx(), c) }
    }
}

/**
 * The satellite view around a camera (the same aerial base as the Bucks map), still: no
 * gestures, so taps go to the wind ring on top. Texture mode, so it draws inside a pop-up.
 */
@Composable
private fun StandMap(lat: Double, lon: Double, onMap: (org.maplibre.android.maps.MapLibreMap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val mapView = androidx.compose.runtime.remember {
        // The map engine starts once per app; this pop-up can open before any other map has.
        org.maplibre.android.MapLibre.getInstance(context.applicationContext)
        org.maplibre.android.maps.MapView(context, org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.setAllGesturesEnabled(false)
                m.uiSettings.isCompassEnabled = false
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isAttributionEnabled = false
                m.setStyle(org.maplibre.android.maps.Style.Builder().fromUri(com.kairos.ui.baseStyleUri(context, com.kairos.ui.BaseMap.AERIAL)))
                // About 600 m across at this zoom: the woods, the field, the roads; +/- from there.
                m.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder().target(org.maplibre.android.geometry.LatLng(lat, lon)).zoom(15.3).build()
                onMap(m)
            }
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onStop(); mapView.onDestroy() }
    }
}

/**
 * "Your wind" at one camera: pick the winds he hunts it on. Saves on every tap; Done confirms.
 * The suggestion comes from where his deer came (labeled as a suggestion; he decides).
 */
@Composable
internal fun YourWindSheet(camId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cam = BuckStore.db.camera(camId) ?: run { onClose(); return }
    val sectors = cam.windSectors
    fun save(s: Set<Int>) = BuckStore.saveCamera(cam.copy(windSectors = s))
    val done = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.makeText(context, if (sectors.isEmpty()) "Saved: no wind set for ${cam.name}" else "Saved: ${StandWind.label(sectors)} at ${cam.name}", Toast.LENGTH_SHORT).show()
        onClose()
    }
    Takeover(done, title = "Your wind at ${cam.name}") {
        Text(
            "Tap the winds you'd hunt this stand on: the ones that carry your scent away from where deer come from.",
            color = KairosColors.Dim, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        // On the satellite view around the camera, so the trail and field are right there.
        Box(Modifier.fillMaxWidth().widthIn(max = 340.dp).aspectRatio(1f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            var map by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
            if (cam.lat != null && cam.lon != null) StandMap(cam.lat, cam.lon) { map = it }
            WindPicker(sectors, null, overMap = cam.lat != null) { s -> save(if (s in sectors) sectors - s else sectors + s) }
            // Zoom: buttons, so pinching never fights tapping the ring.
            if (map != null) Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Icons.Filled.Add to 1.0, Icons.Filled.Remove to -1.0).forEach { (icon, by) ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                            .clickable { map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomBy(by), 250) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, if (by > 0) "Zoom in" else "Zoom out", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
        Text(
            if (sectors.isEmpty()) "None picked" else StandWind.label(sectors),
            color = if (sectors.isEmpty()) KairosColors.Dim else Accent.fern, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        // The suggestion: where deer came from at this camera.
        BuckAnalysis.state?.deerVisits?.get(camId)?.let { StandWind.suggest(it) }?.let { sg ->
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Accent.soft(Accent.teal)).padding(14.dp)) {
                Text("SUGGESTION FROM YOUR PHOTOS", color = Accent.teal, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Deer came here most on ${StandWind.label(sg.sectors)}: ${sg.hits} of ${sg.of} ${if (sg.daylightOnly) "daylight " else ""}visits with a wind" +
                        (if (sg.calm > 0) " (${sg.calm} more in calm air)." else ".") +
                        " Check that this wind carries your scent away from them before you use it.",
                    color = KairosColors.Text, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                if (sg.sectors == sectors) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = Accent.teal, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("In use", color = Accent.teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                } else Box(
                    Modifier.clip(CircleShape).background(Accent.brush(Accent.teal)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); save(sg.sectors) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("Use ${StandWind.label(sg.sectors)}", color = Accent.on(Accent.teal), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "A sit on your wind counts as a check met; a wind off it (3 mph and up) counts as missed. Calm hours don't count either way.",
            color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Hold, so a stray tap can't wipe his pick.
            if (sectors.isNotEmpty()) Box(
                Modifier.weight(1f).height(56.dp).clip(CircleShape).background(KairosColors.Surface2).tapHold(onTap = {
                    Toast.makeText(context, "Hold to clear", Toast.LENGTH_SHORT).show()
                }, onHold = { save(emptySet()); Toast.makeText(context, "Cleared", Toast.LENGTH_SHORT).show() }),
                contentAlignment = Alignment.Center,
            ) { Text("Hold to clear", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            PrimaryPill("Done", done, Modifier.weight(1f))
        }
    }
}

/**
 * The wind on a small card, against the camera: a faint wedge the way the camera faces and the
 * wind arrow over it (circle where it comes from, point where it blows). One glyph, so the wind
 * reads in the camera's own terms without a second arrow.
 */
@Composable
internal fun MiniWindDial(dir: Double?, facing: Double?, color: Color = KairosColors.Text, size: Dp = 34.dp) {
    val ring = KairosColors.Line
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2 - 1.dp.toPx()
            drawCircle(ring, r, c, style = Stroke(1.dp.toPx()))
            if (facing != null) drawPath(wedge(c, facing, 25.0, r), KairosColors.Text.copy(alpha = 0.16f))
        }
        if (dir != null) WindArrow(dir, Modifier.size(size * 0.72f), color)
    }
}
