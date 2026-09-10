package com.kairos.notify

import android.content.Context
import com.kairos.data.Location
import com.kairos.data.Place
import com.kairos.data.WeatherRepository
import com.kairos.engine.DeadlineKind
import com.kairos.engine.MAINE_DEADLINES
import com.kairos.engine.Side
import com.kairos.engine.SpeciesFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The actual daily-reminder work, extracted so BOTH the reliable AlarmManager path
 * ([ReminderReceiver]) and any WorkManager fallback can run the same thing. Decides
 * what, if anything, to say today (a closing deadline; today's best window; on Mondays
 * the week's best day) and posts it. Fails quiet — no network / no permission / nothing
 * worth saying just means no notification.
 */
object DailyReminders {

    private val REMIND_DAYS = setOf(1, 3, 7, 14)

    /** Run one daily pass. Safe to call off the main thread; does its own IO. */
    suspend fun run(context: Context) {
        if (!NotifyPrefsBridge.enabled(context)) return
        if (!Notifications.canPost(context)) return

        SpeciesFilter.enabledNames = NotifyPrefsBridge.enabledSpecies(context)

        val today = LocalDate.now()
        deadlineReminder(context, today)
        forecastNudges(context, today)
    }

    private fun deadlineReminder(context: Context, today: LocalDate) {
        val soon = MAINE_DEADLINES.mapNotNull { d ->
            val deadline = d.events.firstOrNull { it.kind == DeadlineKind.APPLICATION_DEADLINE }
                ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, deadline.date).toInt()
            if (days in REMIND_DAYS) Triple(d.name, deadline.date, days) else null
        }.minByOrNull { it.third } ?: return

        val (name, _, days) = soon
        val whenWord = when (days) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in $days days"
        }
        Notifications.post(
            context,
            Notifications.ID_DEADLINE,
            "Deadline: $name",
            "Applications close $whenWord. Apply through Maine IF&W before the window shuts.",
        )
    }

    private suspend fun forecastNudges(context: Context, today: LocalDate) {
        // Use the last place the app looked at — fast, no GPS wait (the alarm's window is short).
        val place = NotifyPrefsBridge.lastPlace(context) ?: Location.SEBAGO
        val forecast = runCatching { withContext(Dispatchers.IO) { WeatherRepository.fetch(place) } }.getOrNull() ?: return

        forecast.timing?.let { timing ->
            val side = if (timing.fishToday >= timing.huntToday) Side.FISH else Side.HUNT
            val verb = if (side == Side.FISH) "Fishing" else "Hunting"
            val score = timing.scoreForSide(side)
            val window = timing.bestWindows(side).firstOrNull()?.let { formatWindow(it) }
            val body = buildString {
                append("$verb looks best today (score $score).")
                if (window != null) append(" Best window: $window.")
            }
            Notifications.post(context, Notifications.ID_TODAY, "Today at ${forecast.placeLabel}", body)
        }

        if (today.dayOfWeek == DayOfWeek.MONDAY) weeklyBestDay(context, place)
    }

    private suspend fun weeklyBestDay(context: Context, place: Place) {
        val outlook = runCatching { withContext(Dispatchers.IO) { WeatherRepository.fetchOutlook(place) } }.getOrNull() ?: return
        val best = outlook.perSpecies
            .flatMap { sp -> sp.days.map { Triple(sp.side, it.date, it.bestPercent) } }
            .maxByOrNull { it.third } ?: return
        val (side, date, score) = best
        val verb = if (side == Side.FISH) "fishing" else "hunting"
        Notifications.post(
            context,
            Notifications.ID_WEEK,
            "This week's best $verb",
            "${dayName(date.dayOfWeek)} looks like the top day for $verb (score $score).",
        )
    }

    private fun formatWindow(range: IntRange): String =
        "${hourLabel(range.first)}–${hourLabel((range.last + 1).coerceAtMost(24))}"

    private fun hourLabel(h: Int): String = when {
        h == 0 || h == 24 -> "12 AM"
        h == 12 -> "12 PM"
        h < 12 -> "$h AM"
        else -> "${h - 12} PM"
    }

    private fun dayName(d: DayOfWeek): String = d.name.lowercase().replaceFirstChar { it.uppercase() }
}
