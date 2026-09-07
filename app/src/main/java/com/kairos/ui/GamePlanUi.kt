package com.kairos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.advice.GamePlan
import com.kairos.advice.buildGamePlan
import com.kairos.advice.buildSidePlan
import com.kairos.data.Forecast
import com.kairos.engine.SPECIES
import com.kairos.engine.Side
import java.time.LocalDate

/**
 * The compact **teaser** card: three lines — what to do ([GamePlan.headline]), what to
 * throw / how ([GamePlan.tacticLine]), and a quick why ([GamePlan.whyBrief]). The whole
 * card is tappable and opens the full [GamePlanScreen]. Used on the Today side tabs and
 * on the species detail screen.
 */
@Composable
internal fun GamePlanTeaser(plan: GamePlan, side: Side, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), clip = false, spotColor = KairosColors.ShadowSpot, ambientColor = KairosColors.ShadowSpot)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(KairosColors.CardTop, KairosColors.CardBottom)))
            .border(1.dp, KairosColors.CardBorder, RoundedCornerShape(20.dp))
            .clickable { onOpen() }
            .padding(18.dp),
    ) {
        PhaseChip(plan.phaseLabel)
        Spacer(Modifier.height(10.dp))
        Text(plan.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text, lineHeight = 24.sp)
        Spacer(Modifier.height(10.dp))
        LabeledLine(if (side == Side.FISH) "THROW" else "HOW", plan.tacticLine)
        Spacer(Modifier.height(8.dp))
        LabeledLine("WHY", plan.whyBrief)
        Spacer(Modifier.height(14.dp))
        Text(
            "Read the full plan  ›",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = KairosColors.Water,
        )
    }
}

@Composable
private fun LabeledLine(label: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = KairosColors.Water,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 2.dp).width(46.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim, lineHeight = 20.sp)
    }
}

@Composable
private fun PhaseChip(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(KairosColors.Water.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = KairosColors.Water, letterSpacing = 0.6.sp)
    }
}

/**
 * The full plan page: the headline, then every section (Where / When / How / Why) with
 * its do-this line and the longer detail — the "all the info" view the teaser opens.
 */
@Composable
fun GamePlanScreen(state: UiState, speciesName: String?, side: Side?) {
    val ready = state as? UiState.Ready
    if (ready == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Loading conditions…", color = KairosColors.Dim) }
        return
    }
    val c = ready.forecast.conditions
    val precip = ready.forecast.precipMmHr
    val sp = speciesName?.let { name -> SPECIES.firstOrNull { it.name == name } ?: return }
    val plan = if (sp != null) {
        buildGamePlan(sp, c, LocalDate.now(), ready.forecast.timing, precip)
    } else {
        buildSidePlan(side ?: Side.FISH, c, LocalDate.now(), ready.forecast.timing, precip)
    }
    val planSide = sp?.side ?: side ?: Side.FISH

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        PhaseChip(plan.phaseLabel)
        Spacer(Modifier.height(12.dp))
        Text(
            plan.headline,
            fontFamily = Bricolage,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            lineHeight = 28.sp,
            color = KairosColors.Text,
        )
        Spacer(Modifier.height(16.dp))
        WeatherWindowCallout(ready.forecast, planSide)
        plan.sections.forEach { s ->
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .height(14.dp)
                        .width(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(KairosColors.Water),
                )
                Spacer(Modifier.width(8.dp))
                Text(s.label.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(s.brief, style = MaterialTheme.typography.bodyLarge, color = KairosColors.Text, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp)
            if (s.more.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(s.more, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim, lineHeight = 21.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Guidance from the season and today's conditions — not a guarantee.",
            style = MaterialTheme.typography.labelSmall,
            color = KairosColors.Faint,
        )
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The concrete "This window" read — real hours, not vague advice: the best windows today,
 * when a front arrives and to be out ahead of it, the slow stretch, and how tomorrow looks.
 * Built from the live forecast timing so the plan tells you exactly when to go.
 */
@Composable
private fun WeatherWindowCallout(f: Forecast, side: Side) {
    val lines = weatherWindowLines(f, side)
    if (lines.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Overline("This window", color = KairosColors.Water)
        Spacer(Modifier.height(10.dp))
        lines.forEachIndexed { i, line ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .width(5.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(KairosColors.Water),
                )
                Spacer(Modifier.width(10.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Text, lineHeight = 20.sp)
            }
        }
    }
}

/** Concrete timing lines for [side] from the live forecast (best windows, front, tomorrow). */
private fun weatherWindowLines(f: Forecast, side: Side): List<String> {
    val out = mutableListOf<String>()
    val t = f.timing
    if (t != null) {
        val wins = t.bestWindows(side)
        if (wins.isNotEmpty()) {
            out += "Best today: " + wins.joinToString(" & ") { "${hr12(it.first)}–${hr12(it.last + 1)}" } + "."
        }
        val srH = t.sunriseHour.toInt()
        val ssH = t.sunsetHour.toInt()
        val slow = t.hours.filter { it.hour in srH..ssH }
            .minByOrNull { if (side == Side.FISH) it.fishScore else it.huntScore }
        if (slow != null) out += "Slowest around ${hr12(slow.hour)} — no need to force the midday."
    }
    f.frontArrivalHour?.let { h ->
        out += "A cold front pushes in around ${hr12(h)} — try to be out ahead of it; the bite usually backs off once it's through."
    }
    val tomorrow = f.weekTiming.getOrNull(1)
    if (tomorrow != null && t != null) {
        val todayScore = t.scoreForSide(side)
        val tScore = tomorrow.scoreForSide(side)
        val win = tomorrow.bestWindows(side).firstOrNull()?.let { " — peaks ${hr12(it.first)}–${hr12(it.last + 1)}" } ?: ""
        val verdict = when {
            tScore >= todayScore + 6 -> "a better day ($tScore)"
            tScore <= todayScore - 6 -> "a step down ($tScore)"
            else -> "about the same ($tScore)"
        }
        out += "Tomorrow looks $verdict$win."
    }
    return out
}

private fun hr12(h24: Int): String {
    val h = ((h24 % 24) + 24) % 24
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 $ampm"
}
