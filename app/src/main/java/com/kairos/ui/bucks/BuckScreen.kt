package com.kairos.ui.bucks

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.bucks.Buck
import com.kairos.bucks.BuckAnalysis
import com.kairos.bucks.BuckStore
import com.kairos.engine.bucks.AgeAnswer
import com.kairos.engine.bucks.AgeQ
import com.kairos.engine.bucks.AgeQuiz
import com.kairos.engine.bucks.BuckPlanner
import com.kairos.engine.bucks.BuckReport
import com.kairos.engine.bucks.DeerRoutine
import com.kairos.engine.bucks.HuntWindow
import com.kairos.engine.bucks.HuntWindows
import com.kairos.engine.bucks.HisWinds
import com.kairos.engine.bucks.MentorLink
import com.kairos.engine.bucks.RoutineSources
import com.kairos.engine.bucks.compass8
import com.kairos.learn.LearnContent
import com.kairos.ui.KairosColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DAY = DateTimeFormatter.ofPattern("EEE MMM d")

@Composable
fun BuckScreen(buckId: String, onOpenPhoto: (String) -> Unit, onDeleted: () -> Unit, onReview: (String) -> Unit = {}, onMap: (Long?) -> Unit = {}) {
    val db = BuckStore.db
    val b = db.buck(buckId) ?: return
    val res = BuckAnalysis.state
    val r = res?.reports?.get(buckId)
    val color = buckColor(b.colorIdx)
    val zone = ZoneId.systemDefault()
    val photos = db.photosOf(buckId)
    var sorting by remember { mutableStateOf<String?>(null) }
    sorting?.let { id -> SortSheet(listOf(id), onClose = { sorting = null }) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Hero(b, photos, color, onReview, onOpenPhoto, onSort = { sorting = it }, onMap = { onMap(photos.firstOrNull()?.epochSec) })
            if (r == null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(24.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp))
                    Text("Pulling weather and patterning…", color = KairosColors.Dim)
                }
                return@Column
            }
            GamePlan(r, res.windows[buckId].orEmpty(), zone, color)
            val best = r.cameras.firstOrNull()?.camera
            val n = r.visits.size
            val legalN = r.visits.count { it.f?.legal == true }
            SectionLabel("His data")
            Disclosure("When he shows", if (n == 0) "No visits yet" else if (legalN == 0) "$n visit${if (n == 1) "" else "s"}, all after dark" else "$n visits · $legalN in shooting light", Accent.teal) {
                Text("Each dot is a visit. Gray = dark, outside shooting light. Tap a dot for that moment.", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
                Spacer(Modifier.height(10.dp))
                WhenChart(r, best?.lat, best?.lon, zone, color) { v -> v.photoIds.firstOrNull()?.let(onOpenPhoto) }
            }
            val windFs = r.factors.firstOrNull { it.key == "wind_dir" }
            val seen = windFs?.bins?.filter { it.visits > 0 }.orEmpty()
            val topWind = seen.maxByOrNull { it.visits }
            // "Most on" only when one wind actually leads; a tie (e.g. 1 visit each) isn't a most.
            val windLine = when {
                topWind == null -> "No wind data yet"
                seen.count { it.visits == topWind.visits } > 1 -> "${seen.size} different winds over ${windFs!!.n} visits"
                else -> "Most on ${topWind.label}: ${topWind.visits} of ${windFs!!.n}"
            }
            Disclosure("Wind on his visits", windLine, Accent.teal) {
                Text("Against how often each wind actually blew at his cameras.", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
                Spacer(Modifier.height(10.dp))
                WindRose(windFs, color)
            }
            val marks = r.findings.filter { it.factorKey != "phase" }
            Disclosure(
                "Weather and conditions",
                when {
                    marks.isEmpty() -> "No clear lean yet ($n visit${if (n == 1) "" else "s"})"
                    else -> marks.take(2).joinToString("; ") { "${it.bin.verdict.label}: ${it.bin.label.lowercase()}" }
                },
                Accent.fern,
            ) {
                Text("Every condition: his visits vs the camera's hours at the same times of day. Tap one for the numbers.", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
                Column { (listOfNotNull(r.light) + r.factors.filter { it.key != "wind_dir" }).forEach { FactorBars(it, color) } }
            }
            if (r.weeks.size > 1) Disclosure("Week by week", "${r.weeks.last().visits} visit${if (r.weeks.last().visits == 1) "" else "s"} this week · ${r.weeks.size} weeks since he first showed", Accent.fern) { WeeksChart(r, color) }
            if (r.moves.isNotEmpty()) Disclosure("Camera to camera", r.moves.first().let { m -> "${db.camera(m.fromId)?.name} → ${db.camera(m.toId)?.name}: ${m.count}x" }, Accent.fern) {
                TravelMap(r, color)
                r.moves.forEach { m ->
                    val from = db.camera(m.fromId)?.name; val to = db.camera(m.toId)?.name
                    Text("$from → $to: ${m.count}x, median ${BuckPlanner.fmtMinutes(m.medianGapMin)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            SightingsCard(r, zone, onOpenPhoto) { sorting = it }
            SectionLabel("Know him")
            AgeCard(b)
            MentorCard(r.plan.mentors)
            ResearchCard()
            if (r.plan.gaps.isNotEmpty()) Disclosure("To sharpen this", r.plan.gaps.first(), Accent.amber) { r.plan.gaps.forEach { Bullet(it) } }
            ManageRow(b, onDeleted)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Hero(
    b: Buck,
    photos: List<com.kairos.bucks.Photo>,
    color: Color,
    onReview: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onSort: (String) -> Unit,
    onMap: () -> Unit,
) {
    var rename by remember { mutableStateOf(false) }
    var faceOpen by remember { mutableStateOf(false) }
    if (faceOpen) FaceSheet(b.id, null) { faceOpen = false }
    val nav = com.kairos.ui.LocalAppNav.current
    val db = BuckStore.db
    val best = (b.refPhotoIds.mapNotNull { db.photo(it) } + photos).distinctBy { it.id }.take(12)
    val isMain = db.mainBuck?.id == b.id
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(4.dp))
        if (best.isNotEmpty()) {
            // Tap: his photos full screen, from this one.
            VisitHero(best, db, label = b.name.uppercase(), labelColor = color, onOpen = { id -> nav.openPhotos(best.map { it.id }, best.indexOfFirst { it.id == id }.coerceAtLeast(0)) }, onSort = onSort)
            Spacer(Modifier.height(6.dp))
            Text("Swipe through him · tap to open full screen · hold to move a photo", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
            Spacer(Modifier.height(18.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { faceOpen = true }) { BuckFace(b, 56.dp) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (b.pool) "GROUP" else if (isMain) "YOUR #1 BUCK" else "BUCK", color = if (isMain) Accent.amber else KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(b.name, color = KairosColors.Text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (photos.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Accent.brush(Accent.teal)).clickable(onClick = onMap).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Replay ${if (b.pool) "them" else "him"} on the map", color = Accent.on(Accent.teal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Every visit on a timeline, with the wind moving at your cameras", color = Accent.on(Accent.teal).copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent.on(Accent.teal))
            }
            Spacer(Modifier.height(10.dp))
        }
        ChipRow(
            listOfNotNull(
                if (!b.pool && !isMain) "★  Make him my #1" to { BuckStore.starBuck(b.id) } else null,
                "Re-sort his photos" to { onReview(b.id) },
                if (!b.pool) "Rename" to { rename = true } else null,
                (if (b.face == null) "Set his photo" else "Change his photo") to { faceOpen = true },
            ),
        )
    }
    if (rename) {
        var name by remember { mutableStateOf(b.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Name him") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("e.g. Split Brow") }) },
            confirmButton = { TextButton(onClick = { BuckStore.saveBuck(b.copy(name = name.ifBlank { b.name })); rename = false }) { Text("Save") } },
        )
    }
}

/**
 * The game plan, chart first: the call, his winds against the next sit's wind, two tiles, his
 * best sits ahead (tap one for that hour's full forecast), then the steps. The reasoning and every
 * hour of the forecast are one tap away.
 */
@Composable
private fun GamePlan(r: BuckReport, windows: List<HuntWindow>, zone: ZoneId, color: Color) {
    // Tap a sit: that hour in Forecast, at his best camera (the sits are scored there).
    val nav = com.kairos.ui.LocalAppNav.current
    val camId = r.cameras.firstOrNull()?.camera?.id
    var full by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val course = remember { runCatching { LearnContent.load(context) }.getOrNull() }
    fun agree(ids: List<String>) = ids.firstOrNull()?.let { course?.entries?.get(it) }?.let { "${it.agreement} agree" }
    val p = r.plan
    val bestCam = r.cameras.firstOrNull()
    val legal = r.visits.count { it.f?.legal == true }
    val whenTile = p.window?.let { w -> w.light.label + "s" } ?: if (r.visits.isEmpty()) "-" else "After dark"
    val whenSub = p.window?.text?.substringAfter(": ")?.substringBefore(" (")?.take(60) ?: "$legal of ${r.visits.size} in shooting light"
    val his = remember(r) { HisWinds.of(r) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KairosColors.Surface).padding(18.dp).animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(Accent.fern))
            Spacer(Modifier.width(10.dp))
            Text("Game plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KairosColors.Text, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(p.headline, color = KairosColors.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(if (p.patternReady) Accent.fern else Accent.amber))
            Spacer(Modifier.width(8.dp))
            Text(p.pattern, color = KairosColors.Dim, style = MaterialTheme.typography.bodySmall)
        }

        // His winds vs the next sit.
        if (his.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HisWindDial(his, null, color)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("HIS WINDS", color = Accent.teal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    his.sortedBy { it.t }.forEach { w ->
                        Text(
                            "${com.kairos.bucks.U.wind(w.dir, w.mph)}  ·  ${DAY_SHORT.format(Instant.ofEpochSecond(w.t).atZone(zone))}",
                            color = KairosColors.Text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    val distinct = his.mapNotNull { it.dir?.let(::compass8) }.distinct()
                    if (his.size >= 2) Text(
                        if (distinct.size == his.size) "A different wind each visit: no wind pattern yet." else "${distinct.size} winds over ${his.size} visits.",
                        color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlanTile("WHERE", Accent.fern, bestCam?.camera?.name ?: "-", bestCam?.let { "${it.visits} visit${if (it.visits == 1) "" else "s"} here" }, Modifier.weight(1f))
            PlanTile("WHEN", Accent.teal, whenTile, whenSub, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        NextSitFor(r.buckId)

        Spacer(Modifier.height(14.dp))
        Text(
            if (full) "Hide what to do" else "What to do" + if (p.actions.isNotEmpty()) " (${p.actions.size} steps)" else "",
            color = Accent.teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { full = !full }.padding(vertical = 4.dp),
        )
        if (full && p.actions.isNotEmpty()) {
            p.actions.forEachIndexed { k, step ->
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Accent.soft(Accent.fern)), contentAlignment = Alignment.Center) {
                        Text("${k + 1}", color = Accent.fern, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(step.title, color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(step.detail, color = KairosColors.Dim, style = MaterialTheme.typography.bodySmall)
                        agree(step.ruleIds)?.let { Text("Mentors: $it", color = Accent.teal, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        // The mentors' other view, one line: his call is to go, so it's a heads-up, not the plan.
        if (full) p.patient?.let { st ->
            Spacer(Modifier.height(14.dp))
            Text(
                "Mentors' other view${agree(st.ruleIds)?.let { " ($it)" } ?: ""}: stay out of his core until he shows in daylight. If you go, go light: few sits, clean in and out.",
                color = KairosColors.Faint, style = MaterialTheme.typography.labelSmall,
            )
        }
        if (full) {
            Section("When", p.when_); Section("His conditions", p.conditions); Section("He rarely moves when", p.avoid)
            Section("Where", p.where); Section("Wind", p.wind); Section("Find his bed and food", p.scout); Section("Rhythm", p.rhythm)
        }
    }
}

private val DAY_SHORT = DateTimeFormatter.ofPattern("M/d h:mm a")

/** One sit: rank, day + time, the wind arrow, whether it's his wind and how sure the models are. */
@Composable
private fun SitRow(rank: Int, w: HuntWindow, zone: ZoneId, onTap: () -> Unit) {
    val s0 = Instant.ofEpochSecond(w.start).atZone(zone); val e0 = Instant.ofEpochSecond(w.end).atZone(zone)
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface2).clickable(onClick = onTap).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", color = Accent.fern, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text("${DAY.format(s0)} · ${w.kind.label}", color = KairosColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${com.kairos.bucks.U.clock(s0)}-${com.kairos.bucks.U.clock(e0)}" +
                    listOfNotNull(w.tempF?.let { com.kairos.bucks.U.temp(it) }, w.precipProb?.let { "${it.roundToInt()}% rain" }).joinToString("") { "  ·  $it" },
                color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium,
            )
            Text(
                when {
                    w.hisWind.isNotEmpty() -> "His wind (${w.hisWind.size} of ${w.hisWindOf} visits)"
                    w.check == com.kairos.engine.bucks.ModelCheck.SPLIT -> "Models disagree on the wind: check onX"
                    else -> "Not one of his winds"
                } + (w.mentors.filter { it.plus }.maxByOrNull { it.agree }?.let { " · ${it.label.lowercase()}" } ?: ""),
                color = if (w.hisWind.isNotEmpty()) Accent.fern else KairosColors.Faint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.kairos.ui.WindArrow(w.windDir, 24.dp)
            Text(w.windDir?.let { com.kairos.bucks.U.compass(it) } ?: "Calm", color = KairosColors.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            w.windMph?.let { Text(com.kairos.bucks.U.speed(it), color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun PlanTile(label: String, accent: Color, value: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(KairosColors.Surface2).padding(12.dp)) {
        Text(label, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = KairosColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        sub?.let { Text(it, color = KairosColors.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 2) }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Text(title.uppercase(), color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    lines.forEach { Spacer(Modifier.height(4.dp)); Text(it, color = KairosColors.Text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
internal fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•  ", color = KairosColors.Dim)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Text)
    }
}

/** Yes / no / can't-tell age questions from the QDMA field-judging guide. */
@Composable
private fun AgeCard(b: Buck) {
    val est = AgeQuiz.estimate(b.age)
    Disclosure("How old is he?", est?.label ?: "6 quick questions. Judge the body, not the points (QDMA).", Accent.amber) {
        if (est != null) {
            Text(est.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            if (!est.confident) Text("Answer a couple more to tighten this.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
            est.why.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim) }
            Spacer(Modifier.height(10.dp))
            Text("For that age", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AgeQuiz.tactics(est).forEach { (t, _) -> Bullet(t) }
            Spacer(Modifier.height(12.dp))
        }
        AgeQ.entries.forEach { q ->
            val a = b.age[q]
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(q.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(q.hint, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AgeAnswer.YES to "Yes", AgeAnswer.NO to "No", AgeAnswer.UNSURE to "Can't tell").forEach { (v, l) ->
                        val on = a == v
                        Box(
                            Modifier.clip(CircleShape).background(if (on) Accent.amber else KairosColors.Surface2)
                                .clickable { BuckStore.saveBuck(b.copy(age = if (on) b.age - q else b.age + (q to v))) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text(l, color = if (on) Accent.on(Accent.amber) else KairosColors.Text, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }
}

/** Every visit with its full weather: the raw data. Tap a row for that moment. */
@Composable
private fun SightingsCard(r: BuckReport, zone: ZoneId, onOpenPhoto: (String) -> Unit, onSort: (String) -> Unit) {
    var all by remember { mutableStateOf(false) }
    val db = BuckStore.db
    Disclosure("Every sighting", "${r.visits.size} visits · ${r.photoCount} photos, with the weather for each", Accent.teal) {
        val list = r.visits.reversed().let { if (all) it else it.take(8) }
        list.forEach { v ->
            val z = Instant.ofEpochSecond(v.start).atZone(zone)
            val f = v.f
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .tapHold(onTap = { v.photoIds.firstOrNull()?.let(onOpenPhoto) }, onHold = { v.photoIds.firstOrNull()?.let(onSort) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                v.photoIds.firstOrNull()?.let { PhotoThumb(it, Modifier.size(72.dp, 40.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${DAY.format(z)}  ${com.kairos.bucks.U.clock(z)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            db.camera(v.cameraId)?.name, f?.light?.label,
                            f?.let { windText(it.windDir, it.windMph) },
                            f?.tempF?.let { com.kairos.bucks.U.temp(it) },
                            f?.pressTrend3h?.let { "press ${com.kairos.bucks.U.pressureDelta(it)}" },
                            f?.tempDay24?.let { "24h ${if (it >= 0) "+" else "−"}${com.kairos.bucks.U.tempDelta(kotlin.math.abs(it))}" },
                            f?.departureF?.let { "${if (it >= 0) "+" else "−"}${com.kairos.bucks.U.tempDelta(kotlin.math.abs(it))} vs normal" },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim,
                    )
                }
            }
        }
        if (r.visits.size > 8) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${r.visits.size}") }
    }
}

@Composable
private fun MentorCard(links: List<MentorLink>) {
    val context = LocalContext.current
    val course = remember { runCatching { LearnContent.load(context) }.getOrNull() } ?: return
    Disclosure("What the mentors say", "${links.size} rules from your videos match his data", Accent.fern) {
        links.forEach { l ->
            val e = course.entries[l.ruleId] ?: return@forEach
            var open by remember(l.ruleId) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp).animateContentSize()) {
                Text(l.because, style = MaterialTheme.typography.labelMedium, color = if (l.counterpoint) Accent.amber else Accent.teal, fontWeight = FontWeight.SemiBold)
                Text(e.action, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 3)
                Text("${e.agreement} agree · ${e.people.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim)
                if (open) {
                    Spacer(Modifier.height(4.dp))
                    Text(e.why, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
                    e.videos.forEach { vid ->
                        val title = course.videos[vid]?.title ?: vid
                        Text(
                            "▶ $title", style = MaterialTheme.typography.labelSmall, color = KairosColors.Water,
                            modifier = Modifier.padding(top = 4.dp).clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$vid")))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchCard() {
    val context = LocalContext.current
    Disclosure("A deer's day", "GPS-collar research on when deer move, bed and feed", Accent.teal) {
        DeerRoutine.daySummary.forEach { Bullet(it) }
        Spacer(Modifier.height(8.dp))
        RoutineSources.all.forEach { s ->
            Text(
                s.title, style = MaterialTheme.typography.labelSmall, color = KairosColors.Water,
                modifier = Modifier.padding(vertical = 2.dp).clickable { s.url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
            )
        }
    }
}

@Composable
private fun ManageRow(b: Buck, onDeleted: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick = { confirm = true }) { Text("Delete ${b.name}", color = KairosColors.Error) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete ${b.name}?") },
        text = { Text("His photos go back to Sort; nothing is deleted from your phone.") },
        confirmButton = { TextButton(onClick = { BuckStore.deleteBuck(b.id); confirm = false; onDeleted() }) { Text("Delete", color = KairosColors.Error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

