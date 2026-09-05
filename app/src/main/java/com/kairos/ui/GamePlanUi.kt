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
    val plan = if (speciesName != null) {
        val sp = SPECIES.firstOrNull { it.name == speciesName } ?: return
        buildGamePlan(sp, c, LocalDate.now(), ready.forecast.timing, precip)
    } else {
        buildSidePlan(side ?: Side.FISH, c, LocalDate.now(), ready.forecast.timing, precip)
    }

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
