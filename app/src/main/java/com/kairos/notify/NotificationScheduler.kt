package com.kairos.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the single daily reminder. Uses an **AlarmManager** alarm rather than
 * WorkManager, because Samsung (and other OEMs) aggressively defer WorkManager for apps
 * they've put to sleep — which is why reminders only showed up when the app was opened.
 *
 * `setAndAllowWhileIdle` fires even in Doze without needing the exact-alarm permission;
 * the exact minute of a "best time today" nudge doesn't matter, only that it fires. Each
 * firing ([ReminderReceiver]) reschedules the next day, and [BootReceiver] restores it
 * after a reboot (which clears alarms).
 */
object NotificationScheduler {
    private const val REQUEST = 4711
    private val RUN_AT = LocalTime.of(7, 0)
    const val ACTION = "com.kairos.notify.DAILY_REMINDER"

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis(), pendingIntent(context))
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Epoch millis of the next [RUN_AT] (today if still ahead, else tomorrow). */
    private fun nextTriggerMillis(): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(RUN_AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
