package com.kairos.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kairos.data.Location
import com.kairos.data.LocationProvider
import com.kairos.data.Place
import com.kairos.data.WeatherRepository
import com.kairos.engine.MAINE_DEADLINES
import com.kairos.engine.Side
import com.kairos.engine.SpeciesFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The single batched reminder job (see HANDOFF Stride 4 — "ONE batched WorkManager
 * job, keep battery light"). Runs about once a day and decides what, if anything, to
 * say:
 *  - a license/lottery application deadline is closing in (14 / 7 / 3 / 1 days out);
 *  - today's best time to be out (the morning-of / night-before nudge, folded into one
 *    daily run so we don't wake the device twice);
 *  - on Mondays, the week's best day (the start-of-week heads-up).
 *
 * All copy is grounded in the same engine the app uses. It fails quiet: no network,
 * no permission, or nothing worth saying just means no notification.
 */
class DailyForecastWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!NotifyPrefsBridge.enabled(applicationContext)) return Result.success()
        if (!Notifications.canPost(applicationContext)) return Result.success()

        // Match the app's species filter so a "best time" nudge respects what the user
        // is actually after (the worker's process may not have run MainActivity).
        SpeciesFilter.enabledNames = NotifyPrefsBridge.enabledSpecies(applicationContext)

        val today = LocalDate.now()
        deadlineReminder(today)
        forecastNudges(today)
        return Result.success()
    }

    /** Nudge when a lottery application window is closing in. */
    private fun deadlineReminder(today: LocalDate) {
        val soon = MAINE_DEADLINES.mapNotNull { d ->
            val deadline = d.events.firstOrNull { it.kind == com.kairos.engine.DeadlineKind.APPLICATION_DEADLINE }
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
            applicationContext,
            Notifications.ID_DEADLINE,
            "Deadline: $name",
            "Applications close $whenWord. Apply through Maine IF&W before the window shuts.",
        )
    }

    /** Today's best window, plus (on Mondays) the week's best day. */
    private suspend fun forecastNudges(today: LocalDate) {
        val place = runCatching { LocationProvider.current(applicationContext) }.getOrNull()
            ?: Location.SEBAGO

        val forecast = runCatching {
            withContext(Dispatchers.IO) { WeatherRepository.fetch(place) }
        }.getOrNull() ?: return

        forecast.timing?.let { timing ->
            val fish = timing.fishToday
            val hunt = timing.huntToday
            val side = if (fish >= hunt) Side.FISH else Side.HUNT
            val verb = if (side == Side.FISH) "Fishing" else "Hunting"
            val score = timing.scoreForSide(side)
            val window = timing.bestWindows(side).firstOrNull()?.let { formatWindow(it) }
            val body = buildString {
                append("$verb looks best today (score $score).")
                if (window != null) append(" Best window: $window.")
            }
            Notifications.post(applicationContext, Notifications.ID_TODAY, "Today at ${forecast.placeLabel}", body)
        }

        if (today.dayOfWeek == DayOfWeek.MONDAY) weeklyBestDay(place)
    }

    private suspend fun weeklyBestDay(place: Place) {
        val outlook = runCatching {
            withContext(Dispatchers.IO) { WeatherRepository.fetchOutlook(place) }
        }.getOrNull() ?: return

        // The single best species-day across the week, and which side it's on.
        val best = outlook.perSpecies
            .flatMap { sp -> sp.days.map { Triple(sp.side, it.date, it.bestPercent) } }
            .maxByOrNull { it.third } ?: return

        val (side, date, score) = best
        val verb = if (side == Side.FISH) "fishing" else "hunting"
        Notifications.post(
            applicationContext,
            Notifications.ID_WEEK,
            "This week's best $verb",
            "${dayName(date.dayOfWeek)} looks like the top day for $verb (score $score).",
        )
    }

    private fun formatWindow(range: IntRange): String {
        val start = hourLabel(range.first)
        // A window that ends at hour h really runs through the end of that hour.
        val end = hourLabel((range.last + 1).coerceAtMost(24))
        return "$start–$end"
    }

    private fun hourLabel(h: Int): String = when {
        h == 0 || h == 24 -> "12 AM"
        h == 12 -> "12 PM"
        h < 12 -> "$h AM"
        else -> "${h - 12} PM"
    }

    private fun dayName(d: DayOfWeek): String = d.name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        private val REMIND_DAYS = setOf(1, 3, 7, 14)
    }
}
