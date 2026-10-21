package com.kairos.ui.bucks

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.ui.KairosColors
import kotlinx.coroutines.launch

/** Whether the Bucks walkthrough has been seen (per phone). */
object BucksIntroPrefs {
    private const val PREFS = "kairos_bucks"
    fun seen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("intro_seen", false)
    fun markSeen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("intro_seen", true).apply()
    fun reset(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("intro_seen", false).apply()
}

private data class IntroPage(val icon: ImageVector, val step: String?, val title: String, val body: String, val points: List<String>)

/** Page icon accents from the brand palette: intro and insights use the plan gradient; photos Teal, sorting Amber, cameras Fern. */
private fun pageAccent(i: Int) = when (i) { 1 -> Accent.teal; 2 -> Accent.amber; 3 -> Accent.fern; 4 -> Accent.teal; else -> Accent.fern }

private val PAGES = listOf(
    IntroPage(
        Icons.Outlined.CameraAlt, null, "Pattern your bucks.",
        "Turn your trail-cam photos into a plan for each buck: when he moves, on what wind and weather, where he's headed, and when to sit.",
        listOf("Every number comes from your photos and real weather for that hour.", "Works at camp with no signal; weather fills in when you're back in range."),
    ),
    IntroPage(
        Icons.Outlined.PhotoLibrary, "Step 1", "Bring in your photos.",
        "Link the folder your trail-cam photos land in, or pick photos. Kairos reads the time off each one and merges copies of the same shot.",
        listOf("Link a folder once (like DCIM/Trail cams), then tap Check folder for new ones.", "Originals, SPYPOINT app saves and screenshots all work.", "Only want bucks in here? Import just those. Does and empty frames take one tap anyway."),
    ),
    IntroPage(
        Icons.Outlined.TouchApp, "Step 2", "Sort with a few taps.",
        "Photos from one camera within 30 minutes are one visit. You answer once per visit: a buck, a doe, or nothing. Then \"Buck 1?\" yes or no, with his best photo right under it.",
        listOf("Spikes and small racks go in one Young bucks group.", "The first photo from each camera asks which camera it is; after that it's matched by the view."),
    ),
    IntroPage(
        Icons.Outlined.Place, "Step 3", "Place your cameras.",
        "Drop each camera's pin on the satellite map and turn the dial to aim its view. That pulls the weather for every sighting and turns 'walked left' into a compass direction.",
        listOf("Moved a camera? Make the new spot its own camera.", "Bucks, Your cameras, tap a camera, Pin it on the map."),
    ),
    IntroPage(
        Icons.Outlined.AutoGraph, "Step 4", "Read his pattern.",
        "Each buck gets his own page: the plan and your next best sits, when he shows, his winds and weather against how often they happen, every sighting, and what the mentors and the research say.",
        listOf("Patterns are only marked Strong when they're unlikely to be luck.", "Tap any photo to see what was happening at that moment and what to do about it."),
    ),
)

/**
 * First-visit walkthrough: what Bucks is, how it works, and setup, five swipeable pages with
 * the setup actions on the last one.
 */
@Composable
fun BucksIntro(onFolder: () -> Unit, onPhotos: () -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(KairosColors.Bg).padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Skip", color = KairosColors.Dim) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = PAGES[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(if (i == 0 || i == 4) Accent.planBrush else Accent.brush(pageAccent(i))), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = if (i == 0 || i == 4) KairosColors.OnSeg else Accent.on(pageAccent(i)), modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(28.dp))
                p.step?.let {
                    Text(it.uppercase(), style = MaterialTheme.typography.labelLarge, color = pageAccent(i), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                }
                Text(p.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = KairosColors.Text)
                Spacer(Modifier.height(14.dp))
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = KairosColors.Dim)
                Spacer(Modifier.height(20.dp))
                p.points.forEach { pt ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).background(Accent.soft(pageAccent(i))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Check, null, tint = pageAccent(i), modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(pt, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Text)
                    }
                }
            }
        }
        // Page dots.
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES.size) { k ->
                val on = k == pager.currentPage
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (on) 24.dp else 8.dp).clip(CircleShape).background(if (on) pageAccent(pager.currentPage) else KairosColors.Surface2))
            }
        }
        if (pager.currentPage == PAGES.lastIndex) {
            PrimaryPill("Link a folder", onFolder, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            SecondaryPill("Choose photos", onPhotos, Modifier.fillMaxWidth())
        } else {
            PrimaryPill("Next", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            SecondaryPill("I'll set it up later", onClose, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One line of the setup checklist on the Bucks home. */
@Composable
internal fun SetupRow(n: Int, accent: androidx.compose.ui.graphics.Color, title: String, sub: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = !done, onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (done) accent else Accent.soft(accent)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Outlined.Check, null, tint = Accent.on(accent), modifier = Modifier.size(18.dp))
            else Text("$n", color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (done) KairosColors.Dim else KairosColors.Text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
        }
        if (!done) Text("Go", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
