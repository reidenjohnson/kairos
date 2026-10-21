package com.kairos.ui.bucks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.bucks.Buck
import com.kairos.bucks.BuckDb
import com.kairos.bucks.BuckStore
import com.kairos.bucks.Photo
import com.kairos.bucks.PhotoLabel
import com.kairos.ui.KairosColors

/** The "Not sure" pile (visits answered "Can't tell") is reviewed like a group under this id. */
const val UNSURE_ID = "__unsure"

/** Does photo [p] belong to the buck/group/pile [setId]? */
internal fun inSet(p: Photo, setId: String) = if (setId == UNSURE_ID) p.label == PhotoLabel.DEER_UNKNOWN else setId in p.buckIds

/** A buck's, group's or pile's photos, as visits: same camera within 30 minutes, oldest first. */
internal fun visitsOf(db: BuckDb, setId: String): List<List<Photo>> {
    val out = ArrayList<MutableList<Photo>>()
    for (p in db.photos.filter { inSet(it, setId) && it.epochSec != null }.sortedBy { it.epochSec }) {
        val last = out.lastOrNull()?.last()
        if (last != null && last.cameraId == p.cameraId && p.epochSec!! - last.epochSec!! <= com.kairos.bucks.BuckPrefs.visitGapSec) out.last() += p else out += mutableListOf(p)
    }
    return out
}

/** Where a visit can go. Same choices everywhere a visit is moved. */
@Composable
internal fun MoveChoices(current: Buck?, ids: Set<String>, onMoved: (String) -> Unit) {
    val db = BuckStore.db
    var compare by remember(ids) { mutableStateOf<Buck?>(null) }
    val others = db.bucks.filter { !it.pool && it.id != current?.id }
    compare?.let { b ->
        val refs = (b.refPhotoIds + db.photosOf(b.id).map { it.id }).distinct().filter { it !in ids }.mapNotNull { db.photo(it) }.take(8)
        if (refs.isNotEmpty()) { VisitHero(refs, db, label = b.name.uppercase(), labelColor = buckColor(b.colorIdx)); Spacer(Modifier.height(14.dp)) }
        YesNo(yes = "Move to ${b.name}", no = "Back", onYes = { tag(ids, PhotoLabel.BUCK, b); onMoved(b.name) }, onNo = { compare = null })
        return
    }
    ChipRow(
        others.map { b -> b.name to { compare = b } } +
            listOfNotNull(
                if (current?.pool != true) "Young bucks" to { val p = BuckStore.pool(); tag(ids, PhotoLabel.BUCK, p); onMoved("Young bucks") } else null,
                "New buck" to { val b = BuckStore.addBuck(); tag(ids, PhotoLabel.BUCK, b); onMoved(b.name) },
                "Doe or fawn" to { tag(ids, PhotoLabel.DOE, null); onMoved("Doe or fawn") },
                "Nothing" to { tag(ids, PhotoLabel.NO_DEER, null); onMoved("Nothing") },
                "Back to Sort" to { BuckStore.editPhotos(ids) { it.copy(label = PhotoLabel.UNSORTED, buckIds = emptyList()) }; onMoved("Sort") },
            ),
    )
}

/**
 * Re-sort a buck or group one visit at a time: "Still Young bucks?" Keep, or move it. Built for
 * the Young bucks group, so a small buck that grows on you can be pulled out later.
 */
@Composable
fun ReviewScreen(buckId: String, onDone: () -> Unit) {
    val db = BuckStore.db
    val b = db.buck(buckId)
    val name = b?.name ?: if (buckId == UNSURE_ID) "Not sure" else return
    // Snapshot the visit list once, so moving one doesn't reshuffle the rest under him.
    val visits = remember(buckId) { visitsOf(db, buckId).map { v -> v.map { it.id } } }
    var i by remember(buckId) { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var moved by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(4.dp))
        Text("Re-sort $name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (visits.isNotEmpty() && i < visits.size) {
            Text("Visit ${i + 1} of ${visits.size}", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(CircleShape).background(KairosColors.Surface2)) {
                Box(Modifier.fillMaxWidth(i.toFloat() / visits.size).height(4.dp).clip(CircleShape).background(Accent.teal))
            }
        }
        note?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim) }
        Spacer(Modifier.height(12.dp))

        val ids = visits.getOrNull(i)
        val photos = ids?.mapNotNull { BuckStore.db.photo(it) }?.filter { inSet(it, buckId) }.orEmpty()
        if (ids == null) {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).background(Accent.brush(Accent.fern), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Accent.on(Accent.fern), modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text("Done.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("$moved visit${if (moved == 1) "" else "s"} moved.", color = KairosColors.Dim)
                Spacer(Modifier.height(20.dp))
                PrimaryPill("Done", onDone, Modifier.fillMaxWidth())
            }
            return@Column
        }
        if (photos.isEmpty()) { i++; return@Column } // already moved elsewhere
        VisitHero(photos, BuckStore.db, label = "THIS VISIT")
        if (!moving) {
            Ask(if (b == null) "Can you tell now?" else "Still $name?", if (photos.size > 1) "Swipe through all ${photos.size} photos first." else null)
            YesNo(yes = if (b == null) "Still not sure" else "Keep", no = if (b == null) "Yes, sort it" else "Move it", onYes = { i++; note = null }, onNo = { moving = true })
        } else {
            Ask("Move this visit to…")
            MoveChoices(b, photos.map { it.id }.toSet()) { where -> moved++; note = "Moved to $where."; moving = false; i++ }
            TextButton(onClick = { moving = false }) { Text("Cancel", color = KairosColors.Dim) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (i > 0) TextButton(onClick = { i--; moving = false }) { Text("Previous", color = KairosColors.Dim) } else Spacer(Modifier.size(1.dp))
            TextButton(onClick = onDone) { Text("Stop here", color = KairosColors.Dim) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
