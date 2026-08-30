package com.gymtracker.feature.logging.rest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gymtracker.core.domain.rest.DescribeRestNotification
import com.gymtracker.core.domain.rest.RestNotice
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The rest notification (US-54, ADR-0046).
 *
 * Deliberately thin: what to say is [DescribeRestNotification]'s answer, and this only turns it
 * into a `Notification`. ADR-0010 called the notification untestable glue, and that stays true
 * only for as long as nothing is decided here.
 */
@Singleton
class RestNotification
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val describe: DescribeRestNotification,
    ) : RestNotifier {
        private val manager = NotificationManagerCompat.from(context)

        override suspend fun showResting(endsAt: Instant) {
            val notice = describe()

            // The mirror of showRestOver's own dismissal, and found on a device rather than in
            // a test: without it, starting a rest leaves the *previous* rest's "Rest over" in
            // the shade, and it is now stale — it names the set that has just been logged while
            // the countdown beside it names the one after. Two notifications disagreeing about
            // the same question is worse than either of them alone.
            dismissRestOver()

            post(
                id = RESTING_ID,
                notification =
                    builder(RESTING_CHANNEL)
                        .setContentTitle(notice?.exerciseName ?: "Resting")
                        .setContentText(notice?.setLine())
                        // The countdown, rendered by the platform from `when`. Nothing in this
                        // process ticks — which is the whole reason an ongoing notification is
                        // affordable here at all (ADR-0046).
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setWhen(endsAt.toEpochMilli())
                        .setShowWhen(true)
                        .setOngoing(true)
                        .setSilent(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .apply {
                            // Absent rather than disabled when there is nothing to log: an
                            // action that does nothing is worse than one that is not offered.
                            if (notice != null) addAction(action(LOG_SET_LABEL, RestActionReceiver.ACTION_LOG_SET))
                            addAction(action(SKIP_REST_LABEL, RestActionReceiver.ACTION_SKIP_REST))
                        }.build(),
            )
        }

        override fun dismissResting() {
            manager.cancel(RESTING_ID)
        }

        override fun dismissRestOver() {
            manager.cancel(REST_OVER_ID)
        }

        override suspend fun showRestOver() {
            val notice = describe()

            // The countdown and this are different ids, so the old one has to go explicitly.
            // Same reason they are different ids at all: see the channel note below.
            dismissResting()

            post(
                id = REST_OVER_ID,
                notification =
                    builder(REST_OVER_CHANNEL)
                        .setContentTitle("Rest over")
                        .setContentText(notice?.setLine() ?: "Time for your next set.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .apply {
                            if (notice != null) addAction(action(LOG_SET_LABEL, RestActionReceiver.ACTION_LOG_SET))
                        }.build(),
            )
        }

        /** `Set 2 · 135 lb × 8` — the same shape the rest panel renders on screen. */
        private fun RestNotice.setLine(): String {
            val display = WeightFormatter.format(weight?.let { UnitConverter.toKilograms(it, unit) }, unit)
            return "Set $setNumber  ·  ${display.primary} × $reps"
        }

        private fun builder(channelId: String): NotificationCompat.Builder {
            manager.createNotificationChannel(channelFor(channelId))
            return NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentIntent(openTheApp())
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        }

        /**
         * The bug US-54 opened on: there was no content intent, so tapping did nothing.
         *
         * The launcher intent rather than an explicit one for two reasons. `:feature:logging`
         * cannot see `MainActivity` — `:app` depends on this module, not the reverse — and the
         * launcher intent carries `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`, which resumes the task
         * already running instead of stacking a second activity on top of it. No `launchMode`
         * change, and no deep link: the start destination derives session-from-home out of Room
         * (ADR-0013), so this already lands in the running session.
         */
        private fun openTheApp(): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                PendingIntent.getActivity(context, OPEN_REQUEST, launch, IMMUTABLE_UPDATE)
            }

        private fun action(
            label: String,
            action: String,
        ): NotificationCompat.Action =
            NotificationCompat.Action
                .Builder(
                    0,
                    label,
                    PendingIntent.getBroadcast(
                        context,
                        action.hashCode(),
                        Intent(context, RestActionReceiver::class.java).setAction(action),
                        IMMUTABLE_UPDATE,
                    ),
                ).build()

        /**
         * Two channels, and they are not interchangeable. A rest starts every 60 seconds, so the
         * countdown has to be silent and must never pop a heads-up; the buzz at zero is the
         * opposite of that. Keeping the original id and channel for the buzz also preserves
         * whatever the member has already configured for it.
         */
        private fun channelFor(id: String): NotificationChannel =
            if (id == RESTING_CHANNEL) {
                NotificationChannel(RESTING_CHANNEL, "Rest running", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows the rest counting down, with the set that is coming."
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            } else {
                NotificationChannel(REST_OVER_CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Tells you when a rest between sets is over."
                }
            }

        private fun post(
            id: Int,
            notification: android.app.Notification,
        ) {
            if (!manager.areNotificationsEnabled()) return

            // Checked inline rather than in a helper: lint's data flow does not follow an
            // extension function, and a suppression here would hide a genuine crash class.
            val granted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (granted) {
                // The catch covers permission being revoked between the check and the post.
                // Losing the post is fine — the timer is the stored end time, so nothing
                // depends on this.
                runCatching { manager.notify(id, notification) }
            }
        }

        internal companion object {
            const val RESTING_ID = 2

            /** Unchanged from ADR-0010, so an existing channel's settings survive. */
            const val REST_OVER_ID = 1

            const val RESTING_CHANNEL = "rest-running"
            const val REST_OVER_CHANNEL = "rest-timer"

            private const val LOG_SET_LABEL = "LOG SET"
            private const val SKIP_REST_LABEL = "SKIP REST"
            private const val OPEN_REQUEST = 10
            private const val IMMUTABLE_UPDATE =
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
    }
