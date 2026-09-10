package com.kairos.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fires each day when the reminder alarm goes off — reliably, even in Doze. Does the
 * (fast, cached-location) reminder work within the receiver's short background window via
 * [goAsync], then reschedules tomorrow's alarm — so the daily nudge keeps coming without
 * depending on the app being opened.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(9_000) { DailyReminders.run(app) }
            } finally {
                NotificationScheduler.schedule(app) // set tomorrow's alarm
                pending.finish()
            }
        }
    }
}
