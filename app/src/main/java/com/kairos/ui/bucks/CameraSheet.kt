package com.kairos.ui.bucks

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.kairos.bucks.BuckStore
import com.kairos.bucks.PhotoLabel
import com.kairos.bucks.U
import com.kairos.ui.KairosColors

/**
 * One camera, in a pop-up: its newest photos right there (tap to open, hold to sort), "See all in
 * Gallery" (filtered to this camera), then the setup actions underneath: move the pin, change
 * the angle, rename. Opened by tapping a camera on the map or under Your cameras on Bucks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CameraSheet(
    camId: String,
    onClose: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onGallery: (String) -> Unit,
    onPin: (String) -> Unit,
) {
    val db = BuckStore.db
    val cam = db.camera(camId) ?: run { onClose(); return }
    val photos = db.photos.filter { it.cameraId == camId }.sortedByDescending { it.epochSec ?: 0 }
    var sorting by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var name by remember(camId) { mutableStateOf(cam.name) }

    Takeover(onClose, title = cam.name) {
        val bucks = photos.count { it.label == PhotoLabel.BUCK || it.label == PhotoLabel.DEER_UNKNOWN }
        Text(
            listOfNotNull(
                "${photos.size} photo${if (photos.size == 1) "" else "s"}",
                if (bucks > 0) "$bucks with bucks" else null,
                cam.facingDeg?.let { "faces ${U.compass(it)}" } ?: "not aimed yet",
                photos.firstOrNull()?.let { "newest " + photoWhen(it) },
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim,
        )
        Spacer(Modifier.height(12.dp))
        if (photos.isEmpty()) {
            Text("No photos from this camera yet.", color = KairosColors.Dim)
        } else {
            // Newest first, three across. Each shows who's in it with a dot per buck.
            val nav = com.kairos.ui.LocalAppNav.current
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val w = (maxWidth - 14.dp) / 3 // a hair under, so rounding never wraps the third
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    photos.take(9).forEach { p ->
                        Box(Modifier.width(w).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).tapHold(onTap = { nav.openPhotos(photos.map { it.id }, photos.indexOf(p)) }, onHold = { sorting = p.id })) {
                            PhotoThumb(p.id, Modifier.fillMaxSize())
                            Row(Modifier.align(Alignment.BottomStart).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                p.buckIds.mapNotNull { db.buck(it) }.forEach { b ->
                                    Box(Modifier.size(9.dp).background(buckColor(b.colorIdx), CircleShape).border(1.dp, Color.White, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Tap a photo to open it, hold it to sort it.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
            Spacer(Modifier.height(12.dp))
            PrimaryPill("See all ${photos.size} in Gallery", { onGallery(cam.name) }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(18.dp))
        Text("CAMERA", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryPill(if (cam.lat == null) "Pin it" else "Move pin", { onPin(camId) }, Modifier.weight(1f))
            SecondaryPill("Change angle", { onPin(camId) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        if (!renaming) {
            TextButton(onClick = { renaming = true }) {
                Icon(Icons.Outlined.Edit, null, tint = Accent.teal, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text("Rename", color = Accent.teal)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { BuckStore.saveCamera(cam.copy(name = name.ifBlank { cam.name })); renaming = false }) { Text("Save") }
            }
        }
    }
    sorting?.let { SortSheet(listOf(it), onClose = { sorting = null }) }
}
