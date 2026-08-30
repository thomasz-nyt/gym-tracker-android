package com.gymtracker.feature.logging.rest

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the "rest is over" notification (ADR-0010).
 *
 * The alarm is only ever a notification trigger — the timer itself is the end time in
 * DataStore, so a missed or cancelled alarm costs a buzz, never the timer.
 */
@Singleton
class RestAlarm
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RestAlarms {
        private val alarms = context.getSystemService(AlarmManager::class.java)

        override fun schedule(endsAt: Instant) {
            if (!canNotify()) return

            // Exact because a rest is 60 seconds; an inexact alarm can land minutes late,
            // which for this purpose is the same as not firing.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), pendingIntent())
        }

        override fun cancel() {
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

/**
 * Posts the notification when a rest ends. Still thin, but no longer empty: what it says comes
 * from the domain (US-54, ADR-0046), so this only decides *when*.
 */
@AndroidEntryPoint
class RestOverReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notifier: RestNotifier

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        // Reading the next set out of Room is more than onReceive may do inline, and well
        // inside what goAsync allows.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                notifier.showRestOver()
            } finally {
                pending.finish()
            }
        }
    }
}
