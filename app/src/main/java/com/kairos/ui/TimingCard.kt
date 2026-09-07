package com.kairos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.data.DayTiming
import com.kairos.data.HourScore
import com.kairos.engine.Side
import com.kairos.engine.rating
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * The Today hero: BOTH sides at a glance. Two gauges (Hunt + Fish) show each side's
 * day score and its best window; below them a shared, interactive chart plots the two
 * hourly curves together. You can scroll across the next several days and drag a
 * scrubber to read the score at any time. Tap a gauge to open that side's game plan.
 * Curves are colored by side identity — Hunt = Fern (forest), Fish = teal (water).
 */
@Composable
fun TodayHero(today: DayTiming, week: List<DayTiming>, onOpenSide: (Side) -> Unit) {
    val days = week.ifEmpty { listOf(today) }
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
            Box(Modifier.height(12.dp).width(3.dp).clip(RoundedCornerShape(2.dp)).background(KairosColors.Water))
            Spacer(Modifier.width(8.dp))
            Overline("Today's outlook", color = KairosColors.Water)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideGauge(Side.HUNT, today, Modifier.weight(1f), onOpenSide)
            SideGauge(Side.FISH, today, Modifier.weight(1f), onOpenSide)
        }
        Spacer(Modifier.height(16.dp))
        InteractiveTimingChart(days)
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
                Text("$score", fontFamily = Bricolage, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, lineHeight = 34.sp, color = rColor)
                Text(ratingLabel(rating(score)).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = rColor, letterSpacing = 0.6.sp)
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
        val start = 135f; val sweep = 270f
        drawArc(track, start, sweep, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, start, sweep * (score.coerceIn(0, 100) / 100f), false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/**
 * Both sides' hourly curves for a chosen day. A scrollable day strip picks the day;
 * a draggable scrubber reads the Hunt/Fish score at any hour, with the peak windows
 * shaded and (on today) a "now" line.
 */
@Composable
private fun InteractiveTimingChart(days: List<DayTiming>) {
    var sel by remember { mutableIntStateOf(0) }
    // Scrub position as a 0..1 fraction across the day; null = default (now / peak).
    var scrub by remember(sel) { mutableStateOf<Float?>(null) }

    val day = days[sel.coerceIn(0, days.lastIndex)]
    val isToday = day.date == LocalDate.now()
    val byHour = remember(day) { day.hours.associateBy { it.hour } }
    val nowFrac = LocalTime.now().let { (it.hour + it.minute / 60f) / 24f }
    // Default readout: now on today, else the day's better peak hour.
    val defaultHour = if (isToday) nowFrac.times(24f).roundToInt() else peakHour(day)
    val activeHour = (scrub?.times(24f)?.roundToInt() ?: defaultHour).coerceIn(0, 23)
    val huntAt = byHour[activeHour]?.huntScore ?: 0
    val fishAt = byHour[activeHour]?.fishScore ?: 0

    // Day strip — the whole week, evenly split so it fits without scrolling. A dot marks
    // the best hunt day (green) and the best fish day (blue) so you don't have to hunt for it.
    val bestHunt = days.indices.maxByOrNull { days[it].huntToday }
    val bestFish = days.indices.maxByOrNull { days[it].fishToday }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        days.forEachIndexed { i, d ->
            Box(Modifier.weight(1f)) {
                DayChip(d, i == sel, isHuntBest = i == bestHunt, isFishBest = i == bestFish) { sel = i; scrub = null }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    // Scrubber readout.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fmtHour(activeHour), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text)
        Spacer(Modifier.width(12.dp))
        ReadoutDot("Hunt", huntAt, KairosColors.Pine)
        Spacer(Modifier.width(12.dp))
        ReadoutDot("Fish", fishAt, KairosColors.Water)
        Spacer(Modifier.weight(1f))
        Text(
            if (scrub == null && isToday) "now · drag to explore" else "drag to explore",
            style = MaterialTheme.typography.labelSmall,
            color = KairosColors.Faint,
        )
    }
    Spacer(Modifier.height(8.dp))

    val huntColor = KairosColors.Pine
    val fishColor = KairosColors.Water
    val gridColor = KairosColors.Line
    val nowColor = KairosColors.Water
    val scrubColor = KairosColors.Text
    val labelColor = KairosColors.Faint
    val huntWin = day.bestWindows(Side.HUNT)
    val fishWin = day.bestWindows(Side.FISH)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .pointerInput(sel) {
                detectHorizontalDragGestures { change, _ ->
                    scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(sel) {
                detectTapGestures { off -> scrub = (off.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val padT = 6.dp.toPx(); val padB = 18.dp.toPx()
        val plotH = size.height - padT - padB
        val w = size.width
        fun xOf(h: Double) = (h / 24.0).toFloat() * w
        fun yOf(v: Int) = padT + (1f - v / 100f) * plotH

        // Peak-window shading (our "best times"), per side, low alpha.
        fun shade(wins: List<IntRange>, color: Color) {
            wins.forEach { win ->
                val x0 = xOf(win.first.toDouble()); val x1 = xOf((win.last + 1).toDouble())
                drawRect(color.copy(alpha = 0.10f), topLeft = Offset(x0, padT), size = Size(x1 - x0, plotH))
            }
        }
        shade(huntWin, huntColor)
        shade(fishWin, fishColor)

        // Dawn/dusk ticks.
        listOf(day.sunriseHour, day.sunsetHour).forEach { s ->
            drawLine(gridColor, Offset(xOf(s), padT), Offset(xOf(s), padT + plotH), strokeWidth = 1.5f)
        }

        fun curve(pick: (HourScore) -> Int, color: Color) {
            val pts = day.hours.sortedBy { it.hour }.map { it.hour to pick(it) }
            if (pts.size < 2) return
            val line = Path(); val area = Path()
            pts.forEachIndexed { i, (h, v) ->
                val x = xOf(h.toDouble()); val y = yOf(v)
                if (i == 0) { line.moveTo(x, y); area.moveTo(x, padT + plotH); area.lineTo(x, y) } else { line.lineTo(x, y); area.lineTo(x, y) }
            }
            area.lineTo(xOf(pts.last().first.toDouble()), padT + plotH); area.close()
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.01f))))
            drawPath(line, color, style = Stroke(width = 3.5f))
        }
        curve({ it.huntScore }, huntColor)
        curve({ it.fishScore }, fishColor)

        // Now line (only on today).
        if (isToday && nowFrac in 0f..1f) {
            drawLine(nowColor.copy(alpha = 0.5f), Offset(nowFrac * w, padT), Offset(nowFrac * w, padT + plotH), strokeWidth = 2f)
        }

        // Scrubber: a solid line + dots on each curve at the active hour.
        val sx = xOf(activeHour.toDouble())
        drawLine(scrubColor, Offset(sx, padT), Offset(sx, padT + plotH), strokeWidth = 2.5f)
        drawCircle(huntColor, 5.dp.toPx(), Offset(sx, yOf(huntAt)))
        drawCircle(fishColor, 5.dp.toPx(), Offset(sx, yOf(fishAt)))

        val text = android.graphics.Paint().apply {
            color = labelColor.toArgb(); textSize = 9.sp.toPx(); isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        listOf(4, 8, 12, 16, 20).forEach { h ->
            drawContext.canvas.nativeCanvas.drawText(fmtHourShort(h), xOf(h.toDouble()), size.height - 4.dp.toPx(), text)
        }
    }
}

@Composable
private fun ReadoutDot(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text("$label ", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim)
        Text("$value", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text)
    }
}

@Composable
private fun DayChip(
    day: DayTiming,
    selected: Boolean,
    isHuntBest: Boolean,
    isFishBest: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) KairosColors.SegBottom else KairosColors.Surface.copy(alpha = 0.55f)
    val fg = if (selected) KairosColors.OnSeg else KairosColors.Dim
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) Color.Transparent else KairosColors.CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(dowTop(day.date), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
        Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f), maxLines = 1)
        // Best-day dots: green = best hunt day, blue = best fish day.
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (isHuntBest) Box(Modifier.width(5.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(KairosColors.Pine))
            if (isFishBest) Box(Modifier.width(5.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(KairosColors.Water))
            // Keep the row height stable when a day has no dot.
            if (!isHuntBest && !isFishBest) Box(Modifier.height(5.dp))
        }
    }
}

private fun peakHour(day: DayTiming): Int {
    val h = day.hours.maxByOrNull { maxOf(it.huntScore, it.fishScore) } ?: return 12
    return h.hour
}

private fun dayLabel(date: LocalDate): String {
    if (date == LocalDate.now()) return "Today"
    val dow = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
    return "$dow ${date.dayOfMonth}"
}

/** The top line of a day chip: "Today", else the short weekday ("Sun"). */
private fun dowTop(date: LocalDate): String {
    if (date == LocalDate.now()) return "Today"
    return date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
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
