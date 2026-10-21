package com.kairos.ui.bucks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Photo
import com.kairos.bucks.PhotoLabel
import com.kairos.ui.KairosColors
import java.time.format.DateTimeFormatter

private data class Filter(val label: String, val color: Color?, val test: (Photo) -> Boolean)

private val DAY = DateTimeFormatter.ofPattern("EEE MMM d")

/**
 * Everything brought in, newest first, grouped by day. Filters for each buck, the young-buck
 * group, does, nothing, unsorted and each camera. Tap opens that moment; long-press selects,
 * and selected photos can be deleted from Kairos and from the phone folder they came from.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GalleryPane(onOpenPhoto: (String) -> Unit, startFilter: String? = null) {
    val context = LocalContext.current
    val db = BuckStore.db
    // Saved by name, so back from a photo returns to the same filter (even if a buck was added).
    var filterName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(startFilter ?: "All") }
    var sorting by remember { mutableStateOf<List<String>?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    val filters = buildList {
        add(Filter("All", null) { true })
        db.bucks.filter { !it.pool }.forEach { b -> add(Filter(b.name, buckColor(b.colorIdx)) { b.id in it.buckIds }) }
        db.bucks.firstOrNull { it.pool }?.let { b -> add(Filter("Young bucks", buckColor(b.colorIdx)) { b.id in it.buckIds }) }
        add(Filter("Any buck", null) { it.label == PhotoLabel.BUCK || it.label == PhotoLabel.DEER_UNKNOWN })
        add(Filter("Not sure", null) { it.label == PhotoLabel.DEER_UNKNOWN })
        add(Filter("Does", null) { it.label == PhotoLabel.DOE })
        add(Filter("Nothing", null) { it.label == PhotoLabel.NO_DEER })
        add(Filter("Unsorted", null) { it.label == PhotoLabel.UNSORTED })
        db.cameras.forEach { c -> add(Filter(c.name, null) { it.cameraId == c.id }) }
    }
    val filter = filters.indexOfFirst { it.label == filterName }.coerceAtLeast(0)
    val f = filters[filter]
    val photos = db.photos.filter(f.test).sortedByDescending { it.epochSec ?: 0 }
    val byDay = photos.groupBy { it.capture?.toLocalDate() }

    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters.size) { i ->
                val on = i == filter
                Row(
                    Modifier.clip(CircleShape).background(if (on) Accent.fern else KairosColors.Surface2)
                        .clickable { filterName = filters[i].label; sel.clear() }.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    filters[i].color?.let { Box(Modifier.size(8.dp).background(it, CircleShape)); Spacer(Modifier.width(6.dp)) }
                    Text(filters[i].label, style = MaterialTheme.typography.labelLarge, color = if (on) Accent.on(Accent.fern) else KairosColors.Text, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            "${photos.size} photo${if (photos.size == 1) "" else "s"}" + if (sel.isEmpty()) "  ·  hold one to sort it" else "",
            style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, modifier = Modifier.padding(horizontal = 20.dp),
        )
        result?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        val nav = com.kairos.ui.LocalAppNav.current
        // Full screen swipes through exactly what's shown here, in this order.
        val order = byDay.flatMap { (_, ps) -> ps.map { it.id } }
        LazyVerticalGrid(
            GridCells.Adaptive(110.dp), Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            byDay.forEach { (day, ps) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h$day") {
                    Text(day?.let { DAY.format(it) } ?: "No date", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp))
                }
                items(ps, key = { it.id }) { p ->
                    val on = p.id in sel
                    Box(
                        Modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).combinedClickable(
                            onClick = { if (sel.isNotEmpty()) { if (on) sel -= p.id else sel += p.id } else nav.openPhotos(order, order.indexOf(p.id)) },
                            onLongClick = { if (sel.isNotEmpty()) { if (on) sel -= p.id else sel += p.id } else sorting = listOf(p.id) },
                        ),
                    ) {
                        PhotoThumb(p.id, Modifier.fillMaxSize())
                        // Who's in it, at a glance: one dot per buck.
                        Row(Modifier.align(Alignment.BottomStart).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            p.buckIds.mapNotNull { db.buck(it) }.forEach { b ->
                                Box(Modifier.size(10.dp).background(buckColor(b.colorIdx), CircleShape).border(1.5.dp, Color.White, CircleShape))
                            }
                        }
                        if (on) {
                            Box(Modifier.fillMaxSize().border(3.dp, Accent.teal, RoundedCornerShape(8.dp)))
                            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(Accent.teal, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, null, tint = Accent.on(Accent.teal), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        if (sel.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(KairosColors.Surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${sel.size} selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { sel.clear() }) { Text("Cancel", color = KairosColors.Dim) }
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier.clip(CircleShape).background(Accent.fern).clickable { sorting = sel.toList() }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("Sort", color = Accent.on(Accent.fern), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier.clip(CircleShape).background(Accent.brick).clickable { confirm = true }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    sorting?.let { ids ->
        SortSheet(
            ids,
            onClose = { sorting = null; if (sel.isNotEmpty() && ids.size > 1) sel.clear() },
            onSelectMore = if (ids.size == 1) ({ sel.clear(); sel += ids.first(); sorting = null }) else null,
        )
    }

    if (confirm) {
        val n = sel.size
        val files = db.photos.filter { it.id in sel }.sumOf { (if (it.srcUri != null) 1 else 0) + it.copyUris.size }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete $n photo${if (n == 1) "" else "s"}?") },
            text = {
                Text(
                    "Removes ${if (n == 1) "it" else "them"} from Kairos and deletes $files file${if (files == 1) "" else "s"} from your phone " +
                        "(the folder ${if (n == 1) "it" else "they"} came from, plus any same-shot copies). This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val failed = BuckStore.deletePhotos(context, sel.toSet(), fromPhone = true)
                    result = "Deleted $n" + if (failed > 0) " · $failed phone file${if (failed == 1) "" else "s"} couldn't be deleted (removed from Kairos only)" else " from Kairos and your phone"
                    sel.clear(); confirm = false
                }) { Text("Delete from phone too", color = KairosColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    BuckStore.deletePhotos(context, sel.toSet(), fromPhone = false)
                    result = "Removed $n from Kairos; phone files kept"
                    sel.clear(); confirm = false
                }) { Text("Kairos only") }
            },
        )
    }
}

