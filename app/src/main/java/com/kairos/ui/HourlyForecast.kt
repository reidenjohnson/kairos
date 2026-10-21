package com.kairos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.bucks.U
import com.kairos.engine.bucks.HisWinds
import com.kairos.engine.bucks.ModelCheck
import com.kairos.engine.bucks.Sun
import com.kairos.engine.bucks.WindCheck
import com.kairos.engine.bucks.WxHour
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The hour-by-hour forecast (onX-style): 16 days of every hour, scrolled sideways. Rows: light
 * (night shaded, shooting light clear), temperature, wind (arrow = where the air goes, labeled
 * with where it comes FROM), gusts, the two-model wind check, chance of rain, cloud, pressure, and
 * one lane per buck marking the hours whose wind matches one he has moved on.
 *
 * Only the columns on screen are drawn, so 384 hours scroll smoothly.
 */

/**
 * One lane: [hits] = hour starts whose wind matches (drawn faint), [strong] = the ones that also
 * match on something more, like time of day (drawn solid). A buck's lane is all solid.
 */
data class HourLane(val name: String, val color: Color, val hits: Set<Long>, val strong: Set<Long> = hits)

internal val COL_W = 46.dp
private val LABEL_W = 64.dp

private object Rows {
    val day = 20.dp
    val time = 18.dp
    val temp = 62.dp
    val wind = 58.dp
    val check = 12.dp
    val rain = 42.dp
    val cloud = 16.dp
    val press = 34.dp
    val lane = 20.dp
}

/** x of the start of hour [t] in the chart, px. */
internal fun hourX(hours: List<WxHour>, t: Long, colPx: Float): Float {
    val i = hours.indexOfFirst { it.t >= t }.let { if (it < 0) hours.lastIndex else it }
    return i * colPx
}

@Composable
fun HourlyChart(
    hours: List<WxHour>,
    lat: Double,
    lon: Double,
    zone: ZoneId,
    lanes: List<HourLane>,
    selected: Long?,
    scroll: ScrollState,
    /** Sits to tint on the light row: (start, end) unix seconds. */
    sits: List<Pair<Long, Long>> = emptyList(),
    onSelect: (Long) -> Unit,
) {
    if (hours.isEmpty()) return
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val colPx = with(density) { COL_W.toPx() }
    val text = KairosColors.Text
    val dim = KairosColors.Dim
    val faint = KairosColors.Faint
    val grid = KairosColors.Line
    // Night must read at a glance against the card: the page background is the darkest neutral.
    val night = if (KairosColors.dark) Color.Black.copy(alpha = 0.38f) else KairosColors.Surface2
    val teal = KairosColors.Water
    val fern = KairosColors.Pine
    val amber = KairosColors.Fair
    // Sun per local day, worked out once.
    val days = remember(hours, zone) { hours.map { Instant.ofEpochSecond(it.t).atZone(zone).toLocalDate() }.distinct() }
    val legal = remember(days, lat, lon) {
        days.associateWith { d -> Sun.day(d, lat, lon).let { s -> if (s.sunrise == null || s.sunset == null) null else (s.sunrise!! - 1800) to (s.sunset!! + 1800) } }
    }
    val temps = hours.mapNotNull { it.tempF }
    val tLo = (temps.minOrNull() ?: 0.0) - 2
    val tHi = (temps.maxOrNull() ?: 1.0) + 2
    val press = hours.mapNotNull { it.pressureHpa }
    val pLo = press.minOrNull() ?: 0.0
    // At least 4 hPa of range, so a steady day draws flat instead of magnifying noise.
    val pHi = max((press.maxOrNull() ?: 1.0), pLo + 4)
    val totalH = Rows.day + Rows.time + Rows.temp + Rows.wind + Rows.check + Rows.rain + Rows.cloud + Rows.press + Rows.lane * lanes.size + 8.dp

    Row(Modifier.fillMaxWidth()) {
        // Fixed row labels.
        Column(Modifier.width(LABEL_W).padding(end = 6.dp)) {
            Spacer(Modifier.height(Rows.day + Rows.time))
            RowLabel("Temp", Rows.temp)
            RowLabel("Wind", Rows.wind, sub = "gust")
            RowLabel("Models", Rows.check, small = true)
            RowLabel("Rain", Rows.rain, sub = "chance")
            RowLabel("Cloud", Rows.cloud, small = true)
            RowLabel("Pressure", Rows.press, sub = U.pressure(1013.0).substringAfter(" ", ""), small = true)
            lanes.forEach { l ->
                Row(Modifier.height(Rows.lane), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(l.color))
                    Spacer(Modifier.width(4.dp))
                    Text(l.name, style = MaterialTheme.typography.labelSmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Canvas(
                Modifier.width(COL_W * hours.size).height(totalH).pointerInput(hours) {
                    detectTapGestures { p -> hours.getOrNull((p.x / colPx).toInt())?.let { onSelect(it.t) } }
                },
            ) {
                val first = ((scroll.value / colPx).toInt() - 1).coerceAtLeast(0)
                val last = (((scroll.value + scroll.viewportSize) / colPx).toInt() + 1).coerceAtMost(hours.lastIndex)
                var y = 0f
                val yDay = y; y += Rows.day.toPx()
                val yTime = y; y += Rows.time.toPx()
                val yTemp = y; y += Rows.temp.toPx()
                val yWind = y; y += Rows.wind.toPx()
                val yCheck = y; y += Rows.check.toPx()
                val yRain = y; y += Rows.rain.toPx()
                val yCloud = y; y += Rows.cloud.toPx()
                val yPress = y; y += Rows.press.toPx()
                val yLanes = y
                val bottom = yLanes + Rows.lane.toPx() * lanes.size

                for (i in first..last) {
                    val h = hours[i]
                    val x = i * colPx
                    val lg = legal[Instant.ofEpochSecond(h.t).atZone(zone).toLocalDate()]
                    // Night = the hour's middle is outside shooting light.
                    val mid = h.t + 1800
                    if (lg == null || mid < lg.first || mid > lg.second) drawRect(night, Offset(x, yTime), Size(colPx, bottom - yTime))
                    if (sits.any { mid in it.first..it.second }) drawRect(fern, Offset(x, yTime + Rows.time.toPx() - 4.dp.toPx()), Size(colPx, 4.dp.toPx()))
                }
                for (i in first..last) {
                    val h = hours[i]
                    val x = i * colPx
                    val z = Instant.ofEpochSecond(h.t).atZone(zone)
                    if (z.hour == 0) {
                        drawLine(dim, Offset(x, 0f), Offset(x, bottom), 1.dp.toPx())
                        drawText(tm, DAY_FMT.format(z), Offset(x + 4.dp.toPx(), yDay), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = text))
                    } else if (i == first) {
                        // Keep the day visible at the left edge while scrolling through it, unless
                        // the next day's label is about to take that spot.
                        val lx = max(x, scroll.value.toFloat())
                        val next = (i + 1..minOf(i + 4, hours.lastIndex)).firstOrNull { Instant.ofEpochSecond(hours[it].t).atZone(zone).hour == 0 }
                        if (next == null || next * colPx - lx > 96.dp.toPx())
                            drawText(tm, DAY_FMT.format(z), Offset(lx + 4.dp.toPx(), yDay), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = text))
                    }
                    centered(tm, hourLabel(z.hour), x + colPx / 2, yTime + 1.dp.toPx(), 10.sp, dim)

                    // Temperature: line to the next hour + the value.
                    h.tempF?.let { tf ->
                        val ty = yTemp + 20.dp.toPx() + (1 - ((tf - tLo) / (tHi - tLo)).toFloat()) * (Rows.temp.toPx() - 26.dp.toPx())
                        hours.getOrNull(i + 1)?.tempF?.let { n ->
                            val ny = yTemp + 20.dp.toPx() + (1 - ((n - tLo) / (tHi - tLo)).toFloat()) * (Rows.temp.toPx() - 26.dp.toPx())
                            drawLine(amber, Offset(x + colPx / 2, ty), Offset(x + colPx * 1.5f, ny), 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                        drawCircle(amber, 2.5.dp.toPx(), Offset(x + colPx / 2, ty))
                        centered(tm, "${U.tempNum(tf)}°", x + colPx / 2, yTemp + 2.dp.toPx(), 12.sp, text, bold = true)
                    }
                    // Wind: arrow (toward), FROM label, speed, gust.
                    val sp = h.windMph; val d = h.windDir
                    if (sp != null && d != null) {
                        val c = Offset(x + colPx / 2, yWind + 12.dp.toPx())
                        if (sp >= 1) arrow(c, d, 7.dp.toPx() + (sp.coerceAtMost(25.0) / 25.0 * 4.dp.toPx()).toFloat(), text)
                        centered(tm, "${U.speedNum(sp)}", x + colPx / 2, yWind + 23.dp.toPx(), 12.sp, text, bold = true)
                        centered(tm, U.compass(d), x + colPx / 2, yWind + 37.dp.toPx(), 9.sp, dim)
                    }
                    h.gustMph?.let { centered(tm, "${U.speedNum(it)}", x + colPx / 2, yWind + 46.dp.toPx(), 9.sp, faint) }
                    // Two models: quiet gray = agree, Amber = split (don't trust the wind), nothing = one model only.
                    when (WindCheck.of(h)) {
                        ModelCheck.AGREE -> drawRect(faint.copy(alpha = 0.45f), Offset(x + 1, yCheck + 4.dp.toPx()), Size(colPx - 2, 3.dp.toPx()))
                        ModelCheck.SPLIT -> drawRect(amber, Offset(x + 1, yCheck + 3.dp.toPx()), Size(colPx - 2, 5.dp.toPx()))
                        ModelCheck.ONE -> {}
                    }
                    // Rain: chance bar (Teal) + amount when it's measurable.
                    h.precipProb?.let { pp ->
                        val bh = (pp / 100.0 * (Rows.rain.toPx() - 16.dp.toPx())).toFloat()
                        drawRect(teal.copy(alpha = 0.75f), Offset(x + 8.dp.toPx(), yRain + Rows.rain.toPx() - 2.dp.toPx() - bh), Size(colPx - 16.dp.toPx(), bh))
                        if (pp >= 10) centered(tm, "${pp.roundToInt()}%", x + colPx / 2, yRain, 9.sp, dim)
                    }
                    h.cloudPct?.let { cc -> drawRect(faint.copy(alpha = (0.08 + cc / 100.0 * 0.5).toFloat()), Offset(x, yCloud + 3.dp.toPx()), Size(colPx, 10.dp.toPx())) }
                    h.pressureHpa?.let { p ->
                        fun py(v: Double) = yPress + 14.dp.toPx() + (1 - ((v - pLo) / (pHi - pLo)).toFloat()) * (Rows.press.toPx() - 18.dp.toPx())
                        hours.getOrNull(i + 1)?.pressureHpa?.let { n -> drawLine(dim, Offset(x + colPx / 2, py(p)), Offset(x + colPx * 1.5f, py(n)), 1.5.dp.toPx()) }
                        // The value itself every 3 hours, so the line reads without a scale.
                        if (z.hour % 3 == 0) centered(tm, U.pressure(p).substringBefore(" "), x + colPx / 2, yPress, 8.sp, faint)
                    }
                    lanes.forEachIndexed { k, l ->
                        if (h.t in l.hits) {
                            val ly = yLanes + k * Rows.lane.toPx()
                            drawRoundRect(if (h.t in l.strong) l.color else l.color.copy(alpha = 0.35f), Offset(x + 2, ly + 5.dp.toPx()), Size(colPx - 4, Rows.lane.toPx() - 10.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                        }
                    }
                    drawLine(grid, Offset(x, yLanes), Offset(x, bottom), 0.5f)
                }
                for (k in 0..lanes.size) drawLine(grid, Offset(first * colPx, yLanes + k * Rows.lane.toPx()), Offset((last + 1) * colPx, yLanes + k * Rows.lane.toPx()), 0.5f)
                // Selected hour.
                selected?.let { s ->
                    val i = hours.indexOfFirst { it.t == s }
                    if (i in first..last) {
                        val x = i * colPx
                        drawRect(teal.copy(alpha = 0.16f), Offset(x, yTime), Size(colPx, bottom - yTime))
                        drawRect(teal, Offset(x, yTime), Size(colPx, 2.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowLabel(text: String, h: Dp, sub: String? = null, small: Boolean = false) {
    Column(Modifier.height(h), verticalArrangement = Arrangement.Center) {
        Text(text, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.SemiBold, maxLines = 1)
        sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, maxLines = 1) }
    }
}

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE M/d")

private fun hourLabel(h: Int): String = if (com.kairos.bucks.BuckPrefs.clock24) "%02d".format(h) else when (h) {
    0 -> "12a"; 12 -> "12p"; else -> if (h < 12) "${h}a" else "${h - 12}p"
}

private fun DrawScope.centered(tm: TextMeasurer, s: String, cx: Float, top: Float, size: androidx.compose.ui.unit.TextUnit, color: Color, bold: Boolean = false) {
    val m = tm.measure(s, TextStyle(fontSize = size, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(m, topLeft = Offset(cx - m.size.width / 2f, top))
}

/** An arrow centred on [c] pointing where wind FROM [fromDeg] goes. */
internal fun DrawScope.arrow(c: Offset, fromDeg: Double, len: Float, color: Color) {
    rotate((fromDeg + 180).toFloat(), c) {
        val p = Path().apply {
            moveTo(c.x, c.y - len)                      // tip (up = toward, before rotation)
            lineTo(c.x + len * 0.55f, c.y + len * 0.1f)
            lineTo(c.x + len * 0.18f, c.y + len * 0.1f)
            lineTo(c.x + len * 0.18f, c.y + len)
            lineTo(c.x - len * 0.18f, c.y + len)
            lineTo(c.x - len * 0.18f, c.y + len * 0.1f)
            lineTo(c.x - len * 0.55f, c.y + len * 0.1f)
            close()
        }
        drawPath(p, color)
    }
}

/** A small wind arrow for rows and cards. */
@Composable
fun WindArrow(fromDeg: Double?, size: Dp = 18.dp, color: Color = KairosColors.Text) {
    Canvas(Modifier.size(size)) {
        if (fromDeg != null) arrow(center, fromDeg, this.size.minDimension / 2f, color)
        else drawCircle(color, this.size.minDimension / 5f, center, style = Stroke(1.5.dp.toPx()))
    }
}

/** Plain-English sky from the WMO weather code (table: open-meteo.com/en/docs, "WMO Weather interpretation codes"). */
fun skyWords(code: Int?): String? = when (code) {
    null -> null
    0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
    61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"; 66, 67 -> "Freezing rain"
    71 -> "Light snow"; 73 -> "Snow"; 75 -> "Heavy snow"; 77 -> "Snow grains"
    80 -> "Light showers"; 81 -> "Showers"; 82 -> "Heavy showers"; 85, 86 -> "Snow showers"
    95 -> "Thunderstorms"; 96, 99 -> "Thunderstorms, hail"
    else -> null
}

/**
 * Every number for one hour, upfront: the readout over the chart. [extra] goes under it (the
 * bucks whose wind this is, a "play on the map" action).
 */
@Composable
fun HourReadout(h: WxHour, zone: ZoneId, extra: @Composable () -> Unit = {}) {
    val z = Instant.ofEpochSecond(h.t).atZone(zone)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KairosColors.Surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${READ_FMT.format(z)}, ${U.clock(z)}", style = MaterialTheme.typography.labelLarge, color = KairosColors.Dim, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(h.tempF?.let { U.temp(it) } ?: "-", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = KairosColors.Text)
                    h.feelsF?.let { Text("  feels ${U.temp(it)}", style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim, modifier = Modifier.padding(bottom = 8.dp)) }
                }
                skyWords(h.code)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Text) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WindArrow(h.windDir, 44.dp)
                Text(h.windDir?.let { U.compass(it) } ?: "Calm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = KairosColors.Text)
                h.windMph?.let { Text(U.speed(it), style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val check = WindCheck.of(h)
        val cells = listOfNotNull(
            h.gustMph?.let { U.speed(it) to "gusts" },
            h.precipProb?.let { "${it.roundToInt()}%" to "chance of rain" },
            h.precipIn?.takeIf { it > 0 }?.let { U.rain(it) to "in the hour" },
            h.snowIn?.takeIf { it > 0 }?.let { U.rain(it) to "snow" },
            h.cloudPct?.let { "${it.roundToInt()}%" to "cloud" },
            h.humidityPct?.let { "${it.roundToInt()}%" to "humidity" },
            h.pressureHpa?.let { U.pressure(it) to "pressure" },
        )
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                row.forEach { (v, l) ->
                    Column(Modifier.weight(1f)) {
                        Text(v, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = KairosColors.Text)
                        Text(l, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val c = when (check) { ModelCheck.AGREE -> KairosColors.Dim; ModelCheck.SPLIT -> KairosColors.Fair; ModelCheck.ONE -> KairosColors.Faint }
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(8.dp))
            Text(
                when (check) {
                    ModelCheck.AGREE -> "Models agree on the wind" + (h.altDir?.let { " (ECMWF ${U.compass(it)} ${h.altMph?.let { m -> U.speed(m) } ?: ""})" } ?: "")
                    ModelCheck.SPLIT -> "Models disagree: ${splitWords(h)}. $CHECK_IT"
                    ModelCheck.ONE -> "Second model doesn't reach this hour"
                },
                style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim,
            )
        }
        extra()
    }
}

private val READ_FMT = DateTimeFormatter.ofPattern("EEE MMM d")

/** One model's wind in words: "calm" under 3 mph (no direction worth matching), else "NW 5 mph". */
internal fun windWords(dir: Double?, mph: Double?): String = when {
    mph == null || dir == null -> "no reading"
    mph < HisWinds.CALM_MPH -> "calm"
    else -> "${U.compass(dir)} ${U.speed(mph)}"
}

/** A split hour said plainly: both readings (Open-Meteo best match = NOAA HRRR, then GFS; ECMWF IFS). */
internal fun splitWords(h: WxHour) = "NOAA says ${windWords(h.windDir, h.windMph)}, ECMWF says ${windWords(h.altDir, h.altMph)}"

/** What to do about a split: look at another forecast for the same hour. */
internal const val CHECK_IT = "Check this hour's wind in onX (or any other forecast) before you go: which way it blows, and whether it's calm."

/** One column per day: high/low, the evening sit's wind, chance of rain, and buck dots. Tap = jump there. */
@Composable
/** [sits] = the sit windows; a day's dots count only hours inside them (any hour of a day matches almost every wind). */
fun DayStrip(hours: List<WxHour>, zone: ZoneId, lanes: List<HourLane>, selectedDay: LocalDate?, sits: List<Pair<Long, Long>> = emptyList(), onPick: (LocalDate) -> Unit) {
    val byDay = remember(hours, zone) { hours.groupBy { Instant.ofEpochSecond(it.t).atZone(zone).toLocalDate() }.toList() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(byDay, key = { _, it -> it.first.toEpochDay() }) { _, (d, hs) ->
            val on = d == selectedDay
            val hi = hs.mapNotNull { it.tempF }.maxOrNull()
            val lo = hs.mapNotNull { it.tempF }.minOrNull()
            // The day's main wind: the 5 PM hour, an evening sit's middle (falls back to noon).
            val pm = hs.firstOrNull { Instant.ofEpochSecond(it.t).atZone(zone).hour == 17 } ?: hs[hs.size / 2]
            val rain = hs.mapNotNull { it.precipProb }.maxOrNull()
            val inSit = hs.filter { h -> sits.any { (a, b) -> h.t < b && h.t + 3600 > a } }
            val bucks = lanes.filter { l -> inSit.any { it.t in l.hits } }
            Column(
                Modifier.width(64.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) KairosColors.Water.copy(alpha = 0.18f) else KairosColors.Surface)
                    .clickable { onPick(d) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (d == LocalDate.now(zone)) "Today" else d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text)
                Text("${d.monthValue}/${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                Spacer(Modifier.height(6.dp))
                WindArrow(pm.windDir, 20.dp)
                Text(pm.windDir?.let { U.compass(it) } ?: "-", style = MaterialTheme.typography.labelSmall, color = KairosColors.Text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(hi?.let { "${U.tempNum(it)}°" } ?: "-", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = KairosColors.Text)
                Text(lo?.let { "${U.tempNum(it)}°" } ?: "-", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                Text(rain?.let { "${it.roundToInt()}%" } ?: " ", style = MaterialTheme.typography.labelSmall, color = KairosColors.Water)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp)) {
                    bucks.take(4).forEach { Box(Modifier.size(8.dp).clip(CircleShape).background(it.color)) }
                }
            }
        }
    }
}

/** The chart's width-aware scroller: centre hour [t] in a [viewport]-wide window. */
internal suspend fun ScrollState.centerOn(hours: List<WxHour>, t: Long, colPx: Float) {
    val x = hourX(hours, t, colPx)
    animateScrollTo((x - viewportSize / 2f + colPx / 2).roundToInt().coerceIn(0, maxValue))
}

@Composable
internal fun rememberColPx(): Float = with(LocalDensity.current) { COL_W.toPx() }
