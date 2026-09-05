package com.kairos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.data.DayTiming
import com.kairos.engine.Rating
import com.kairos.engine.Side
import com.kairos.engine.rating
import java.time.LocalTime

/**
 * The at-a-glance "best times today" card: an overall per-side score for the day
 * plus an hour-by-hour curve showing the peak windows to go. On the Best tab it
 * points to whichever side scores better today.
 */
@Composable
fun TimingCard(timing: DayTiming, side: Side?) {
    val effSide = side ?: if (timing.fishToday >= timing.huntToday) Side.FISH else Side.HUNT
    val dayScore = timing.scoreForSide(effSide)
    val ratingColor = ratingColor(rating(dayScore))
    val title = when (side) {
        Side.HUNT -> "HUNTING TODAY"
        Side.FISH -> "FISHING TODAY"
        null -> if (effSide == Side.FISH) "FISHING IS BETTER TODAY" else "HUNTING IS BETTER TODAY"
    }
    val windows = timing.bestWindows(effSide)

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), clip = false, spotColor = KairosColors.ShadowSpot, ambientColor = KairosColors.ShadowSpot)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(KairosColors.HeroTop, KairosColors.HeroBottom)))
            .border(1.dp, KairosColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .height(12.dp)
                            .width(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(KairosColors.Water),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.labelSmall, color = KairosColors.Water, letterSpacing = 1.4.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (windows.isEmpty()) "Steady all day" else "Best  " + windows.joinToString("  ·  ") { fmtWindow(it) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KairosColors.Text,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$dayScore", fontFamily = Bricolage, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, lineHeight = 44.sp, color = ratingColor)
                Text(ratingLabel(rating(dayScore)).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ratingColor, letterSpacing = 0.4.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        TimingChart(timing, effSide, ratingColor)
    }
}

@Composable
private fun TimingChart(timing: DayTiming, side: Side, lineColor: androidx.compose.ui.graphics.Color) {
    val labelColor = KairosColors.Faint
    val gridColor = KairosColors.Line
    val nowColor = KairosColors.Water
    val values = timing.hours.associate { it.hour to if (side == Side.FISH) it.fishScore else it.huntScore }
    val windows = timing.bestWindows(side)
    val nowH = LocalTime.now().let { it.hour + it.minute / 60.0 }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val padL = 6.dp.toPx()
        val padR = 6.dp.toPx()
        val padT = 6.dp.toPx()
        val padB = 18.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        fun xOf(h: Double) = padL + (h / 24.0).toFloat() * plotW
        fun yOf(v: Int) = padT + (1f - v / 100f) * plotH

        // Peak-window highlight bands.
        windows.forEach { w ->
            val x0 = xOf(w.first.toDouble())
            val x1 = xOf((w.last + 1).toDouble())
            drawRect(
                color = lineColor.copy(alpha = 0.14f),
                topLeft = Offset(x0, padT),
                size = androidx.compose.ui.geometry.Size((x1 - x0), plotH),
            )
        }

        // Curve (area + line).
        val pts = (0..23).mapNotNull { h -> values[h]?.let { h to it } }
        if (pts.size >= 2) {
            val line = Path()
            val area = Path()
            pts.forEachIndexed { i, (h, v) ->
                val x = xOf(h.toDouble()); val y = yOf(v)
                if (i == 0) { line.moveTo(x, y); area.moveTo(x, padT + plotH); area.lineTo(x, y) } else { line.lineTo(x, y); area.lineTo(x, y) }
            }
            area.lineTo(xOf(pts.last().first.toDouble()), padT + plotH)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))))
            drawPath(line, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f))
        }

        // Dawn/dusk ticks.
        listOf(timing.sunriseHour, timing.sunsetHour).forEach { s ->
            drawLine(gridColor, Offset(xOf(s), padT), Offset(xOf(s), padT + plotH), strokeWidth = 1.5f)
        }

        // Now marker.
        if (nowH in 0.0..24.0) {
            drawLine(nowColor, Offset(xOf(nowH), padT), Offset(xOf(nowH), padT + plotH), strokeWidth = 2f)
        }

        // Hour labels.
        val text = android.graphics.Paint().apply {
            color = labelColor.toArgb(); textSize = 9.sp.toPx(); isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        listOf(4, 8, 12, 16, 20).forEach { h ->
            drawContext.canvas.nativeCanvas.drawText(fmtHourShort(h), xOf(h.toDouble()), size.height - 4.dp.toPx(), text)
        }
    }
}

private fun fmtWindow(w: IntRange): String = "${fmtHour(w.first)}–${fmtHour(w.last + 1)}"

private fun fmtHour(h24: Int): String {
    val h = ((h24 % 24) + 24) % 24
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when (h % 12) { 0 -> 12; else -> h % 12 }
    return "$h12 $ampm"
}

private fun fmtHourShort(h24: Int): String {
    val h = h24 % 24
    val ampm = if (h < 12) "a" else "p"
    val h12 = when (h % 12) { 0 -> 12; else -> h % 12 }
    return "$h12$ampm"
}
