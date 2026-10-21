package com.kairos.ui.bucks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.bucks.BuckPrefs
import com.kairos.bucks.BuckStore
import com.kairos.bucks.PhotoImporter
import com.kairos.ui.KairosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bucks settings: units, how visits group, the rut, re-reading strips, and starting over. */
@Composable
fun BuckSettingsScreen(onStartedOver: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { BuckPrefs.load(context) }
    var reading by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Group("Units") { UnitRows() }

        Group("Visits") {
            Text(
                "Photos at one camera this close together count as one visit, and are sorted with one answer. " +
                    "Shorter keeps two deer that cross minutes apart separate.",
                style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim,
            )
            Spacer(Modifier.height(8.dp))
            val opts = listOf(3, 5, 10, 15, 30)
            Seg(opts.map { "$it min" }, opts.indexOf(BuckPrefs.visitMin).coerceAtLeast(0)) { BuckPrefs.pickVisitMin(opts[it]) }
        }

        Group("Season") {
            Text("Your rut dates drive the season phase on every photo and plan.", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
            Spacer(Modifier.height(8.dp))
            RutRow(BuckStore.db.rut)
        }

        Group("Photos") {
            Text(
                "Read the date, time and temperature off every photo's strip again. Values you fixed by hand, and times from the photo's own data, are never changed.",
                style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryPill("Read every strip again", {
                if (reading?.startsWith("Reading") == true) return@SecondaryPill
                reading = "Reading…"
                scope.launch {
                    val r = withContext(Dispatchers.IO) { PhotoImporter.rereadAll(context) { p -> reading = "Reading ${p.done} of ${p.total}…" } }
                    reading = "Done: ${r.checked} photos, ${r.timesFixed} times and ${r.tempsFixed} temps corrected" + if (r.flagged > 0) ", ${r.flagged} to check in Sort." else "."
                }
            }, Modifier.fillMaxWidth())
            reading?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim) }
        }

        Group("Help") {
            SecondaryPill("Show the walkthrough again", { BucksIntroPrefs.reset(context); onStartedOver() }, Modifier.fillMaxWidth())
        }

        Group("Start over") {
            Text("Clears every photo, buck, camera and answer in Bucks. Photos on your phone aren't touched.", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmReset = true }) { Text("Start over", color = KairosColors.Error) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Start over?") },
        text = { Text("Clears every photo, buck, camera and answer in Bucks. The photos on your phone aren't touched; import them again anytime.") },
        confirmButton = { TextButton(onClick = { BuckStore.reset(); confirmReset = false; onStartedOver() }) { Text("Start over", color = KairosColors.Error) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KairosColors.Surface).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
