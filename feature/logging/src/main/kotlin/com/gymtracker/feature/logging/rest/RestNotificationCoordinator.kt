package com.gymtracker.feature.logging.rest

import com.gymtracker.core.domain.rest.RestTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the alarm and the notification in step with the stored rest (US-54, ADR-0046).
 *
 * `restEndsAt` is the single trigger: a value schedules and shows, null cancels and dismisses.
 * Skipping, logging the next set and ending a session all already write that one value, so none
 * of them has to remember to do anything else.
 *
 * That is the point, not a tidiness argument. Scheduling used to live in a Compose side effect
 * keyed on a separate "a rest started" flag, and `RestController.skip()` never set that flag —
 * so `cancel()` was unreachable and ADR-0010's "cancelled on skip" was false from the day it
 * was written. There is no longer a call site that can forget, because cancelling is no longer
 * something call sites do.
 *
 * Process-scoped rather than screen-scoped for the same reason: a rest outlives the screen that
 * started it, and after a process kill this simply re-reads the store and converges.
 */
@Singleton
class RestNotificationCoordinator
    @Inject
    constructor(
        private val store: RestTimerStore,
        private val alarms: RestAlarms,
        private val notifier: RestNotifier,
    ) {
        private var scope: CoroutineScope? = null

        /** @param scope the process-lifetime scope to collect on; never cancelled in the app. */
        fun start(scope: CoroutineScope) {
            this.scope = scope
            scope.launch {
                // The first emission is acted on like any other, including when it is null.
                // That is what clears a countdown left in the shade by a process that was
                // killed mid-rest — notifications outlive the process that posted them.
                store.restEndsAt.distinctUntilChanged().collect(::apply)
            }
        }

        /**
         * Re-applies the running rest without waiting for it to change.
         *
         * Because whether we *can* post is not part of `restEndsAt`. Granting notification
         * permission mid-rest — which is exactly when US-05 asks for it, on the member's very
         * first rest — leaves that rest with an alarm that was never scheduled and a
         * notification that was never posted, and nothing about the stored end time changes to
         * say so. Called when the app resumes, which covers the permission dialog closing and
         * equally a member turning notifications back on in system Settings.
         */
        fun reapply() {
            scope?.launch { apply(store.restEndsAt.first()) }
        }

        private suspend fun apply(endsAt: Instant?) {
            if (endsAt == null) {
                alarms.cancel()
                notifier.dismissResting()
            } else {
                alarms.schedule(endsAt)
                notifier.showResting(endsAt)
            }
        }
    }
