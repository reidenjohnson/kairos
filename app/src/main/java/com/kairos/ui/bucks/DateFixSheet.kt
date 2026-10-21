package com.kairos.ui.bucks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.bucks.BuckAnalysis
import com.kairos.bucks.BuckStore
import com.kairos.bucks.TimeSource
import com.kairos.bucks.U
import com.kairos.ui.KairosColors
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DATE = DateTimeFormatter.ofPattern("EEE MMM d")

/**
 * Photos whose strip date doesn't fit their temperature. For each: the strip itself, big, so he
 * can read it; what was read and why it doesn't fit (NOAA's temperature at the camera then); the
 * dates one digit away that do fit, best first. Pick one, or keep the read. Either way it's saved
 * as his (hand-set times are never overwritten).
 */
@Composable
internal fun DateFixSheet(onClose: () -> Unit) {
    val fixes = BuckAnalysis.state?.dateFixes.orEmpty()
    val db = BuckStore.db
    Takeover(onClose, title = "Check these dates") {
        if (fixes.isEmpty()) { Text("All set.", color = KairosColors.Dim); return@Takeover }
        Text(
            "The strip prints the camera's temperature. On your photos it's within about 2°F of NOAA's at the camera (6°F at most). These are far off for the date that was read.",
            color = KairosColors.Dim, style = MaterialTheme.typography.bodySmall,
        )
        fixes.forEach { (id, f) ->
            val p = db.photo(id) ?: return@forEach
            Spacer(Modifier.height(16.dp))
            // The strip, big: the bottom of the photo.
            Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(KairosColors.Surface2)) {
                PhotoThumb(id, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter, full = true)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Read as ${DATE.format(f.read)}, ${U.clock(f.read)} · strip ${U.temp(f.stripF.toDouble())}",
                color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            )
            Text(
                "NOAA had ${f.modelAt[f.read]?.let { U.temp(it) } ?: "?"} at ${db.camera(p.cameraId)?.name ?: "the camera"} then.",
                color = KairosColors.Dim, style = MaterialTheme.typography.bodySmall,
            )
            f.options.take(3).forEach { o ->
                Column(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Accent.soft(Accent.fern))
                        .clickable { BuckStore.editPhotos(setOf(id)) { it.copy(capture = o, timeSource = TimeSource.MANUAL, timeCheck = false) } }
                        .padding(12.dp),
                ) {
                    Text("It's ${DATE.format(o)}", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            f.modelAt[o]?.let { "NOAA ${U.temp(it)} then" },
                            if (o == f.twin) "same as the photo taken with it" else null,
                        ).joinToString("  ·  "),
                        color = Accent.fern, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                "Keep ${DATE.format(f.read)}", color = KairosColors.Dim, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                    .clickable { BuckStore.editPhotos(setOf(id)) { it.copy(timeSource = TimeSource.MANUAL, timeCheck = false) } }.padding(8.dp),
            )
        }
    }
}
