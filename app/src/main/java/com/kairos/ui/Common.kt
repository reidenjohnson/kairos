package com.kairos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.R
import com.kairos.engine.Rating
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared rating color used by the score rows, chips, and the Trends chart. */
internal fun ratingColor(rating: Rating): Color = when (rating) {
    Rating.PRIME -> KairosColors.Prime
    Rating.GOOD -> KairosColors.Good
    Rating.FAIR -> KairosColors.Fair
    Rating.SLOW -> KairosColors.Slow
}

internal fun ratingLabel(rating: Rating): String = when (rating) {
    Rating.PRIME -> "Prime"
    Rating.GOOD -> "Good"
    Rating.FAIR -> "Fair"
    Rating.SLOW -> "Slow"
}

@Composable
internal fun RatingChip(rating: Rating) {
    Box(
        modifier = Modifier
            .background(ratingColor(rating).copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(ratingLabel(rating), style = MaterialTheme.typography.labelMedium, color = ratingColor(rating))
    }
}

internal fun ageText(savedAtMillis: Long): String {
    val mins = ((System.currentTimeMillis() - savedAtMillis) / 60_000L).toInt()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} h ago"
        else -> "${mins / (60 * 24)} d ago"
    }
}

internal fun timeText(savedAtMillis: Long): String {
    val zoned = Instant.ofEpochMilli(savedAtMillis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("h:mm a").format(zoned)
}

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "Nov 15" — used across Seasons and Trends. */
internal fun monthDay(date: LocalDate): String = "${MONTHS[date.monthValue - 1]} ${date.dayOfMonth}"

/**
 * The Kairos brand mark: the compound-bow "K" drawn with a nocked arrow. Rendered from
 * the exported logo asset (white on transparent) and tinted so it reads on any surface.
 */
@Composable
internal fun KairosMark(
    size: Dp = 24.dp,
    color: Color = KairosColors.OnSeg,
    modifier: Modifier = Modifier,
) {
    // The asset is tight-cropped, so sizing by height keeps the mark's true aspect;
    // the caller frames it to match the app-icon spacing (mark ~43% of the tile).
    Image(
        painter = painterResource(R.drawable.logo_mark_white),
        contentDescription = "Kairos",
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.height(size),
    )
}
