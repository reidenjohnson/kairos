package com.kairos.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.engine.SPECIES
import com.kairos.engine.Side
import com.kairos.notify.Notifications

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

        item { Spacer(Modifier.height(Space.sm)) }
        item { Overline("Reminders", modifier = Modifier.padding(top = Space.sm)) }
        item { NotificationsCard() }

        item { Spacer(Modifier.height(Space.lg)) }
    }
}

/**
 * The daily-reminders toggle. Turning it on asks for the notification permission on
 * Android 13+; only a granted result flips [NotifyPrefs] on (which schedules the job).
 */
@Composable
private fun NotificationsCard() {
    val context = LocalContext.current
    val on = NotifyPrefs.enabled
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) NotifyPrefs.set(true)
    }
    val toggle: (Boolean) -> Unit = { want ->
        when {
            !want -> NotifyPrefs.set(false)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context) ->
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> NotifyPrefs.set(true)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KairosColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = Space.lg, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { toggle(!on) }.padding(vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Daily reminders", style = MaterialTheme.typography.bodyLarge, color = KairosColors.Text)
                Spacer(Modifier.height(2.dp))
                Text(
                    "One nudge a day: the best time to be out, plus a heads-up before a " +
                        "moose or antlerless-deer lottery deadline closes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KairosColors.Dim,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.width(Space.md))
            Switch(
                checked = on,
                onCheckedChange = toggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = KairosColors.OnSeg,
                    checkedTrackColor = KairosColors.Pine,
                    uncheckedTrackColor = KairosColors.Surface2,
                ),
            )
        }
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
