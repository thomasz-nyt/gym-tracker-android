package com.gymtracker.feature.logging.rest

import com.gymtracker.core.domain.rest.RestTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
        /** @param scope the process-lifetime scope to collect on; never cancelled in the app. */
        fun start(scope: CoroutineScope) {
            scope.launch {
                // The first emission is acted on like any other, including when it is null.
                // That is what clears a countdown left in the shade by a process that was
                // killed mid-rest — notifications outlive the process that posted them.
                store.restEndsAt.distinctUntilChanged().collect { endsAt ->
                    if (endsAt == null) {
                        alarms.cancel()
                        notifier.dismissResting()
                    } else {
                        alarms.schedule(endsAt)
                        notifier.showResting(endsAt)
                    }
                }
            }
        }
    }
