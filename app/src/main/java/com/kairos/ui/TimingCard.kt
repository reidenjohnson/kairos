package com.kairos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.data.DayTiming
import com.kairos.engine.Side
import com.kairos.engine.rating
import java.time.LocalTime

/**
 * The Today hero: BOTH sides at a glance. Two gauges (Hunt + Fish) show each side's
 * day score and its best window; a shared chart plots the two hourly curves together
 * so you can read when each peaks. Tap a gauge to open that side's game plan. The
 * curves are colored by side identity — Hunt = Fern (forest), Fish = teal (water).
 */
@Composable
fun TodayHero(timing: DayTiming, onOpenSide: (Side) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), clip = false, spotColor = KairosColors.ShadowSpot, ambientColor = KairosColors.ShadowSpot)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(KairosColors.HeroTop, KairosColors.HeroBottom)))
            .border(1.dp, KairosColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(12.dp)
                    .width(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KairosColors.Water),
            )
            Spacer(Modifier.width(8.dp))
            Overline("Today's outlook", color = KairosColors.Water)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideGauge(Side.HUNT, timing, Modifier.weight(1f), onOpenSide)
            SideGauge(Side.FISH, timing, Modifier.weight(1f), onOpenSide)
        }
        Spacer(Modifier.height(16.dp))
        Legend()
        Spacer(Modifier.height(8.dp))
        BothSidesChart(timing)
    }
}

private fun sideColor(side: Side): Color = if (side == Side.FISH) KairosColors.Water else KairosColors.Pine

@Composable
private fun SideGauge(side: Side, timing: DayTiming, modifier: Modifier, onOpenSide: (Side) -> Unit) {
    val score = timing.scoreForSide(side)
    val rColor = ratingColor(rating(score))
    val window = timing.bestWindows(side).firstOrNull()
    val label = if (side == Side.FISH) "FISH" else "HUNT"

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenSide(side) }
            .background(KairosColors.Surface.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .border(1.dp, KairosColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(sideColor(side)))
            Spacer(Modifier.width(6.dp))
            Overline(label, color = KairosColors.Dim)
        }
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            GaugeArc(score, rColor, Modifier.size(104.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$score",
                    fontFamily = Bricolage,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp,
                    lineHeight = 34.sp,
                    color = rColor,
                )
                Text(
                    ratingLabel(rating(score)).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = rColor,
                    letterSpacing = 0.6.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (window == null) "Steady all day" else "Best " + fmtWindow(window),
            style = MaterialTheme.typography.labelMedium,
            color = KairosColors.Dim,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A 270° speedometer arc: a faint full track with a value sweep in the rating color. */
@Composable
private fun GaugeArc(score: Int, color: Color, modifier: Modifier) {
    val track = KairosColors.Line
    Canvas(modifier.aspectRatio(1f)) {
        val stroke = 9.dp.toPx()
        val inset = stroke / 2f + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        val start = 135f
        val sweep = 270f
        drawArc(track, start, sweep, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, start, sweep * (score.coerceIn(0, 100) / 100f), false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem("Hunt", KairosColors.Pine)
        LegendItem("Fish", KairosColors.Water)
        Spacer(Modifier.weight(1f))
        Text("best times today", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, fontWeight = FontWeight.SemiBold)
    }
}

/** Both sides' hourly curves on one set of axes, with dawn/dusk ticks and a now line. */
@Composable
private fun BothSidesChart(timing: DayTiming) {
    val gridColor = KairosColors.Line
    val nowColor = KairosColors.Water
    val huntColor = KairosColors.Pine
    val fishColor = KairosColors.Water
    val labelColor = KairosColors.Faint
    val nowH = LocalTime.now().let { it.hour + it.minute / 60.0 }

    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val padL = 6.dp.toPx(); val padR = 6.dp.toPx(); val padT = 6.dp.toPx(); val padB = 18.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        fun xOf(h: Double) = padL + (h / 24.0).toFloat() * plotW
        fun yOf(v: Int) = padT + (1f - v / 100f) * plotH

        // Dawn/dusk ticks (recessive).
        listOf(timing.sunriseHour, timing.sunsetHour).forEach { s ->
            drawLine(gridColor, Offset(xOf(s), padT), Offset(xOf(s), padT + plotH), strokeWidth = 1.5f)
        }

        fun curve(pick: (com.kairos.data.HourScore) -> Int, color: Color) {
            val pts = timing.hours.sortedBy { it.hour }.map { it.hour to pick(it) }
            if (pts.size < 2) return
            val line = Path(); val area = Path()
            pts.forEachIndexed { i, (h, v) ->
                val x = xOf(h.toDouble()); val y = yOf(v)
                if (i == 0) { line.moveTo(x, y); area.moveTo(x, padT + plotH); area.lineTo(x, y) } else { line.lineTo(x, y); area.lineTo(x, y) }
            }
            area.lineTo(xOf(pts.last().first.toDouble()), padT + plotH)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.01f))))
            drawPath(line, color, style = Stroke(width = 3.5f))
        }
        curve({ it.huntScore }, huntColor)
        curve({ it.fishScore }, fishColor)

        if (nowH in 0.0..24.0) {
            drawLine(nowColor, Offset(xOf(nowH), padT), Offset(xOf(nowH), padT + plotH), strokeWidth = 2f)
        }

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
