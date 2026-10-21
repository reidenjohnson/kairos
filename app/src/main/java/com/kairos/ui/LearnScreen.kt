package com.kairos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.learn.Course
import com.kairos.learn.LearnContent
import com.kairos.learn.LearnProgress
import com.kairos.learn.LearnUnit
import com.kairos.learn.Lesson
import com.kairos.learn.Section

private val LEVELS = listOf("beginner" to "Beginner", "intermediate" to "Intermediate", "pro" to "Pro")

/**
 * Learn home: the whitetail course from beginner to pro. A "Continue" card points at the
 * first lesson not yet checked off; each unit shows its progress and expands to its lessons.
 * Lessons are never locked, so a hunter can jump straight to what they need.
 */
@Composable
fun LearnScreen(onOpenLesson: (String) -> Unit) {
    val context = LocalContext.current
    val course = remember { LearnContent.load(context) }
    LaunchedEffect(Unit) { LearnProgress.ensureLoaded(context) }
    val done = LearnProgress.done
    val all = course.lessons
    val next = all.firstOrNull { it.id !in done }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Spacer(Modifier.height(Space.xs)) }
        item {
            Column {
                Overline("Learn · Whitetail", color = KairosColors.Pine)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Hunt deer, start to finish",
                    fontFamily = Bricolage,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp,
                    lineHeight = 34.sp,
                    color = KairosColors.Text,
                )
                Spacer(Modifier.height(Space.sm))
                val count = all.count { it.id in done }
                Text(
                    "$count of ${all.size} lessons done · about 5 minutes each",
                    style = MaterialTheme.typography.bodySmall,
                    color = KairosColors.Faint,
                )
                Spacer(Modifier.height(Space.sm))
                ProgressBar(count, all.size)
            }
        }
        if (next != null) {
            item { ContinueCard(course, next) { onOpenLesson(next.id) } }
        }
        LEVELS.forEach { (key, label) ->
            val units = course.units.filter { it.level == key }
            if (units.isEmpty()) return@forEach
            item(key = "level-$key") {
                Overline(label, modifier = Modifier.padding(top = Space.lg))
            }
            units.forEach { u ->
                item(key = "unit-${u.name}") {
                    UnitCard(u, done, expandedByDefault = next != null && u.lessons.any { it.id == next.id }, onOpenLesson)
                }
            }
        }
        item {
            Text(
                "Every lesson comes from hunters on YouTube. Where several of them teach the same thing, the lesson says so. Links to the videos are at the bottom of each lesson.",
                style = MaterialTheme.typography.labelSmall,
                color = KairosColors.Faint,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = Space.lg),
            )
        }
        item { Spacer(Modifier.height(Space.lg)) }
    }
}

@Composable
private fun ContinueCard(course: Course, lesson: Lesson, onClick: () -> Unit) {
    val unit = course.unitOf(lesson.id)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(KairosColors.SegTop, KairosColors.SegBottom)))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Overline("Up next", color = KairosColors.OnSeg)
        Spacer(Modifier.height(6.dp))
        Text(lesson.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KairosColors.OnSeg)
        Spacer(Modifier.height(2.dp))
        Text(
            "${unit?.name.orEmpty()} · ${lesson.minutes} min",
            style = MaterialTheme.typography.bodySmall,
            color = KairosColors.OnSeg.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun UnitCard(unit: LearnUnit, done: Set<String>, expandedByDefault: Boolean, onOpenLesson: (String) -> Unit) {
    var expanded by rememberSaveable(unit.name) { mutableStateOf(expandedByDefault) }
    val count = unit.lessons.count { it.id in done }
    val complete = count == unit.lessons.size

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(unit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text)
                if (unit.goal.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(unit.goal, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ProgressBar(count, unit.lessons.size) }
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        if (complete) "Done" else "$count/${unit.lessons.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (complete) KairosColors.Pine else KairosColors.Faint,
                    )
                }
            }
            Spacer(Modifier.width(Space.sm))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide lessons" else "Show lessons",
                tint = KairosColors.Faint,
            )
        }
        if (expanded) {
            unit.lessons.forEach { l ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLesson(l.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DoneDot(l.id in done)
                    Spacer(Modifier.width(Space.md))
                    Text(
                        l.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (l.id in done) KairosColors.Dim else KairosColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${l.minutes} min", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
                }
            }
            Spacer(Modifier.height(Space.sm))
        }
    }
}

/**
 * One lesson: the hook, its key points, each section with a "Read more" that opens the
 * consensus entries it was written from, a try-this, a heads-up where sources differ, a
 * quick self-check, and the videos it came from. Ends with check-off and next lesson.
 */
@Composable
fun LessonScreen(lessonId: String, onOpenLesson: (String) -> Unit) {
    val context = LocalContext.current
    val course = remember { LearnContent.load(context) }
    LaunchedEffect(Unit) { LearnProgress.ensureLoaded(context) }
    val lesson = course.lesson(lessonId) ?: return
    val unit = course.unitOf(lessonId)
    val all = course.lessons
    val next = all.getOrNull(all.indexOfFirst { it.id == lessonId } + 1)
    val isDone = lessonId in LearnProgress.done
    val uriHandler = LocalUriHandler.current
    // A new lesson opened from "Next" should start at the top, not keep the old scroll.
    val listState = remember(lessonId) { androidx.compose.foundation.lazy.LazyListState() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Spacer(Modifier.height(Space.xs)) }
        item {
            Column {
                Overline(
                    "${unit?.name.orEmpty()} · ${LEVELS.firstOrNull { it.first == unit?.level }?.second.orEmpty()}",
                    color = KairosColors.Pine,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    lesson.title,
                    fontFamily = Bricolage,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    lineHeight = 30.sp,
                    color = KairosColors.Text,
                )
                Spacer(Modifier.height(Space.xs))
                Text("${lesson.minutes} min read", style = MaterialTheme.typography.bodySmall, color = KairosColors.Faint)
            }
        }
        if (lesson.hook.isNotEmpty()) {
            item { Body(lesson.hook, italic = true) }
        }
        item {
            Card {
                Overline("Key points")
                Spacer(Modifier.height(Space.sm))
                lesson.sections.forEach { s ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = KairosColors.Pine, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(Space.sm))
                        Text(s.heading, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = KairosColors.Text)
                    }
                }
            }
        }
        lesson.sections.forEachIndexed { i, s ->
            item(key = "s$i-$lessonId") { SectionBlock(course, s) }
        }
        if (lesson.tryThis.isNotEmpty()) {
            item { Callout("Try this", lesson.tryThis, KairosColors.Pine) }
        }
        if (lesson.headsUp.isNotEmpty()) {
            item { Callout("Heads up", lesson.headsUp, KairosColors.Fair) }
        }
        if (lesson.check.isNotEmpty()) {
            item { Overline("Check yourself", modifier = Modifier.padding(top = Space.sm)) }
            lesson.check.forEachIndexed { i, c ->
                item(key = "c$i-$lessonId") { CheckCard(c.q, c.a) }
            }
        }
        val refs = course.videosFor(lesson)
        if (refs.isNotEmpty()) {
            item {
                Card {
                    Overline("References")
                    Spacer(Modifier.height(Space.sm))
                    refs.forEach { (id, v) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri(v.url(id)) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text("${v.title} ↗", style = MaterialTheme.typography.bodySmall, color = KairosColors.Water, lineHeight = 18.sp)
                            if (v.channel.isNotEmpty()) {
                                Text(v.channel, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PillButton(
                    if (isDone) "Done ✓  (tap to undo)" else "Mark as done",
                    filled = !isDone,
                    modifier = Modifier.weight(1f),
                ) { LearnProgress.setDone(context, lessonId, !isDone) }
                if (next != null) {
                    PillButton("Next lesson →", filled = isDone, modifier = Modifier.weight(1f)) {
                        if (!isDone) LearnProgress.setDone(context, lessonId, true)
                        onOpenLesson(next.id)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Space.xl)) }
    }
}

@Composable
private fun SectionBlock(course: Course, section: Section) {
    var open by rememberSaveable(section.heading) { mutableStateOf(false) }
    val entries = section.from.mapNotNull { course.entries[it] }
    Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
        Text(section.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text)
        Spacer(Modifier.height(6.dp))
        Body(section.text)
        if (entries.isNotEmpty()) {
            Text(
                if (open) "Hide details" else "Read more",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = KairosColors.Water,
                modifier = Modifier.clickable { open = !open }.padding(vertical = 8.dp),
            )
            if (open) {
                entries.forEach { e ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = Space.sm)
                            .clip(RoundedCornerShape(12.dp))
                            .background(KairosColors.Surface2)
                            .padding(12.dp),
                    ) {
                        Text(e.action, style = MaterialTheme.typography.bodySmall, color = KairosColors.Text, lineHeight = 19.sp)
                        if (e.why.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Why: ${e.why}", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, lineHeight = 19.sp)
                        }
                        if (e.people.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            val who = if (e.people.size == 1) "Taught by" else "${e.people.size} sources agree:"
                            Text(
                                "$who ${e.people.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = KairosColors.Faint,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckCard(q: String, a: String) {
    var shown by rememberSaveable(q) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .clickable { shown = !shown }
            .padding(16.dp),
    ) {
        Text(q, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = KairosColors.Text, lineHeight = 20.sp)
        Spacer(Modifier.height(Space.sm))
        if (shown) {
            Text(a, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Pine, lineHeight = 20.sp)
        } else {
            Text("Think it through, then tap to see the answer", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
        }
    }
}

@Composable
private fun Callout(label: String, text: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(16.dp),
    ) {
        Column {
            Overline(label, color = accent)
            Spacer(Modifier.height(6.dp))
            Body(text)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Body(text: String, italic: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        color = if (italic) KairosColors.Dim else KairosColors.Text,
        lineHeight = 24.sp,
    )
}

@Composable
private fun ProgressBar(value: Int, total: Int) {
    val frac = if (total == 0) 0f else value.toFloat() / total
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(KairosColors.Line),
    ) {
        if (frac > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(KairosColors.Pine),
            )
        }
    }
}

@Composable
private fun DoneDot(done: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (done) Modifier.background(KairosColors.Pine)
                else Modifier.border(1.5.dp, KairosColors.Faint, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Filled.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(KairosColors.Pine)
                else Modifier.border(1.dp, KairosColors.Line, RoundedCornerShape(999.dp)).background(KairosColors.Surface),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else KairosColors.Text,
        )
    }
}
