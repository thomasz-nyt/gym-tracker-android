package com.gymtracker.feature.logging.rest

import com.gymtracker.feature.logging.FakeRestTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/**
 * US-54, ADR-0046: the stored end time is the *only* trigger for scheduling and for dismissal.
 *
 * The reason this class exists at all is the bug in the arrangement it replaces. Scheduling
 * used to be a Compose side effect keyed on `RestController.restStarted`, and
 * `RestController.skip()` never flipped that flag — so `alarm.cancel()` was unreachable and
 * ADR-0010's "cancelled on skip" was false from the day it was written. `skipping a rest takes
 * the alarm and the notification with it` below is that bug, held down.
 */
class RestNotificationCoordinatorTest {
    private val now: Instant = Instant.parse("2026-08-30T18:00:00Z")
    private val store = FakeRestTimerStore()
    private val alarms = RecordingAlarms()
    private val notifier = RecordingNotifier()

    // `backgroundScope`, because the collection is meant to run for the life of the process
    // and so never completes on its own — see the class under test.
    private fun coordinate(scope: CoroutineScope) {
        RestNotificationCoordinator(store, alarms, notifier).start(scope)
    }

    @Test
    fun `nothing is scheduled or shown while no rest is running`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinate(backgroundScope)

            assertEquals(emptyList(), alarms.scheduled)
            assertEquals(emptyList(), notifier.shown)
        }

    @Test
    fun `starting a rest schedules the alarm and shows the countdown`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinate(backgroundScope)

            store.setRest(now.plusSeconds(60), Duration.ofSeconds(60))

            assertEquals(listOf(now.plusSeconds(60)), alarms.scheduled)
            assertEquals(listOf(now.plusSeconds(60)), notifier.shown)
        }

    @Test
    fun `skipping a rest takes the alarm and the notification with it`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinate(backgroundScope)
            store.setRest(now.plusSeconds(60), Duration.ofSeconds(60))

            // What `RestTimer.skip()` writes. Nothing else is signalled — that is the point:
            // a caller cannot forget to cancel, because cancelling is not a thing callers do.
            val alarmsBefore = alarms.cancelled
            val dismissalsBefore = notifier.dismissed

            store.setRestEndsAt(null)

            assertEquals(alarmsBefore + 1, alarms.cancelled, "the buzz that was coming is called off")
            assertEquals(dismissalsBefore + 1, notifier.dismissed, "and the countdown does not outlive the rest")
        }

    @Test
    fun `a stale notification from a previous process is cleared on startup`() =
        runTest(UnconfinedTestDispatcher()) {
            // Notifications outlive the process that posted them, so an app killed mid-rest can
            // relaunch to find a countdown still sitting in the shade with nothing behind it.
            // Acting on the *first* value — including a null one — is what clears it, and is why
            // the collection deliberately does not skip its initial emission.
            coordinate(backgroundScope)

            assertEquals(1, alarms.cancelled)
            assertEquals(1, notifier.dismissed)
        }

    @Test
    fun `logging the next set retimes rather than stacking a second countdown`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinate(backgroundScope)
            store.setRest(now.plusSeconds(60), Duration.ofSeconds(60))

            store.setRest(now.plusSeconds(120), Duration.ofSeconds(60))

            assertEquals(
                listOf(now.plusSeconds(60), now.plusSeconds(120)),
                notifier.shown,
                "one notification, re-posted — the id is reused, so the second replaces the first",
            )
        }

    @Test
    fun `a rest already running when the process starts is picked back up`() =
        runTest(UnconfinedTestDispatcher()) {
            // Killed mid-rest and relaunched: the store still holds the end time, so the
            // coordinator re-derives rather than stranding a notification nobody owns.
            store.setRest(now.plusSeconds(45), Duration.ofSeconds(60))

            coordinate(backgroundScope)

            assertEquals(listOf(now.plusSeconds(45)), notifier.shown)
            assertEquals(listOf(now.plusSeconds(45)), alarms.scheduled)
        }

    @Test
    fun `re-applying re-posts the running rest without waiting for it to change`() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = RestNotificationCoordinator(store, alarms, notifier)
            coordinator.start(backgroundScope)
            store.setRest(now.plusSeconds(60), Duration.ofSeconds(60))

            // Standing in for notification permission arriving mid-rest: the stored end time is
            // unchanged, so nothing in the collection above will fire again on its own.
            coordinator.reapply()

            assertEquals(
                listOf(now.plusSeconds(60), now.plusSeconds(60)),
                notifier.shown,
                "the rest the member is actually taking gets its notification after the grant",
            )
        }

    @Test
    fun `re-applying with no rest running clears rather than posts`() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinator = RestNotificationCoordinator(store, alarms, notifier)
            coordinator.start(backgroundScope)
            val dismissalsBefore = notifier.dismissed

            coordinator.reapply()

            assertEquals(emptyList(), notifier.shown)
            assertEquals(dismissalsBefore + 1, notifier.dismissed)
        }

    private class RecordingAlarms : RestAlarms {
        val scheduled = mutableListOf<Instant>()
        var cancelled = 0

        override fun schedule(endsAt: Instant) {
            scheduled += endsAt
        }

        override fun cancel() {
            cancelled++
        }
    }

    private class RecordingNotifier : RestNotifier {
        val shown = mutableListOf<Instant>()
        var dismissed = 0

        override suspend fun showResting(endsAt: Instant) {
            shown += endsAt
        }

        override fun dismissResting() {
            dismissed++
        }

        override suspend fun showRestOver() = error("the alarm's receiver posts this one, not the coordinator")

        override fun dismissRestOver() = error("the alarm's receiver posts this one, not the coordinator")
    }
}
