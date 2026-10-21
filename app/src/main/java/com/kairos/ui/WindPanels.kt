package com.kairos.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.data.WindAxis
import com.kairos.data.WindMath
import com.kairos.data.WindPack
import com.kairos.data.WindSeries
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The Wind screen's bottom panels: the hour slider (always there) and the tapped-spot card
 * (chart first, numbers big, reference rows behind a tap — expands in place).
 */

private val HPA_TO_INHG = 0.02953

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val HOUR_FMT = DateTimeFormatter.ofPattern("EEE h a", Locale.US)
private val DAYTIME_FMT = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)

private fun zoned(sec: Long) = Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault())

/** "Tue 2:15 PM" for a millis timestamp — the offline chip's "data from". */
internal fun dayTimeText(millis: Long): String = DAYTIME_FMT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "Now" / "in 5 h" / "3 h ago" / "in 1 d 4 h" relative to the axis' now hour. */
private fun relativeText(index: Int): String {
    val d = index - WindAxis.NOW_INDEX
    if (d == 0) return "Now"
    val a = kotlin.math.abs(d)
    val span = if (a < 24) "$a h" else "${a / 24} d${if (a % 24 != 0) " ${a % 24} h" else ""}"
    return if (d > 0) "in $span" else "$span ago"
}

/** Ease the slider onto the nearest whole hour (after a drag / chart scrub). */
private suspend fun snapHour(from: Float, onHour: (Float) -> Unit) {
    val target = from.roundToInt().toFloat()
    animate(from, target, animationSpec = androidx.compose.animation.core.tween(160)) { v, _ -> onHour(v) }
}

/** Fractional axis hour under x on a track [width] px wide. */
private fun hourAt(x: Float, width: Float): Float =
    (x / width * (WindAxis.COUNT - 1)).coerceIn(0f, (WindAxis.COUNT - 1).toFloat())

@Composable
internal fun WindTimeSlider(axisStart: Long, hour: Float, onHour: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    val currentHour by rememberUpdatedState(hour)
    val idx = hour.roundToInt()

    // Play: glide through the hours (~3 h per second) — the field blends between hours, so it's smooth.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var h = if (currentHour >= WindAxis.COUNT - 1) 0f else currentHour
        var last = 0L
        while (playing) {
            withFrameNanos { t ->
                if (last != 0L) h += (t - last) / 1e9f * 3f
                last = t
            }
            if (h >= WindAxis.COUNT - 1) { onHour((WindAxis.COUNT - 1).toFloat()); playing = false } else onHour(h)
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = KairosColors.Surface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    // Pausing lands on a whole hour, like letting go of the slider.
                    if (playing) { playing = false; scope.launch { snapHour(currentHour, onHour) } } else playing = true
                }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play hours",
                        tint = KairosColors.Water,
                    )
                }
                IconButton(onClick = { playing = false; onHour((idx - 1).coerceAtLeast(0).toFloat()) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Hour earlier", tint = KairosColors.Dim)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        HOUR_FMT.format(zoned(axisStart + idx * 3600L)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = KairosColors.Text,
                    )
                    Text(relativeText(idx), style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
                }
                IconButton(onClick = { playing = false; onHour((idx + 1).coerceAtMost(WindAxis.COUNT - 1).toFloat()) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Hour later", tint = KairosColors.Dim)
                }
                TextButton(
                    onClick = { playing = false; onHour(WindAxis.NOW_INDEX.toFloat()) },
                    enabled = idx != WindAxis.NOW_INDEX,
                ) { Text("Now", fontWeight = FontWeight.Bold, color = if (idx != WindAxis.NOW_INDEX) KairosColors.Water else KairosColors.Faint) }
            }
            HourTrack(
                axisStart = axisStart,
                hour = hour,
                onScrub = { playing = false; onHour(it) },
                onRelease = { h -> scope.launch { snapHour(h, onHour) } },
            )
        }
    }
}

/** The scrubbable hour track: day labels + midnight ticks, the future tinted, a Now marker, a thumb. */
@Composable
private fun HourTrack(axisStart: Long, hour: Float, onScrub: (Float) -> Unit, onRelease: (Float) -> Unit) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = KairosColors.Faint, fontFamily = Hanken, fontWeight = FontWeight.SemiBold)
    val currentHour by rememberUpdatedState(hour)
    val line = KairosColors.Line
    val faint = KairosColors.Faint
    val water = KairosColors.Water
    val text = KairosColors.Text
    val surface = KairosColors.Surface
    // Midnights (device time zone) on the axis, with their day labels.
    val midnights = remember(axisStart) {
        (0 until WindAxis.COUNT).filter { zoned(axisStart + it * 3600L).hour == 0 }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 10.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> onRelease(hourAt(o.x, size.width.toFloat())) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onScrub(hourAt(o.x, size.width.toFloat())) },
                    onDragEnd = { onRelease(currentHour) },
                    onDragCancel = { onRelease(currentHour) },
                ) { change, _ ->
                    change.consume()
                    onScrub(hourAt(change.position.x, size.width.toFloat()))
                }
            },
    ) {
        val w = size.width
        val trackY = size.height - 12.dp.toPx()
        val xOf = { h: Float -> h / (WindAxis.COUNT - 1) * w }
        val nowX = xOf(WindAxis.NOW_INDEX.toFloat())
        // Track: past = hairline, future = a teal-tinted band (forecast).
        drawLine(line, Offset(0f, trackY), Offset(w, trackY), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(water.copy(alpha = 0.28f), Offset(nowX, trackY), Offset(w, trackY), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        // Midnight ticks + day labels centered on each day's span.
        midnights.forEachIndexed { k, m ->
            val x = xOf(m.toFloat())
            drawLine(faint, Offset(x, trackY - 7.dp.toPx()), Offset(x, trackY + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
            val end = midnights.getOrNull(k + 1) ?: (WindAxis.COUNT - 1)
            if (end - m >= 10) {
                val label = DAY_FMT.format(zoned(axisStart + m * 3600L))
                val layout = measurer.measure(label, labelStyle)
                val cx = xOf((m + end) / 2f)
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, 0f))
            }
        }
        // Now marker.
        drawLine(water, Offset(nowX, trackY - 10.dp.toPx()), Offset(nowX, trackY + 5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        // Thumb.
        val tx = xOf(hour)
        drawCircle(text.copy(alpha = 0.18f), radius = 11.dp.toPx(), center = Offset(tx, trackY))
        drawCircle(surface, radius = 8.dp.toPx(), center = Offset(tx, trackY))
        drawCircle(text, radius = 8.dp.toPx(), center = Offset(tx, trackY), style = Stroke(2.5.dp.toPx()))
    }
}

/**
 * The tapped point. Collapsed: the wind chart for the whole slider window + the selected hour,
 * big. Tap the header to expand in place for temp / cloud / rain / pressure and the sources.
 */
@Composable
internal fun WindSpotCard(
    lat: Double,
    lon: Double,
    spot: SpotWind?,
    packs: List<WindPack>,
    axisStart: Long,
    hour: Float,
    onScrub: (Float) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    val idx = hour.roundToInt().coerceIn(0, WindAxis.COUNT - 1)
    val tSec = axisStart + idx * 3600L
    val series = (spot as? SpotWind.Ready)?.series
    val k = series?.indexOf(tSec) ?: -1

    // Terrain pack at this exact point + hour, if one covers it (headline when it does).
    val pack = packs.firstOrNull { it.contains(lat, lon) && it.covers(tSec) }
    val packUv = remember(pack, lat, lon, tSec) {
        pack?.let { p -> FloatArray(2).takeIf { p.sampleAt(lat, lon, tSec, it) > 0f } }
    }
    // The pack's wind at this point for every axis hour, for the chart (null = no pack here).
    val terrain = remember(packs, lat, lon, axisStart) { terrainSeries(packs, lat, lon, axisStart) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = KairosColors.Surface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.animateContentSize().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 12.dp)) {
            // Header: tap to expand. Source chip reveals the attribution.
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Overline("This spot", color = KairosColors.Water)
                    Text(
                        "%.4f, %.4f".format(lat, lon) + if ((spot as? SpotWind.Ready)?.live == false) " · nearest saved point" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = KairosColors.Faint,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    tint = KairosColors.Dim,
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = KairosColors.Dim) }
            }

            when (spot) {
                null, SpotWind.Loading -> Text("Loading this spot's wind…", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, modifier = Modifier.padding(vertical = 24.dp))
                SpotWind.Failed -> Text(
                    "Couldn't load wind here, and nothing is saved nearby. It will load once you have signal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KairosColors.Dim,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, end = 10.dp),
                )
                is SpotWind.Ready -> {
                    Box(Modifier.padding(end = 10.dp)) {
                        WindSpotChart(
                            series = spot.series,
                            terrain = terrain,
                            axisStart = axisStart,
                            hour = hour,
                            onScrub = onScrub,
                            onRelease = { h -> scope.launch { snapHour(h, onScrub) } },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val hSpeed = series!!.speed.getOrNull(k)?.takeIf { k >= 0 && !it.isNaN() }
                    val hDir = series.dir.getOrNull(k)?.takeIf { k >= 0 && !it.isNaN() }
                    val hGust = series.gust.getOrNull(k)?.takeIf { k >= 0 && !it.isNaN() }
                    val headSpeed = packUv?.let { WindMath.speed(it[0].toDouble(), it[1].toDouble()).toFloat() } ?: hSpeed
                    val headDir = packUv?.let { WindMath.fromDeg(it[0].toDouble(), it[1].toDouble()).toFloat() } ?: hDir
                    WindReadout(speed = headSpeed, fromDeg = headDir, gust = hGust, gustFromHrrr = packUv != null)
                    if (packUv != null && hSpeed != null && hDir != null) {
                        Text(
                            "Regional model: ${hSpeed.roundToInt()} mph from ${WindMath.compass(hDir.toDouble())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = KairosColors.Dim,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Source label — tap to reveal where the numbers come from.
                    Text(
                        (if (packUv != null) "Terrain-adjusted (${pack!!.shortSource()}, ${pack.meshMeters.takeIf { !it.isNaN() }?.roundToInt() ?: "?"} m)" else "Regional model (HRRR, 3 km)") +
                            if (showSource) "  ▴" else "  ▾",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = KairosColors.Water,
                        modifier = Modifier.clickable { showSource = !showSource }.padding(vertical = 4.dp),
                    )
                    if (showSource) {
                        Text(
                            buildString {
                                append("Data: NOAA HRRR via Open-Meteo (free, non-commercial).")
                                if (!spot.live) append(" Offline: showing the nearest saved grid point, from ${dayTimeText(spot.series.fetchedAtMillis)}.")
                                if (pack != null) {
                                    append(" Terrain wind: pack \"${pack.name}\" (${pack.source}")
                                    if (!pack.meshMeters.isNaN()) append(", ${pack.meshMeters.roundToInt()} m mesh")
                                    if (pack.createdUtc > 0) append(", made ${dayTimeText(pack.createdUtc * 1000)}")
                                    append("). Gusts and temperature are HRRR; the chart shows both winds.")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = KairosColors.Faint,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(end = 10.dp, bottom = 4.dp),
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(4.dp))
                        WindRefRows(series, k)
                    }
                }
            }
        }
    }
}

/** The pack's model name for the short label: "WindNinja 4.0 (mass-conserving…)" -> "WindNinja".
 *  The full source string is in the tap-to-reveal note. */
private fun WindPack.shortSource(): String =
    source.split(' ', '(', ',').firstOrNull { it.isNotBlank() } ?: "WindNinja"

/** The selected hour, big: speed, an arrow showing which way the air goes, "from NNW", gust. */
@Composable
private fun WindReadout(speed: Float?, fromDeg: Float?, gust: Float?, gustFromHrrr: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            speed?.roundToInt()?.toString() ?: "–",
            fontFamily = Bricolage,
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.5).sp,
            lineHeight = 44.sp,
            color = KairosColors.Text,
        )
        Text(" mph", style = MaterialTheme.typography.titleMedium, color = KairosColors.Dim, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.width(18.dp))
        if (fromDeg != null) {
            val tint = KairosColors.Text
            Canvas(Modifier.size(34.dp)) { drawWindArrow(center, size.minDimension * 0.9f, fromDeg, tint, 2.5.dp.toPx()) }
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(
                fromDeg?.let { "from ${WindMath.compass(it.toDouble())}" } ?: "No data this hour",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KairosColors.Text,
            )
            Text(
                gust?.let { "gust ${it.roundToInt()} mph${if (gustFromHrrr) " (HRRR)" else ""}" } ?: (fromDeg?.let { "${it.roundToInt()}°" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = KairosColors.Dim,
            )
        }
    }
}

/** Reference rows (the expanded part): temp, cloud, rain, pressure + its 3-hour trend. */
@Composable
private fun WindRefRows(s: WindSeries, k: Int) {
    fun at(a: FloatArray, i: Int) = a.getOrNull(i)?.takeIf { i >= 0 && !it.isNaN() }
    val p = at(s.pressure, k)
    val p3 = at(s.pressure, k - 3)
    val rows = listOf(
        "Temperature" to (at(s.temp, k)?.let { "${it.roundToInt()}°F" } ?: "–"),
        "Cloud cover" to (at(s.cloud, k)?.let { "${it.roundToInt()}%" } ?: "–"),
        "Precipitation" to (at(s.precip, k)?.let { if (it < 0.005f) "None" else "%.2f in".format(it) } ?: "–"),
        "Pressure" to (p?.let {
            val inHg = it * HPA_TO_INHG
            val trend = p3?.let { b -> (it - b) * HPA_TO_INHG }
            "%.2f inHg".format(inHg) + (trend?.let { t -> "  ${trendArrow(t.toDouble())} %+.2f / 3 h".format(t) } ?: "")
        } ?: "–"),
        "Direction" to (at(s.dir, k)?.let { "${it.roundToInt()}° (from ${WindMath.compass(it.toDouble())})" } ?: "–"),
    )
    Column(Modifier.padding(end = 10.dp)) {
        rows.forEach { (key, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(key, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = KairosColors.Text)
            }
        }
    }
}

/**
 * Wind speed (solid) + gusts (dashed, with a faint band between) across the whole slider window,
 * downwind arrows every 12 h along the top, Now as a dashed line and the selected hour as a solid
 * one. One hue (brand Water) for both series — they're the same quantity; dash + a legend tell them
 * apart. Drag across it to scrub the slider.
 */
@Composable
private fun WindSpotChart(
    series: WindSeries,
    /** Terrain (pack) speed + FROM direction per axis hour, or null. When present it is THE wind
     *  line and HRRR's wind drops to a thin recessive line for comparison. */
    terrain: Pair<FloatArray, FloatArray>?,
    axisStart: Long,
    hour: Float,
    onScrub: (Float) -> Unit,
    onRelease: (Float) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val currentHour by rememberUpdatedState(hour)
    val water = KairosColors.Water // brand teal, 4.8:1 light / 5.7:1 dark on Surface
    val grid = ChartColors.Grid
    val label = ChartColors.Label
    val nowColor = ChartColors.Today
    val text = KairosColors.Text
    val labelStyle = TextStyle(fontSize = 10.sp, color = label, fontFamily = Hanken)

    // Align the point's series to the axis once.
    val data = remember(series, axisStart) {
        val sp = FloatArray(WindAxis.COUNT) { Float.NaN }
        val gu = FloatArray(WindAxis.COUNT) { Float.NaN }
        val di = FloatArray(WindAxis.COUNT) { Float.NaN }
        for (h in 0 until WindAxis.COUNT) {
            val i = series.indexOf(axisStart + h * 3600L)
            if (i < 0) continue
            sp[h] = series.speed[i]; gu[h] = series.gust[i]; di[h] = series.dir[i]
        }
        Triple(sp, gu, di)
    }
    val (hsp, gu, hdi) = data
    // Main wind line + arrows: terrain where the pack has the hour, else HRRR.
    val sp = remember(data, terrain) { terrain?.first?.let { t -> FloatArray(hsp.size) { if (t[it].isNaN()) hsp[it] else t[it] } } ?: hsp }
    val di = remember(data, terrain) { terrain?.second?.let { t -> FloatArray(hdi.size) { if (t[it].isNaN()) hdi[it] else t[it] } } ?: hdi }
    val maxV = remember(data, terrain) {
        val m = (sp.filter { !it.isNaN() } + gu.filter { !it.isNaN() } + hsp.filter { !it.isNaN() }).maxOrNull() ?: 10f
        (ceil(maxOf(m, 10f) / 5f) * 5f)
    }

    Column {
        // Legend (two series -> always named).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
                drawLine(water, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            Text(if (terrain != null) " terrain wind   " else " wind   ", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
            Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
                drawLine(water, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
            }
            Text(" gust   ", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
            if (terrain != null) {
                Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
                    drawLine(label, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                }
                Text(" regional   ", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
            }
            Text("mph", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o -> onRelease(hourAt(o.x, size.width.toFloat())) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> onScrub(hourAt(o.x, size.width.toFloat())) },
                        onDragEnd = { onRelease(currentHour) },
                        onDragCancel = { onRelease(currentHour) },
                    ) { change, _ ->
                        change.consume()
                        onScrub(hourAt(change.position.x, size.width.toFloat()))
                    }
                },
        ) {
            val w = size.width
            val arrowBand = 18.dp.toPx()
            val top = arrowBand + 4.dp.toPx()
            val bottom = size.height - 2.dp.toPx()
            val xOf = { h: Float -> h / (WindAxis.COUNT - 1) * w }
            val yOf = { v: Float -> bottom - (v / maxV) * (bottom - top) }

            // Recessive grid every 10 mph, labeled at the left edge.
            var gv = 10f
            while (gv < maxV) {
                val y = yOf(gv)
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                val l = measurer.measure(gv.roundToInt().toString(), labelStyle)
                drawText(l, topLeft = Offset(2.dp.toPx(), y - l.size.height - 1.dp.toPx()))
                gv += 10f
            }
            drawLine(grid, Offset(0f, bottom), Offset(w, bottom), strokeWidth = 1.dp.toPx())

            // Gust band + lines, drawn as runs so data gaps (no HRRR) stay gaps.
            forEachRun(sp, gu) { a, b ->
                val band = Path().apply {
                    moveTo(xOf(a.toFloat()), yOf(gu[a]))
                    for (h in a + 1..b) lineTo(xOf(h.toFloat()), yOf(gu[h]))
                    for (h in b downTo a) lineTo(xOf(h.toFloat()), yOf(sp[h]))
                    close()
                }
                drawPath(band, water.copy(alpha = 0.14f))
            }
            drawSeries(gu, xOf, yOf, water, 1.5.dp.toPx(), PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            if (terrain != null) drawSeries(hsp, xOf, yOf, label, 1.dp.toPx(), null)
            drawSeries(sp, xOf, yOf, water, 2.dp.toPx(), null)

            // Downwind arrows every 12 h (at the hour's direction), in the top band.
            for (h in 6 until WindAxis.COUNT step 12) {
                val d = di[h]
                if (d.isNaN()) continue
                drawWindArrow(Offset(xOf(h.toFloat()), arrowBand / 2), 11.dp.toPx(), d, label, 1.5.dp.toPx())
            }

            // Now (dashed, recessive) and the selected hour (solid, with a dot on the wind line).
            val nowX = xOf(WindAxis.NOW_INDEX.toFloat())
            drawLine(nowColor, Offset(nowX, top), Offset(nowX, bottom), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            val selX = xOf(hour)
            drawLine(text, Offset(selX, arrowBand), Offset(selX, bottom), strokeWidth = 1.5.dp.toPx())
            val hi = hour.roundToInt().coerceIn(0, WindAxis.COUNT - 1)
            if (!sp[hi].isNaN()) {
                val c = Offset(selX, yOf(sp[hi]))
                drawCircle(KairosColors.Surface, radius = 5.5.dp.toPx(), center = c) // 2px surface ring
                drawCircle(water, radius = 4.dp.toPx(), center = c)
            }
        }
    }
}

/** Speed + FROM direction of the first pack covering (lat, lon), per axis hour (NaN where the
 *  pack has no such hour); null when no pack covers the point. */
private fun terrainSeries(packs: List<WindPack>, lat: Double, lon: Double, axisStart: Long): Pair<FloatArray, FloatArray>? {
    val p = packs.firstOrNull { it.contains(lat, lon) } ?: return null
    val sp = FloatArray(WindAxis.COUNT) { Float.NaN }
    val di = FloatArray(WindAxis.COUNT) { Float.NaN }
    val uv = FloatArray(2)
    for (h in 0 until WindAxis.COUNT) {
        val t = axisStart + h * 3600L
        if (!p.covers(t) || p.sampleAt(lat, lon, t, uv) <= 0f) continue
        sp[h] = WindMath.speed(uv[0].toDouble(), uv[1].toDouble()).toFloat()
        di[h] = WindMath.fromDeg(uv[0].toDouble(), uv[1].toDouble()).toFloat()
    }
    return if (sp.any { !it.isNaN() }) sp to di else null
}

/** Calls [block] for each run [a..b] of hours where both series have data. */
private inline fun forEachRun(a1: FloatArray, a2: FloatArray, block: (Int, Int) -> Unit) {
    var h = 0
    while (h < a1.size) {
        if (a1[h].isNaN() || a2[h].isNaN()) { h++; continue }
        val start = h
        while (h + 1 < a1.size && !a1[h + 1].isNaN() && !a2[h + 1].isNaN()) h++
        if (h > start) block(start, h)
        h++
    }
}

private fun DrawScope.drawSeries(
    v: FloatArray,
    xOf: (Float) -> Float,
    yOf: (Float) -> Float,
    color: Color,
    width: Float,
    effect: PathEffect?,
) {
    val path = Path()
    var pen = false
    for (h in v.indices) {
        if (v[h].isNaN()) { pen = false; continue }
        val x = xOf(h.toFloat()); val y = yOf(v[h])
        if (pen) path.lineTo(x, y) else path.moveTo(x, y)
        pen = true
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round, pathEffect = effect))
}

/**
 * An arrow pointing DOWNWIND (the way the air moves), for a FROM direction — so a north wind
 * (from 0°) points down the screen. Matches the particles.
 */
internal fun DrawScope.drawWindArrow(c: Offset, length: Float, fromDeg: Float, color: Color, width: Float) {
    rotate(degrees = fromDeg + 180f, pivot = c) {
        // Drawn pointing up (north) before rotation.
        val half = length / 2
        val tip = Offset(c.x, c.y - half)
        drawLine(color, Offset(c.x, c.y + half), tip, strokeWidth = width, cap = StrokeCap.Round)
        val head = length * 0.32f
        drawLine(color, tip, Offset(tip.x - head * 0.75f, tip.y + head), strokeWidth = width, cap = StrokeCap.Round)
        drawLine(color, tip, Offset(tip.x + head * 0.75f, tip.y + head), strokeWidth = width, cap = StrokeCap.Round)
    }
}
