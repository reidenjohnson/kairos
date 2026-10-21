package com.kairos.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A reboot clears scheduled alarms, so re-arm the daily reminder after boot (only if the
 * user has reminders turned on).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (NotifyPrefsBridge.enabled(context.applicationContext)) {
            NotificationScheduler.schedule(context.applicationContext)
        }
    }
}
