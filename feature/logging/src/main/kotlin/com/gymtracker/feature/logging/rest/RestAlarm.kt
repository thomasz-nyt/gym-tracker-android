package com.gymtracker.feature.logging.rest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
 * The alarm is only ever a trigger — the timer itself is the end time in DataStore, so a missed
 * or cancelled alarm costs a buzz, never the timer. It is scheduled whether or not notifications
 * are allowed: since ADR-0049 the receiver it fires plays the zero-second cue, which is not a
 * notification, and the notifier checks permission itself before posting anything.
 */
@Singleton
class RestAlarm
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RestAlarms {
        private val alarms = context.getSystemService(AlarmManager::class.java)

        override fun schedule(endsAt: Instant) {
            // Exact because a rest is 60 seconds; an inexact alarm can land minutes late,
            // which for this purpose is the same as not firing.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), pendingIntent())
        }

        override fun cancel() {
            alarms.cancel(pendingIntent())
        }

        override fun scheduleCue(at: Instant) {
            // Deliberately not behind canNotify(): the cue is a haptic pulse and an optional tone,
            // not a notification, so a member who declined notifications still gets it (ADR-0049).
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), cueIntent())
        }

        override fun cancelCue() {
            alarms.cancel(cueIntent())
        }

        private fun pendingIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, RestOverReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun cueIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                CUE_REQUEST_CODE,
                Intent(context, RestCueReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private companion object {
            const val REQUEST_CODE = 1
            const val CUE_REQUEST_CODE = 2
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

    @Inject
    lateinit var cue: RestCue

    @Inject
    lateinit var foreground: AppForeground

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        // Read here, on the main thread, before anything moves off it: the process lifecycle is a
        // main-thread object.
        val onScreen = foreground.isInForeground()

        // Reading the next set out of Room is more than onReceive may do inline, and well
        // inside what goAsync allows.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // The zero-second cue (ADR-0049) rides this alarm rather than a third one; the
                // pulse comes first so it is not waiting behind a Room read for the set line.
                cue.play()
                // With the app on screen the band already reads 0:00 and the cue has just pulsed;
                // a notification over it would only cover LOG SET (US-56 as amended). The
                // countdown still comes down either way.
                if (onScreen) notifier.dismissResting() else notifier.showRestOver()
            } finally {
                pending.finish()
            }
        }
    }
}
