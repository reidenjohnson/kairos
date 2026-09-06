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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.engine.DeadlineEvent
import com.kairos.engine.DeadlineKind
import com.kairos.engine.DeadlinePhase
import com.kairos.engine.LicenseDeadline
import com.kairos.engine.MAINE_DEADLINES
import com.kairos.engine.deadlineStatus
import java.time.LocalDate

/**
 * The "Licenses & lotteries" screen: Maine's permit-lottery application windows
 * (moose, antlerless deer) so you apply before they close. Every date traces to an
 * official IF&W page. Dates shift year to year, so a passed window honestly points
 * to the next cycle rather than guessing unposted dates (see Deadlines.kt).
 */
@Composable
fun DeadlinesScreen() {
    val today = LocalDate.now()
    // Surface the most urgent first: open-to-apply, then upcoming, then passed.
    val sorted = MAINE_DEADLINES.sortedBy { deadlineStatus(it, today).phase.ordinal }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Spacer(Modifier.height(Space.xs)) }
        item { Header() }
        sorted.forEach { d -> item(key = d.name) { DeadlineCard(d) } }
        item { Footer() }
        item { Spacer(Modifier.height(Space.lg)) }
    }
}

@Composable
private fun Header() {
    Column {
        Overline("Licenses & lotteries", color = KairosColors.Water)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Application deadlines",
            fontFamily = Bricolage,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            lineHeight = 34.sp,
            color = KairosColors.Text,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Apply before these windows close — dates from Maine IF&W",
            style = MaterialTheme.typography.bodySmall,
            color = KairosColors.Faint,
        )
    }
}

@Composable
private fun DeadlineCard(d: LicenseDeadline) {
    val today = LocalDate.now()
    val status = deadlineStatus(d, today)
    val uriHandler = LocalUriHandler.current
    val accent = phaseColor(status.phase)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                d.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                lineHeight = 22.sp,
            )
            PhaseChip(status.phase)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            status.headline(d.cycleYear, d.typicalWindow),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(d.summary, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, lineHeight = 18.sp)

        Spacer(Modifier.height(16.dp))
        Text(
            "KEY DATES · ${d.cycleYear}",
            style = MaterialTheme.typography.labelSmall,
            color = KairosColors.Faint,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(8.dp))
        d.events.forEach { EventRow(it, today, isDeadline = it.kind == DeadlineKind.APPLICATION_DEADLINE) }

        Spacer(Modifier.height(12.dp))
        Text(
            "Dates shift each year (${d.typicalWindow}). Confirm the current cycle on IF&W before you rely on them.",
            style = MaterialTheme.typography.labelSmall,
            color = KairosColors.Faint,
            lineHeight = 15.sp,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Apply / details — ${d.sourceLabel} ↗",
            style = MaterialTheme.typography.bodySmall,
            color = KairosColors.Water,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(d.sourceUrl) }
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun EventRow(e: DeadlineEvent, today: LocalDate, isDeadline: Boolean) {
    val past = e.date.isBefore(today)
    val dotColor = when {
        isDeadline -> KairosColors.Fair
        past -> KairosColors.Faint.copy(alpha = 0.5f)
        else -> KairosColors.Water
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dotColor),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            monthDay(e.date),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isDeadline) FontWeight.Bold else FontWeight.Normal,
            color = if (past) KairosColors.Faint else KairosColors.Text,
            modifier = Modifier.width(64.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                e.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isDeadline) FontWeight.Bold else FontWeight.SemiBold,
                color = if (past) KairosColors.Dim else KairosColors.Text,
            )
            if (e.note.isNotEmpty()) {
                Text(e.note, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
            }
        }
    }
}

@Composable
private fun PhaseChip(phase: DeadlinePhase) {
    val (label, color) = when (phase) {
        DeadlinePhase.OPEN -> "Apply now" to KairosColors.Fair
        DeadlinePhase.UPCOMING -> "Upcoming" to KairosColors.Water
        DeadlinePhase.PASSED -> "Closed" to KairosColors.Faint
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun phaseColor(phase: DeadlinePhase) = when (phase) {
    DeadlinePhase.OPEN -> KairosColors.Fair
    DeadlinePhase.UPCOMING -> KairosColors.Water
    DeadlinePhase.PASSED -> KairosColors.Dim
}

@Composable
private fun Footer() {
    Text(
        "These are draw-by-lottery permits — you must apply months ahead. Turn on notifications " +
            "to get a reminder before each window closes. Always confirm current dates and rules with Maine IF&W.",
        style = MaterialTheme.typography.labelSmall,
        color = KairosColors.Faint,
        lineHeight = 15.sp,
    )
}
