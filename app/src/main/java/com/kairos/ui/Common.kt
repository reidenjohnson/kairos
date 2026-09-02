package com.kairos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * The Kairos brand mark: an arrow whose shaft curls into a fishhook — one glyph
 * for both sides of the app (the "hooked arrow" lead concept from the redesign).
 */
@Composable
internal fun KairosMark(size: Dp = 24.dp, color: Color = KairosColors.OnSeg) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Shaft that curves into a hook at the bottom.
        val body = Path().apply {
            moveTo(w * 0.55f, w * 0.14f)
            lineTo(w * 0.55f, w * 0.60f)
            quadraticBezierTo(w * 0.55f, w * 0.84f, w * 0.34f, w * 0.84f)
            quadraticBezierTo(w * 0.20f, w * 0.84f, w * 0.26f, w * 0.68f)
        }
        drawPath(body, color, style = stroke)
        // Arrowhead at the top.
        val head = Path().apply {
            moveTo(w * 0.42f, w * 0.28f)
            lineTo(w * 0.55f, w * 0.13f)
            lineTo(w * 0.68f, w * 0.28f)
        }
        drawPath(head, color, style = stroke)
    }
}
