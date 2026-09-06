package com.kairos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.engine.SPECIES
import com.kairos.engine.Side

/**
 * Settings: the species filter. The user picks who they're after; only those species
 * show across Today, Seasons, and Trends (the rest are hidden until re-enabled). Reads
 * and writes [SpeciesPrefs], which persists the choice and drives the timing hero too.
 */
@Composable
fun SettingsScreen() {
    // Touch the observable so the whole screen recomposes as switches flip.
    val enabled = SpeciesPrefs.enabled
    val shown = SPECIES.count { SpeciesPrefs.isEnabled(it.name) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Spacer(Modifier.height(Space.xs)) }
        item {
            Column {
                Overline("Species", color = KairosColors.Water)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "What you're after",
                    fontFamily = Bricolage,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp,
                    lineHeight = 34.sp,
                    color = KairosColors.Text,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Only the species you turn on show up across Today, Seasons, and Trends. " +
                        "$shown of ${SPECIES.size} shown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KairosColors.Dim,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextButton(onClick = { SpeciesPrefs.enableAll() }, enabled = !SpeciesPrefs.isDefault()) {
                    Text("Select all")
                }
                TextButton(onClick = { SpeciesPrefs.clearAll() }, enabled = shown > 0) {
                    Text("Clear all")
                }
            }
        }

        sideSection(Side.HUNT, "Hunt")
        sideSection(Side.FISH, "Fish")

        item { Spacer(Modifier.height(Space.lg)) }
    }
}

/** A side header (with a side-wide toggle) and the species switch rows for that side. */
private fun androidx.compose.foundation.lazy.LazyListScope.sideSection(side: Side, label: String) {
    val speciesOfSide = SPECIES.filter { it.side == side }
    item {
        val onCount = speciesOfSide.count { SpeciesPrefs.isEnabled(it.name) }
        val allOn = onCount == speciesOfSide.size
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Overline("$label · $onCount of ${speciesOfSide.size}", modifier = Modifier.weight(1f))
            Text(
                if (allOn) "All on" else "Turn all on",
                style = MaterialTheme.typography.labelMedium,
                color = KairosColors.Water,
                modifier = Modifier
                    .clickable(enabled = !allOn) { SpeciesPrefs.setSide(side, true) }
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            )
        }
    }
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(KairosColors.Surface, RoundedCornerShape(16.dp)),
        ) {
            speciesOfSide.forEachIndexed { i, sp ->
                SpeciesRow(sp.name, SpeciesPrefs.isEnabled(sp.name)) { SpeciesPrefs.toggle(sp.name) }
                if (i < speciesOfSide.lastIndex) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = Space.lg)
                            .background(KairosColors.Line),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeciesRow(name: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = Space.lg, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (on) KairosColors.Text else KairosColors.Faint,
        )
        Switch(
            checked = on,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = KairosColors.OnSeg,
                checkedTrackColor = KairosColors.Pine,
                uncheckedTrackColor = KairosColors.Surface2,
            ),
        )
    }
}
