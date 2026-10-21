package com.kairos.ui.bucks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.bucks.BuckAnalysis
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Photo
import com.kairos.bucks.Quarters
import com.kairos.bucks.TimeSource
import com.kairos.bucks.U
import com.kairos.engine.bucks.Factors
import com.kairos.engine.bucks.HourFeatures
import com.kairos.engine.bucks.MomentWriter
import com.kairos.engine.bucks.WindAt
import com.kairos.engine.moonInfo
import com.kairos.ui.KairosColors
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * One photo: the picture (hold it to sort), when it was (tap to fix), who's in it (tap to
 * change), the weather that moment (tap any tile for the full close-up), and a write-up built
 * from that exact moment and his history. "Watch it on the map" replays the wind around it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoMomentScreen(photoId: String, onOpenBuck: (String) -> Unit, onGone: () -> Unit, onMap: (Long, String?) -> Unit = { _, _ -> }) {
    val db = BuckStore.db
    val p = db.photo(photoId) ?: return
    val res = BuckAnalysis.state
    val f = res?.photoWx?.get(photoId)
    val cam = db.camera(p.cameraId)
    val bundle = p.cameraId?.let { res?.cams?.get(it) }
    val bucks = p.buckIds.mapNotNull { db.buck(it) }
    val zone = ZoneId.systemDefault()
    val t = p.epochSec
    var sorting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var wxOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var faceFor by remember { mutableStateOf<String?>(null) }
    var similar by remember { mutableStateOf(false) }
    if (similar) SimilarWeatherSheet(photoId, onClose = { similar = false })
    val nav = com.kairos.ui.LocalAppNav.current
    // Full screen: this buck's photos (swipe through him), else just this one.
    val viewerIds = (bucks.firstOrNull()?.let { b -> db.photosOf(b.id).map { it.id } } ?: emptyList()).let { if (photoId in it) it else listOf(photoId) }
    // The 15-minute reading right at the photo beats the hour's average.
    val q = if (t != null && bundle != null) Quarters.at(bundle.quarters, t) else null
    val windDir = q?.dir ?: f?.windDir
    val windMph = q?.mph ?: f?.windMph

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            ZoomPhoto(p.id, Modifier.fillMaxSize(), onTap = { nav.openPhotos(viewerIds, viewerIds.indexOf(photoId)) }, onHold = { sorting = true })
            Row(
                Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                    .clickable { sorting = true }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("Sort", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // When + where, tap to fix.
            Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { editing = true }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(photoWhen(p), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Edit, "Fix the time or temperature", tint = Accent.teal, modifier = Modifier.size(20.dp))
                }
                Text(
                    listOfNotNull(cam?.name ?: "No camera", "time " + p.timeSource.label.lowercase(), p.stripTempF?.let { "strip " + U.temp(it.toDouble()) }).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim,
                )
            }
            if (p.timeCheck || p.timeSource == TimeSource.NONE) Banner("This time needs a check. Tap to fix it.", Accent.amber) { editing = true }

            // Who's in it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("IN THIS PHOTO", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bucks.forEach { b -> Pill(if (b.pool) "Young bucks" else b.name, buckColor(b.colorIdx), Modifier.clickable { onOpenBuck(b.id) }) }
                    if (bucks.isEmpty()) Pill(whoLabel(p))
                }
                TextButton(onClick = { sorting = true }) { Text("Change", color = Accent.teal, fontWeight = FontWeight.Bold) }
            }

            bucks.firstOrNull { !it.pool }?.let { b ->
                TextButton(onClick = { faceFor = b.id }, modifier = Modifier.padding(top = 0.dp)) {
                    Text(if (b.face == null) "Use this for ${b.name}'s profile photo" else "Use this for ${b.name}'s photo instead", color = Accent.teal)
                }
            }

            // The weather that moment: compact tiles, tap for everything.
            if (f == null || t == null) {
                ChartCard("Weather at this moment", accent = Accent.teal) {
                    Text(
                        when {
                            t == null -> "Set this photo's time to pull its weather."
                            cam?.lat == null -> "Pin ${cam?.name ?: "this camera"} on the map (Bucks, Your cameras) to pull its weather."
                            else -> "Weather not downloaded yet; it fills in with signal."
                        },
                        color = KairosColors.Dim,
                    )
                }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Wind", { WindTag(windDir, windMph) }, f.gustMph?.takeIf { it >= (windMph ?: 0.0) + 5 }?.let { "gusts ${U.speed(it)}" } ?: "steady") { wxOpen = true }
                    Tile("Temp", { Big(f.tempF?.let { U.temp(it) } ?: "?") }, f.departureF?.let { (if (it >= 0) "+" else "−") + U.tempDelta(kotlin.math.abs(it)) + " vs normal" }) { wxOpen = true }
                    Tile("Pressure", { Big(f.pressureHpa?.let { U.pressure(it) } ?: "?") }, f.pressTrend3h?.let { trendWord(it) }) { wxOpen = true }
                    Tile("Moon", { Big("${(f.moonIllum * 100).roundToInt()}%") }, moonInfo(Instant.ofEpochSecond(t).atZone(zone).toLocalDate()).phaseName) { wxOpen = true }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface).clickable { onMap(t, bucks.firstOrNull()?.id) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Map, null, tint = Accent.teal)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Watch it on the map", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("The wind moving around this moment, 15 minutes at a time", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Accent.soft(Accent.fern)).clickable { similar = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WbCloudy, null, tint = Accent.fern)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Show me similar weather", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("The next days with this wind at this time, from the 16-day forecast", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
                    }
                }

                // The write-up for this exact moment.
                val b0 = bucks.firstOrNull()
                val visits = b0?.let { res.reports[it.id]?.visits }.orEmpty()
                val near = bundle?.let { Quarters.between(it.quarters, t - 2 * 3600, t + 2 * 3600).map { x -> WindAt(x.t, x.dir, x.mph) } }.orEmpty()
                val story = remember(p.id, f, visits, near, com.kairos.bucks.BuckPrefs.temp, com.kairos.bucks.BuckPrefs.wind, com.kairos.bucks.BuckPrefs.compass, com.kairos.bucks.BuckPrefs.clock24) {
                    MomentWriter.write(f, t, zone, p.cameraId ?: "", cam?.name ?: "this camera", b0?.name, b0?.pool == true, visits, Factors.rut, U, near)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Accent.planBrush).padding(20.dp)) {
                    Text(story.headline.uppercase(), color = Accent.amberOnPhoto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(story.sub, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    story.lines.forEach { l ->
                        Text(l.label.uppercase(), color = Accent.tealOnPhoto, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(l.text, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (story.steps.isNotEmpty()) ChartCard("Do this", accent = Accent.fern) {
                    story.steps.forEachIndexed { i, s ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Accent.soft(Accent.fern)), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = Accent.fern, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(s.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(s.detail, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete this photo", color = KairosColors.Dim) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (sorting) SortSheet(listOf(p.id), onClose = { sorting = false })
    faceFor?.let { id -> FaceSheet(id, p.id) { faceFor = null } }
    if (editing) EditPhotoSheet(p.id) { editing = false }
    if (wxOpen && f != null && t != null) WeatherTakeover(p, f, bundle, t) { wxOpen = false }
    if (confirmDelete) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this photo?") },
            text = { Text("Removes it from Kairos. Delete the file on your phone too?") },
            confirmButton = { TextButton(onClick = { BuckStore.deletePhotos(context, setOf(p.id), fromPhone = true); confirmDelete = false; onGone() }) { Text("Delete from phone too", color = KairosColors.Error) } },
            dismissButton = { TextButton(onClick = { BuckStore.deletePhoto(p.id); confirmDelete = false; onGone() }) { Text("Kairos only") } },
        )
    }
}

private fun trendWord(dh: Double): String = when {
    dh <= -1.0 -> "falling (${U.pressureDelta(dh)} in 3 h)"
    dh >= 1.0 -> "rising (${U.pressureDelta(dh)} in 3 h)"
    else -> "steady"
}

@Composable
private fun Big(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)

@Composable
private fun Tile(label: String, value: @Composable () -> Unit, sub: String?, onClick: () -> Unit) {
    Column(Modifier.widthIn(min = 116.dp).clip(RoundedCornerShape(18.dp)).background(KairosColors.Surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        value()
        sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim) }
    }
}

@Composable
internal fun Banner(text: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Accent.soft(color)).clickable(onClick = onClick).padding(14.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Everything about the weather at one moment, in a pop-up: the hour's numbers, how they compare,
 * and the wind every 15 minutes for 3 hours either side (arrows, so a swing reads at a glance).
 */
@Composable
internal fun WeatherTakeover(p: Photo?, f: HourFeatures, bundle: BuckAnalysis.CamBundle?, t: Long, onClose: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val q = bundle?.let { Quarters.at(it.quarters, t) }
    Takeover(onClose, title = "Weather · " + U.clock(t, zone)) {
        Text(listOfNotNull(bundle?.cam?.name, lightLine(f)).joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cell("Wind", Modifier.weight(1f)) { WindTag(q?.dir ?: f.windDir, q?.mph ?: f.windMph, big = true) }
            Cell("Gusts", Modifier.weight(1f)) { Big((q?.gust ?: f.gustMph)?.let { U.speed(it) } ?: "?") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cell("Temp", Modifier.weight(1f)) {
                Big((q?.tempF ?: f.tempF)?.let { U.temp(it) } ?: "?")
                f.departureF?.let { Text((if (it >= 0) "+" else "−") + U.tempDelta(kotlin.math.abs(it)) + " vs normal", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim) }
                p?.stripTempF?.let { Text("strip said " + U.temp(it.toDouble()), style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim) }
            }
            Cell("Pressure", Modifier.weight(1f)) {
                Big(f.pressureHpa?.let { U.pressure(it) } ?: "?")
                f.pressTrend3h?.let { Text(trendWord(it), style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cell("Last 24 h", Modifier.weight(1f)) { Big(f.tempDay24?.let { (if (it >= 0) "+" else "−") + U.tempDelta(kotlin.math.abs(it)) } ?: "?"); Text("vs the day before", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim) }
            Cell("Moon", Modifier.weight(1f)) { Big("${(f.moonIllum * 100).roundToInt()}%"); Text(moonInfo(Instant.ofEpochSecond(t).atZone(zone).toLocalDate()).phaseName, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            listOfNotNull(
                f.front?.label, f.rain?.label, f.cloudPct?.let { "clouds ${it.roundToInt()}%" }, f.precipIn?.takeIf { it > 0 }?.let { U.rain(it) + " that hour" },
                Factors.rut.label(f.phase),
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim,
        )
        bundle?.quarters?.let { qs ->
            val strip = Quarters.between(qs, t - 3 * 3600, t + 3 * 3600)
            if (strip.size > 2) {
                Spacer(Modifier.height(16.dp))
                Text("WIND EVERY 15 MIN", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    strip.forEach { x ->
                        val now = kotlin.math.abs(x.t - t) < 450
                        Column(
                            Modifier.width(54.dp).clip(RoundedCornerShape(12.dp)).background(if (now) Accent.soft(Accent.teal) else Color.Transparent).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(U.clock(x.t, zone).replace(" AM", "a").replace(" PM", "p"), style = MaterialTheme.typography.labelSmall, color = if (now) Accent.teal else KairosColors.Dim, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal)
                            Spacer(Modifier.height(6.dp))
                            if (x.dir != null && (x.mph ?: 0.0) >= 2) WindArrow(x.dir, Modifier.size(22.dp)) else Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("·", color = KairosColors.Faint) }
                            Spacer(Modifier.height(4.dp))
                            Text(x.mph?.let { "${U.speedNum(it)}" } ?: "?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Speed in ${com.kairos.bucks.BuckPrefs.wind.label}. Arrows point where the air went; the open dot is where it came from.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("NOAA HRRR model weather at the camera (${f.source}; wind every 15 min).", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
    }
}

@Composable
private fun Cell(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface2).padding(12.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

internal fun lightLine(f: HourFeatures): String {
    val sr = f.minsFromSunrise; val ss = f.minsToSunset
    fun hm(m: Int) = if (m < 90) "$m min" else "${m / 60} h ${m % 60} min".replace(" 0 min", "")
    val rel = when {
        ss != null && kotlin.math.abs(ss) <= 180 -> if (ss >= 0) "${hm(ss)} before sunset" else "${hm(-ss)} after sunset"
        sr != null && kotlin.math.abs(sr) <= 240 -> if (sr >= 0) "${hm(sr)} after sunrise" else "${hm(-sr)} before sunrise"
        else -> null
    }
    return listOfNotNull(f.light.label, rel, if (f.legal) "shooting light" else "outside shooting light").joinToString(" · ")
}

