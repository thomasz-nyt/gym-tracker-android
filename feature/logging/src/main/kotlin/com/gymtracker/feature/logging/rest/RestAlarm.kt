package com.gymtracker.feature.logging.rest

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/**
 * Schedules the "rest is over" notification (ADR-0010).
 *
 * The alarm is only ever a notification trigger — the timer itself is the end time in
 * DataStore, so a missed or cancelled alarm costs a buzz, never the timer.
 */
class RestAlarm
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val alarms = context.getSystemService(AlarmManager::class.java)

        fun schedule(endsAt: Instant) {
            if (!canNotify()) return

            // Exact because a rest is 90 seconds; an inexact alarm can land minutes late,
            // which for this purpose is the same as not firing.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), pendingIntent())
        }

        fun cancel() {
            alarms.cancel(pendingIntent())
        }

        /**
         * Without notification permission there is nothing for the alarm to do, so we do not
         * take a wakeup for it. The in-app countdown is unaffected (US-05).
         */
        private fun canNotify(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        private fun pendingIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, RestOverReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private companion object {
            const val REQUEST_CODE = 1
        }
    }

/** Posts the notification when a rest ends. Thin by design; the logic lives in the domain. */
class RestOverReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channel =
            NotificationChannel(CHANNEL_ID, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when a rest between sets is over."
            }
        manager.createNotificationChannel(channel)

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Rest over")
                .setContentText("Time for your next set.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

        // Checked inline rather than in a helper: lint's data flow does not follow an
        // extension function, and a suppression here would hide a genuine crash class.
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            // The catch covers permission being revoked between the check and the post. Losing
            // the buzz is fine — the timer is the stored end time, so nothing depends on this.
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }

    private companion object {
        const val CHANNEL_ID = "rest-timer"
        const val NOTIFICATION_ID = 1
    }
}
