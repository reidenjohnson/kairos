package com.kairos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.data.DayScore
import com.kairos.data.Outlook
import com.kairos.data.ScoreHistory
import com.kairos.engine.SPECIES
import java.time.LocalDate

/**
 * The "Trends" tab: for a chosen species, the forecasted best score for each day
 * ahead (the "expected" line) overlaid with the app's own recorded best score for
 * days already passed (the "actual" dots). See HANDOFF §9 item 3.
 */
@Composable
fun TrendsScreen(state: UiState, outlook: Outlook?) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(SPECIES.first().name) }

    val actual = remember(selected, state) { ScoreHistory.history(context, selected) }
    val expected = remember(selected, outlook) {
        outlook?.perSpecies?.firstOrNull { it.speciesName == selected }?.days ?: emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Score history & outlook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "The line is the model's best score for each day — the past week and the days ahead. " +
                "Dots are what you saw on the days you actually checked, logged automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SPECIES) { sp ->
                FilterChip(
                    selected = sp.name == selected,
                    onClick = { selected = sp.name },
                    label = { Text(sp.name, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KairosColors.SegBottom,
                        selectedLabelColor = KairosColors.OnSeg,
                        containerColor = KairosColors.Surface,
                        labelColor = KairosColors.Dim,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = sp.name == selected,
                        borderColor = KairosColors.Line,
                        selectedBorderColor = KairosColors.Pine,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Chart-only palette (see ChartColors) — a saturated, colorblind-safe pair
        // kept separate from the muted brand accents.
        val expectedColor = ChartColors.Expected
        val actualColor = ChartColors.Actual
        val gridColor = ChartColors.Grid
        val todayColor = ChartColors.Today
        val labelColor = ChartColors.Label

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KairosColors.Surface),
        ) {
            Column(Modifier.padding(14.dp)) {
                Legend(expectedColor, actualColor)
                Spacer(Modifier.height(10.dp))
                if (actual.isEmpty() && expected.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (outlook == null) {
                                "Connect to load the outlook. Your daily scores are logged and will appear here."
                            } else {
                                "Trends will fill in as you check conditions over the next few days."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "${expected.size} days modeled · logged: ${actual.size} " +
                            if (actual.size == 1) "day" else "days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ScoreChart(
                        actual = actual,
                        expected = expected,
                        expectedColor = expectedColor,
                        actualColor = actualColor,
                        gridColor = gridColor,
                        todayColor = todayColor,
                        labelColor = labelColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(expectedColor: Color, actualColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(18.dp).height(4.dp)) {
            drawLine(expectedColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
        }
        Spacer(Modifier.width(6.dp))
        Text("Expected (forecast)", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(16.dp))
        Canvas(Modifier.width(12.dp).height(12.dp)) {
            drawCircle(actualColor, radius = size.minDimension / 2.5f, center = Offset(size.width / 2, size.height / 2))
        }
        Spacer(Modifier.width(6.dp))
        Text("Actual (recorded)", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The chart: one Canvas that draws its own axes, grid, and labels, so nothing
 * depends on sibling layout. X is a real date axis (past actuals on the left,
 * the outlook to the right of the dashed "today" line); Y is the 0-100 score.
 */
@Composable
private fun ScoreChart(
    actual: List<ScoreHistory.Point>,
    expected: List<DayScore>,
    expectedColor: Color,
    actualColor: Color,
    gridColor: Color,
    todayColor: Color,
    labelColor: Color,
) {
    val today = LocalDate.now()
    val dates = actual.map { it.date } + expected.map { it.date } + today
    val startDay = dates.min().toEpochDay()
    val endDayRaw = dates.max().toEpochDay()
    val endDay = if (endDayRaw == startDay) startDay + 1 else endDayRaw
    val span = (endDay - startDay).toFloat()

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp),
    ) {
        val padL = 30.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 8.dp.toPx()
        val padB = 20.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB

        fun xOf(d: LocalDate) = padL + ((d.toEpochDay() - startDay).toFloat() / span) * plotW
        fun yOf(pct: Int) = padT + (1f - pct / 100f) * plotH

        val text = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        val native = drawContext.canvas.nativeCanvas

        // Grid + Y labels (0 / 50 / 100)
        listOf(0, 50, 100).forEach { g ->
            val y = yOf(g)
            drawLine(gridColor, Offset(padL, y), Offset(padL + plotW, y), strokeWidth = 1.5f)
            text.textAlign = android.graphics.Paint.Align.RIGHT
            native.drawText("$g", padL - 4.dp.toPx(), y + 3.5.dp.toPx(), text)
        }

        // Today marker (dashed vertical line)
        val tx = xOf(today).coerceIn(padL, padL + plotW)
        drawLine(
            todayColor.copy(alpha = 0.85f),
            Offset(tx, padT),
            Offset(tx, padT + plotH),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )

        // Expected line + points
        val exp = expected.sortedBy { it.date }
        for (i in 0 until exp.size - 1) {
            drawLine(
                expectedColor,
                Offset(xOf(exp[i].date), yOf(exp[i].bestPercent)),
                Offset(xOf(exp[i + 1].date), yOf(exp[i + 1].bestPercent)),
                strokeWidth = 4f,
            )
        }
        exp.forEach { drawCircle(expectedColor, 4f, Offset(xOf(it.date), yOf(it.bestPercent))) }

        // Actual line + dots — drawn solid and as thick as the expected line so
        // it reads just as clearly, with slightly larger dots on top.
        val act = actual.sortedBy { it.date }
        for (i in 0 until act.size - 1) {
            drawLine(
                actualColor,
                Offset(xOf(act[i].date), yOf(act[i].percent)),
                Offset(xOf(act[i + 1].date), yOf(act[i + 1].percent)),
                strokeWidth = 4f,
            )
        }
        act.forEach { drawCircle(actualColor, 7f, Offset(xOf(it.date), yOf(it.percent))) }

        // X labels: start (left), end (right), "today" under its marker
        val baseline = size.height - 5.dp.toPx()
        text.textAlign = android.graphics.Paint.Align.LEFT
        native.drawText(monthDay(LocalDate.ofEpochDay(startDay)), padL, baseline, text)
        text.textAlign = android.graphics.Paint.Align.RIGHT
        native.drawText(monthDay(LocalDate.ofEpochDay(endDay)), padL + plotW, baseline, text)
        // Only label the marker "today" when it won't collide with the end date
        // labels (early on, today == the start date, so its date already shows).
        val edgeGap = 28.dp.toPx()
        if (tx > padL + edgeGap && tx < padL + plotW - edgeGap) {
            val todayText = android.graphics.Paint(text).apply {
                color = todayColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            native.drawText("today", tx, baseline, todayText)
        }
    }
}
