package com.kairos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.data.Forecast
import com.kairos.engine.SPECIES
import com.kairos.engine.Side
import com.kairos.engine.scoreAll
import java.time.format.DateTimeFormatter

/**
 * The species detail screen (tap a species on Today): the score, a per-factor
 * breakdown with up/down/neutral arrows and plain-English notes, and a conditions
 * "game plan". Everything traces back to the engine's factors — see [Explain].
 */
@Composable
fun DetailScreen(state: UiState, speciesName: String, onOpenSeason: (String) -> Unit) {
    val ready = state as? UiState.Ready
    if (ready == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Loading conditions…", color = KairosColors.Dim)
        }
        return
    }
    val species = SPECIES.firstOrNull { it.name == speciesName } ?: return
    val forecast = ready.forecast
    val c = forecast.conditions
    val score = scoreAll(c).firstOrNull { it.species.name == speciesName } ?: return
    val breakdown = factorBreakdown(species, c)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        ScoreHero(score.percent, score.rating, whyFor(species, c))
        Spacer(Modifier.height(20.dp))

        SectionLabel("WHY · FACTOR BY FACTOR")
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(KairosColors.Surface)
                .border(1.dp, KairosColors.Line, RoundedCornerShape(18.dp))
                .padding(vertical = 4.dp),
        ) {
            breakdown.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(KairosColors.Line))
                FactorRowView(row)
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("GAME PLAN")
        Spacer(Modifier.height(8.dp))
        GamePlanCard(gamePlan(species, c))

        if (species.side == Side.HUNT && forecast.legalShootingHours != null) {
            Spacer(Modifier.height(16.dp))
            LegalLightLine(forecast)
        }

        val citations = citationsFor(speciesName)
        if (citations.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("SOURCES · WHY THIS SCORE")
            Spacer(Modifier.height(8.dp))
            SourcesCard(citations, species.side == Side.FISH)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(KairosColors.Surface)
                .border(1.dp, KairosColors.Line, RoundedCornerShape(14.dp))
                .clickable { onOpenSeason(speciesName) }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Season & regulations",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = KairosColors.Text,
                modifier = Modifier.weight(1f),
            )
            Text("View  →", style = MaterialTheme.typography.labelLarge, color = KairosColors.Water)
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ScoreHero(percent: Int, rating: com.kairos.engine.Rating, why: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(KairosColors.HeroTop, KairosColors.HeroBottom)))
            .border(1.dp, KairosColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$percent",
                fontFamily = Bricolage,
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp,
                color = ratingColor(rating),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    ratingLabel(rating).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ratingColor(rating),
                    letterSpacing = 0.6.sp,
                )
                Text("out of 100 right now", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KairosColors.Line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ratingColor(rating)),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(why, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim, lineHeight = 19.sp)
    }
}

@Composable
private fun FactorRowView(row: FactorRow) {
    val (icon, tint) = when (row.dir) {
        FactorDir.UP -> Icons.Filled.ArrowUpward to KairosColors.Good
        FactorDir.DOWN -> Icons.Filled.ArrowDownward to KairosColors.Error
        FactorDir.NEUTRAL -> Icons.Filled.Remove to KairosColors.Faint
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = KairosColors.Text)
                Spacer(Modifier.width(8.dp))
                Text(row.value, fontFamily = Bricolage, fontSize = 13.sp, color = KairosColors.Dim)
            }
            Spacer(Modifier.height(2.dp))
            Text(row.note, style = MaterialTheme.typography.bodySmall, color = KairosColors.Faint, lineHeight = 17.sp)
            Spacer(Modifier.height(6.dp))
            MattersBar(row.weight)
        }
    }
}

/** A thin bar showing how much this species cares about the factor. */
@Composable
private fun MattersBar(weight: Double) {
    val frac = (weight / 0.47).coerceIn(0.06, 1.0).toFloat()
    Box(
        Modifier
            .fillMaxWidth(0.5f)
            .height(3.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(KairosColors.Line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KairosColors.Dim.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun GamePlanCard(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(KairosColors.CardTop, KairosColors.CardBottom)))
            .border(1.dp, KairosColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = KairosColors.Text, lineHeight = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text("Guidance from today's conditions — not a guarantee.", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
    }
}

@Composable
private fun LegalLightLine(f: Forecast) {
    val hours = f.legalShootingHours ?: return
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Legal shooting hours", style = MaterialTheme.typography.bodyMedium, color = KairosColors.Dim, modifier = Modifier.weight(1f))
        Text("${fmt.format(hours.first)} – ${fmt.format(hours.second)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = KairosColors.Text)
    }
}

@Composable
private fun SourcesCard(citations: List<Citation>, isFish: Boolean) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KairosColors.Surface)
            .border(1.dp, KairosColors.Line, RoundedCornerShape(18.dp))
            .padding(vertical = 4.dp),
    ) {
        citations.forEachIndexed { i, cite ->
            if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(KairosColors.Line))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(cite.url) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cite.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KairosColors.Text,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open", tint = KairosColors.Water, modifier = Modifier.size(16.dp))
            }
        }
    }
    if (isFish) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Note: water temp is a monthly Sebago estimate, not a live reading — the free " +
                "weather feed only gives air temp.",
            style = MaterialTheme.typography.labelSmall,
            color = KairosColors.Faint,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, letterSpacing = 1.4.sp)
}
