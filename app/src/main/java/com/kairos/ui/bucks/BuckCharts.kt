package com.kairos.ui.bucks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.bucks.BuckAnalysis
import com.kairos.engine.bucks.BuckReport
import com.kairos.engine.bucks.FactorStat
import com.kairos.engine.bucks.Sun
import com.kairos.engine.bucks.Verdict
import com.kairos.engine.bucks.Visit
import com.kairos.ui.KairosColors
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Charts for a buck page. Every chart compares HIM (his color) to the camera's hours (neutral
 * gray) where a comparison exists, carries its own title, and has the numbers one tap away.
 */

/** A clean section: big title, quiet subtitle, content on a soft surface. */
@Composable
internal fun ChartCard(title: String, sub: String? = null, accent: Color = Accent.teal, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KairosColors.Surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KairosColors.Text)
        }
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim) }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/**
 * A section that opens on tap: title, a one-line takeaway, and the full detail inside. Same look
 * as [ChartCard], so the page reads as one list of plain sentences until you want the numbers.
 */
@Composable
internal fun Disclosure(title: String, summary: String, accent: Color = Accent.teal, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember(title) { mutableStateOf(startOpen) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KairosColors.Surface)
            .clickable { open = !open }.padding(18.dp).animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
            }
            Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
        }
        if (open) {
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** Each visit on a 24-hour line, one row per day he showed; shaded = dark (outside shooting light). */
@Composable
internal fun WhenChart(r: BuckReport, lat: Double?, lon: Double?, zone: ZoneId, color: Color, onTap: (Visit) -> Unit) {
    val days = r.visits.map { Instant.ofEpochSecond(it.start).atZone(zone).toLocalDate() }.distinct().sorted()
    if (days.isEmpty()) return
    val tm = rememberTextMeasurer()
    val rowH = 22.dp
    val dark = KairosColors.Surface2
    val grid = KairosColors.Line
    val label = KairosColors.Dim
    val h = rowH * days.size + 22.dp
    Canvas(
        Modifier.fillMaxWidth().height(h).pointerInput(r.visits) {
            detectTapGestures { pos ->
                val left = 52.dp.toPx(); val w = size.width - left
                val hit = r.visits.minByOrNull { v ->
                    val z = Instant.ofEpochSecond(v.start).atZone(zone)
                    val row = days.indexOf(z.toLocalDate())
                    val x = left + w * (z.toLocalTime().toSecondOfDay() / 86400f)
                    val y = row * rowH.toPx() + rowH.toPx() / 2
                    (x - pos.x) * (x - pos.x) + (y - pos.y) * (y - pos.y)
                }
                if (hit != null) onTap(hit)
            }
        },
    ) {
        val left = 52.dp.toPx()
        val w = size.width - left
        val rh = rowH.toPx()
        days.forEachIndexed { i, d ->
            val y = i * rh
            // Dark = before sunrise-30 and after sunset+30 (outside Maine legal light).
            if (lat != null && lon != null) {
                val s = Sun.day(d, lat, lon)
                val sr = s.sunrise?.let { Instant.ofEpochSecond(it - 1800).atZone(zone).toLocalTime().toSecondOfDay() / 86400f } ?: 0.25f
                val ss = s.sunset?.let { Instant.ofEpochSecond(it + 1800).atZone(zone).toLocalTime().toSecondOfDay() / 86400f } ?: 0.75f
                drawRect(dark, Offset(left, y + 2), Size(w * sr, rh - 4))
                drawRect(dark, Offset(left + w * ss, y + 2), Size(w * (1 - ss), rh - 4))
            }
            drawText(tm, "${d.monthValue}/${d.dayOfMonth}", Offset(0f, y + 3), TextStyle(fontSize = 11.sp, color = label))
        }
        for (hr in listOf(0, 6, 12, 18, 24)) {
            val x = left + w * hr / 24f
            drawLine(grid, Offset(x, 0f), Offset(x, days.size * rh), strokeWidth = 1f)
            val t = when (hr) { 0, 24 -> "12a"; 12 -> "12p"; else -> if (hr < 12) "${hr}a" else "${hr - 12}p" }
            val ms = tm.measure(t, TextStyle(fontSize = 10.sp, color = label))
            drawText(ms, topLeft = Offset((x - ms.size.width / 2f).coerceIn(left, size.width - ms.size.width), days.size * rh + 4))
        }
        for (v in r.visits) {
            val z = Instant.ofEpochSecond(v.start).atZone(zone)
            val row = days.indexOf(z.toLocalDate())
            val x = left + w * (z.toLocalTime().toSecondOfDay() / 86400f)
            val y = row * rh + rh / 2
            drawCircle(Color.White.copy(alpha = if (KairosColors.dark) 0.15f else 0.9f), 7.dp.toPx(), Offset(x, y))
            drawCircle(color, 5.dp.toPx(), Offset(x, y))
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(dark))
        Text("  dark (outside shooting light)   ", style = MaterialTheme.typography.labelSmall, color = label)
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text("  a visit (tap for that moment)", style = MaterialTheme.typography.labelSmall, color = label)
    }
}


/** 8-point rose: his visits per wind direction (colored wedges) over how often each blew (gray outline). */
@Composable
internal fun WindRose(fs: FactorStat?, color: Color) {
    if (fs == null) { Text("No wind data for his visits yet.", color = KairosColors.Dim); return }
    val tm = rememberTextMeasurer()
    val label = KairosColors.Dim
    val base = baselineColor
    val dirs = fs.bins.take(8)
    val calm = fs.bins.getOrNull(8)
    val maxShare = max(dirs.maxOf { it.share }, dirs.maxOf { if (it.baseShare.isNaN()) 0.0 else it.baseShare }).coerceAtLeast(0.05)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(190.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val rMax = size.minDimension / 2 - 16.dp.toPx()
            for (k in 1..3) drawCircle(KairosColors.Line, rMax * k / 3, c, style = Stroke(1f))
            dirs.forEachIndexed { i, b ->
                val a = Math.toRadians(i * 45.0 - 90)
                val r = (b.share / maxShare * rMax).toFloat()
                val path = Path().apply {
                    moveTo(c.x, c.y)
                    val a0 = a - Math.toRadians(17.0); val a1 = a + Math.toRadians(17.0)
                    lineTo(c.x + r * cos(a0).toFloat(), c.y + r * sin(a0).toFloat())
                    lineTo(c.x + r * cos(a1).toFloat(), c.y + r * sin(a1).toFloat())
                    close()
                }
                drawPath(path, color)
                val lr = rMax + 9.dp.toPx()
                val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                val m = tm.measure(names[i], TextStyle(fontSize = 10.sp, color = label))
                drawText(m, topLeft = Offset(c.x + lr * cos(a).toFloat() - m.size.width / 2, c.y + lr * sin(a).toFloat() - m.size.height / 2))
            }
            val outline = Path()
            dirs.forEachIndexed { i, b ->
                val a = Math.toRadians(i * 45.0 - 90)
                val r = ((if (b.baseShare.isNaN()) 0.0 else b.baseShare) / maxShare * rMax).toFloat()
                val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                if (i == 0) outline.moveTo(p.x, p.y) else outline.lineTo(p.x, p.y)
            }
            outline.close()
            drawPath(outline, base, style = Stroke(2.dp.toPx()))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            LegendRow(color, "His visits")
            LegendRow(base, "How often it blew there")
            Spacer(Modifier.height(6.dp))
            Text("${fs.n} visits with wind known", style = MaterialTheme.typography.labelSmall, color = label)
            calm?.let { Text("Calm: ${it.visits} (${pct(it.share)} vs ${pct(it.baseShare)})", style = MaterialTheme.typography.labelSmall, color = label) }
            dirs.filter { it.verdict != Verdict.NONE }.forEach {
                Text("${it.label}: ${it.verdict.label}", style = MaterialTheme.typography.labelMedium, color = KairosColors.Text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun LegendRow(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) { drawRoundRect(c, cornerRadius = CornerRadius(3f, 3f)) }
        Text("  $text", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
    }
}

internal fun pct(x: Double) = if (x.isNaN()) "?" else "${(x * 100).roundToInt()}%"

/** One condition: per bin, his share (color) over the camera's share (gray); tap for the numbers. */
@Composable
internal fun FactorBars(fs: FactorStat, color: Color) {
    var open by remember(fs.key) { mutableStateOf(false) }
    val marked = fs.bins.filter { it.verdict != Verdict.NONE }
    Column(Modifier.fillMaxWidth().animateContentSize().clickable { open = !open }.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fs.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            marked.firstOrNull()?.let { Pill("${it.verdict.label}: ${it.label}", verdictColor(it.verdict)) }
        }
        Spacer(Modifier.height(4.dp))
        val maxShare = fs.bins.maxOf { max(it.share, if (it.baseShare.isNaN()) 0.0 else it.baseShare) }.coerceAtLeast(0.01)
        fs.bins.forEach { b ->
            if (b.visits == 0 && (b.baseShare.isNaN() || b.baseShare < 0.02) && !open) return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(b.label, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, modifier = Modifier.width(128.dp), maxLines = 2)
                Column(Modifier.weight(1f)) {
                    Bar((b.share / maxShare).toFloat(), color)
                    Spacer(Modifier.height(2.dp))
                    Bar(((if (b.baseShare.isNaN()) 0.0 else b.baseShare) / maxShare).toFloat(), baselineColor)
                }
                Text(" ${b.visits}/${fs.n}", style = MaterialTheme.typography.labelSmall, color = KairosColors.Text, modifier = Modifier.width(44.dp))
            }
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Colored = share of his ${fs.n} visits. Gray = share of ${fs.baseHours} camera hours" +
                    if (fs.key == "light") "." else " at the same cameras and times of day.",
                style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim,
            )
            fs.bins.filter { it.visits > 0 || it.verdict != Verdict.NONE }.forEach { b ->
                Text(
                    "${b.label}: ${b.visits} visits (${pct(b.share)}) vs ${pct(b.baseShare)}" +
                        (if (!b.lift.isNaN() && b.baseShare > 0) ", ${"%.1f".format(b.lift)}x" else "") +
                        ", luck odds ${"%.2f".format(if (b.verdict == Verdict.AVOIDS) b.pLow else b.pHigh)}" +
                        (if (b.verdict != Verdict.NONE) " -> ${b.verdict.label}" else ""),
                    style = MaterialTheme.typography.labelSmall, color = KairosColors.Text,
                )
            }
        } else if (marked.isEmpty()) {
            Text("No clear lean yet. Tap for numbers.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
        }
    }
}

@Composable
private fun Bar(frac: Float, c: Color) {
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val w = (size.width * frac.coerceIn(0f, 1f)).coerceAtLeast(if (frac > 0) 3f else 0f)
        drawRoundRect(c, size = Size(w, size.height), cornerRadius = CornerRadius(3f, 3f))
    }
}

internal fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.STRONG -> KairosColors.Good
    Verdict.LEANING -> KairosColors.Water
    Verdict.AVOIDS -> KairosColors.Error
    Verdict.NONE -> KairosColors.Dim
}

/** Visits per week: shooting-light visits (his color) stacked on night visits (gray). */
@Composable
internal fun WeeksChart(r: BuckReport, color: Color) {
    val weeks = r.weeks.takeLast(12) // the last 12 weeks keep the date labels readable
    if (weeks.isEmpty()) return
    val tm = rememberTextMeasurer()
    val label = KairosColors.Dim
    val maxV = weeks.maxOf { it.visits }.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val bottom = size.height - 16.dp.toPx()
        val slot = size.width / weeks.size
        val bw = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        weeks.forEachIndexed { i, w ->
            val x = i * slot + (slot - bw) / 2
            val night = w.visits - w.legalVisits
            val hN = bottom * night / maxV
            val hL = bottom * w.legalVisits / maxV
            if (night > 0) drawRoundRect(baselineColor, Offset(x, bottom - hN), Size(bw, hN), CornerRadius(4f, 4f))
            if (w.legalVisits > 0) drawRoundRect(color, Offset(x, bottom - hN - hL - (if (night > 0) 2.dp.toPx() else 0f)), Size(bw, hL), CornerRadius(4f, 4f))
            drawText(tm, "${w.weekStart.monthValue}/${w.weekStart.dayOfMonth}", Offset(x - 4, bottom + 2), TextStyle(fontSize = 9.sp, color = label))
            drawText(tm, "${w.visits}", Offset(x + bw / 2 - 4, bottom - hN - hL - 14.dp.toPx()), TextStyle(fontSize = 10.sp, color = KairosColors.Text))
        }
    }
    Row { LegendRow(color, "In shooting light"); Spacer(Modifier.width(12.dp)); LegendRow(baselineColor, "Night") }
}

/** Cameras placed by location, with his moves between them as arrows (width = how often). */
@Composable
internal fun TravelMap(r: BuckReport, color: Color) {
    val cams = BuckAnalysis.state?.cams.orEmpty().values.map { it.cam }.filter { it.lat != null && it.lon != null && r.cameras.any { c -> c.camera.id == it.id } }
    if (cams.size < 2) return
    val tm = rememberTextMeasurer()
    val text = KairosColors.Text
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val pad = 40.dp.toPx()
        val minLat = cams.minOf { it.lat!! }; val maxLat = cams.maxOf { it.lat!! }
        val minLon = cams.minOf { it.lon!! }; val maxLon = cams.maxOf { it.lon!! }
        val kx = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = ((maxLon - minLon) * kx).coerceAtLeast(1e-5); val spanY = (maxLat - minLat).coerceAtLeast(1e-5)
        val s = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        fun pos(id: String): Offset? {
            val c = cams.firstOrNull { it.id == id } ?: return null
            return Offset((pad + ((c.lon!! - minLon) * kx * s)).toFloat(), (size.height - pad - ((c.lat!! - minLat) * s)).toFloat())
        }
        r.moves.forEach { m ->
            val a = pos(m.fromId) ?: return@forEach
            val b = pos(m.toId) ?: return@forEach
            drawLine(color, a, b, strokeWidth = (1.5f + m.count).dp.toPx().coerceAtMost(8.dp.toPx()))
            val d = b - a; val len = d.getDistance().coerceAtLeast(1f); val u = d / len
            val tip = b - u * 14.dp.toPx()
            val n = Offset(-u.y, u.x)
            drawPath(Path().apply { moveTo(tip.x + u.x * 8, tip.y + u.y * 8); lineTo(tip.x - u.x * 6 + n.x * 7, tip.y - u.y * 6 + n.y * 7); lineTo(tip.x - u.x * 6 - n.x * 7, tip.y - u.y * 6 - n.y * 7); close() }, color)
            val mid = (a + b) / 2f
            drawText(tm, "${m.count}x", mid + Offset(4f, 4f), TextStyle(fontSize = 11.sp, color = text, fontWeight = FontWeight.Bold))
        }
        cams.forEach { c ->
            val p = pos(c.id)!!
            drawCircle(text, 6.dp.toPx(), p)
            drawText(tm, c.name, p + Offset(8f, -18f), TextStyle(fontSize = 11.sp, color = text))
        }
        drawText(tm, "N ↑", Offset(size.width - 30.dp.toPx(), 0f), TextStyle(fontSize = 10.sp, color = KairosColors.Dim))
    }
}


/**
 * His winds: one spoke per visit at the direction the wind came FROM (length = speed, ring =
 * 15 mph), with the sector each one covers for matching (±HisWinds.MATCH_DEG) shaded. [next] =
 * the next sit's wind as a Fern arrow, so you can see at a glance whether it lands in his shading.
 */
@Composable
internal fun HisWindDial(his: List<com.kairos.engine.bucks.VisitWind>, next: com.kairos.engine.bucks.HuntWindow?, color: Color, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val label = KairosColors.Dim
    val ring = KairosColors.Line
    val fern = Accent.fern
    val text = KairosColors.Text
    Canvas(modifier.size(150.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val rMax = size.minDimension / 2 - 14.dp.toPx()
        drawCircle(ring, rMax, c, style = Stroke(1.dp.toPx()))
        drawCircle(ring, rMax / 2, c, style = Stroke(0.5.dp.toPx()))
        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (n, d) ->
            val a = Math.toRadians(d - 90)
            val m = tm.measure(n, TextStyle(fontSize = 10.sp, color = label, fontWeight = FontWeight.Bold))
            val lr = rMax + 8.dp.toPx()
            drawText(m, topLeft = Offset(c.x + lr * cos(a).toFloat() - m.size.width / 2, c.y + lr * sin(a).toFloat() - m.size.height / 2))
        }
        val m = com.kairos.engine.bucks.HisWinds.MATCH_DEG.toFloat()
        his.forEach { w ->
            val d = w.dir ?: return@forEach
            drawArc(color.copy(alpha = 0.16f), (d - 90 - m).toFloat(), 2 * m, true, Offset(c.x - rMax, c.y - rMax), Size(2 * rMax, 2 * rMax))
        }
        his.forEach { w ->
            val d = w.dir
            if (d == null) { drawCircle(color, 5.dp.toPx(), c, style = Stroke(2.dp.toPx())); return@forEach }
            val a = Math.toRadians(d - 90)
            val r = (w.mph.coerceAtMost(15.0) / 15.0 * rMax).toFloat().coerceAtLeast(rMax * 0.3f)
            val tip = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            drawLine(color, c, tip, 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(color, 4.dp.toPx(), tip)
        }
        // The next sit's wind: an arrow from its FROM side into the middle (the way the air moves).
        next?.windDir?.let { d ->
            val a = Math.toRadians(d - 90)
            val from = Offset(c.x + rMax * cos(a).toFloat(), c.y + rMax * sin(a).toFloat())
            drawLine(fern, from, c, 2.5.dp.toPx())
            val back = Math.toRadians(d - 90) // toward the FROM side: the head's back corners
            val head = 9.dp.toPx()
            val p = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + head * cos(back - 0.45).toFloat(), c.y + head * sin(back - 0.45).toFloat())
                lineTo(c.x + head * cos(back + 0.45).toFloat(), c.y + head * sin(back + 0.45).toFloat())
                close()
            }
            drawPath(p, fern)
        }
        drawCircle(text, 2.dp.toPx(), c)
    }
}
