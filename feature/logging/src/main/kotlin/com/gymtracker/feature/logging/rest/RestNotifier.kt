package com.gymtracker.feature.logging.rest

import java.time.Instant

/**
 * Scheduling the wakeup that ends a rest (ADR-0010).
 *
 * An interface only so [RestNotificationCoordinator] can be tested without an `AlarmManager`.
 * The one real implementation is [RestAlarm].
 */
interface RestAlarms {
    fun schedule(endsAt: Instant)

    fun cancel()
}

/**
 * Posting and clearing the rest notification (US-54, ADR-0046).
 *
 * [showResting] and [showRestOver] are two different notifications, not two states of one — see
 * [RestNotification] for why they keep separate ids and channels.
 */
interface RestNotifier {
    /**
     * Posts the ongoing, silent countdown. Re-posting replaces the one already up rather than
     * stacking a second, so a re-timed rest needs no dismissal first.
     *
     * Suspending because what it says is read from the database (`DescribeRestNotification`).
     */
    suspend fun showResting(endsAt: Instant)

    /** Clears the countdown. Called when a rest is skipped, retimed away, or simply ends. */
    fun dismissResting()

    /** Replaces the countdown with the high-priority "Rest over" (US-05). */
    suspend fun showRestOver()

    /**
     * Clears the "Rest over" notification.
     *
     * Needed because `setAutoCancel` only fires when the notification *body* is tapped, not
     * when one of its actions is — so acting on it would otherwise leave it sitting there.
     */
    fun dismissRestOver()
}
