package com.kairos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineRegion

/**
 * Manage the map areas saved for offline use — see what's downloaded and delete what you
 * no longer need. Downloads are started from the map ("Save this area for offline").
 */
@Composable
fun OfflineMapsScreen() {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) }
    var regions by remember { mutableStateOf<List<Pair<OfflineRegion, String>>?>(null) }
    var reload by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(reload) {
        OfflineMaps.list(context) { regions = it }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Areas you've saved to use with no signal. Download new areas from the map.",
            style = MaterialTheme.typography.bodyMedium,
            color = KairosColors.Dim,
        )
        Spacer(Modifier.height(16.dp))

        val list = regions
        when {
            list == null -> Text("Loading…", color = KairosColors.Faint)
            list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
                Text(
                    "No saved areas yet. Open the Map, pan to where you hunt or fish, and tap the download button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KairosColors.Faint,
                )
            }
            else -> LazyColumn {
                itemsIndexed(list) { _, (region, name) ->
                    RegionRow(name = name, onDelete = { OfflineMaps.delete(region) { reload++ } })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun RegionRow(name: String, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = KairosColors.Surface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = KairosColors.Text)
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete saved area", tint = KairosColors.Dim)
            }
        }
    }
}
