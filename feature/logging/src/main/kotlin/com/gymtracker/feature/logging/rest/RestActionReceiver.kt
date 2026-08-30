package com.gymtracker.feature.logging.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gymtracker.core.domain.rest.DescribeRestNotification
import com.gymtracker.core.domain.rest.LogUpNextSet
import com.gymtracker.core.domain.rest.RestTimer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The notification's action buttons (US-54).
 *
 * A receiver rather than an activity on purpose: neither action opens the app, which is what
 * makes them worth having — the answer to "did that work?" is the notification updating in
 * place. It also means this never meets Android 12's notification-trampoline restriction,
 * because nothing here starts an `Activity`.
 *
 * Nothing is carried in the intent but the action name. What to log is re-derived from the
 * database on each tap, because a notification can outlive the process that posted it and
 * anything remembered in a `PendingIntent` would be remembered stale (ADR-0046).
 */
@AndroidEntryPoint
class RestActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var describe: DescribeRestNotification

    @Inject
    lateinit var logUpNextSet: LogUpNextSet

    @Inject
    lateinit var restTimer: RestTimer

    @Inject
    lateinit var notifier: RestNotifier

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val action = intent?.action ?: return
        if (action != ACTION_LOG_SET && action != ACTION_SKIP_REST) return

        // A write plus a re-post is far inside a receiver's budget, but it is still more than
        // onReceive may do inline.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (action) {
                    // Logging starts the next rest itself, so the coordinator re-posts the
                    // countdown with the set number already advanced. Nothing to do here after.
                    ACTION_LOG_SET -> describe()?.let { logUpNextSet(it.upNext) }
                    // Clearing the stored end time is the whole skip: the coordinator sees it
                    // and takes the alarm and the notification down.
                    ACTION_SKIP_REST -> restTimer.skip()
                }
            } finally {
                // The rest-over notification is the one thing the coordinator does not own, so
                // an action taken from it has to clear it.
                notifier.dismissRestOver()
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION_LOG_SET = "com.gymtracker.feature.logging.rest.LOG_SET"
        const val ACTION_SKIP_REST = "com.gymtracker.feature.logging.rest.SKIP_REST"
    }
}
