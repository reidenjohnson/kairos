package com.kairos.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedules (or cancels) the single daily reminder job. One [DailyForecastWorker]
 * runs about once a day, first fire aimed at the next ~7 AM, needing a network. Using
 * a periodic job (not exact alarms) keeps it battery-light per the Stride 4 spec — the
 * exact minute of a "best time today" nudge does not matter.
 */
object NotificationScheduler {
    private const val WORK_NAME = "kairos_daily_reminders"
    private val RUN_AT = LocalTime.of(7, 0)

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyForecastWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // Keep an already-scheduled job rather than resetting its clock on every launch.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Minutes from now until the next [RUN_AT]. */
    private fun initialDelayMinutes(): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(RUN_AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMinutes().coerceAtLeast(1)
    }
}
