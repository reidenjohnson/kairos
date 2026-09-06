package com.kairos.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kairos.MainActivity
import com.kairos.R

/**
 * Notification plumbing: one channel for Kairos' daily reminders, plus a small helper
 * to post a notification that deep-opens the app. Kept intentionally simple — the
 * batched [DailyForecastWorker] decides what (if anything) to say each day.
 */
object Notifications {
    const val CHANNEL_ID = "kairos_reminders"

    // Stable ids so a new day's notification replaces the previous one of the same kind
    // rather than stacking up.
    const val ID_TODAY = 1001
    const val ID_WEEK = 1002
    const val ID_DEADLINE = 1003

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Best-time-to-go nudges and license/lottery deadline reminders"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /** True when the OS will let us post (Android 13+ gates on a runtime permission). */
    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, title: String, body: String) {
        if (!canPost(context)) return
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // The white-on-transparent mark doubles as the status-bar icon (the system
        // masks it to the notification tint).
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_mark_white)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
