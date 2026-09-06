package com.gymtracker.core.domain.rest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-05 / ADR-0010: the timer is a stored end time, not a countdown. */
class RestTimerTest {
    private val now: Instant = Instant.parse("2026-07-28T18:00:00Z")
    private val store = FakeRestTimerStore()

    private fun timer(at: Instant = now) = RestTimer(store, Clock.fixed(at, ZoneOffset.UTC))

    @Test
    fun `the default rest is one minute until changed`() =
        runTest {
            assertEquals(Duration.ofSeconds(60), store.defaultRest.first())
        }

    @Test
    fun `starting a rest stores when it ends, not how long is left`() =
        runTest {
            timer().start()

            assertEquals(now.plusSeconds(60), store.restEndsAt.first())
        }

    @Test
    fun `remaining time is derived from the clock`() =
        runTest {
            timer().start()

            val after30s = timer(now.plusSeconds(30)).remaining().first()

            assertEquals(Duration.ofSeconds(30), after30s)
        }

    @Test
    fun `a rest that ended while the app was gone reads as finished`() =
        runTest {
            // The point of storing an end time: process death is a non-event.
            timer().start()

            assertNull(timer(now.plusSeconds(61)).remaining().first())
        }

    @Test
    fun `remaining is null when no rest is running`() =
        runTest {
            assertNull(timer().remaining().first())
        }

    @Test
    fun `extending a rest moves its end and its total together`() =
        runTest {
            // ADR-0049: "0:45 of 1:30", not a bar that jumps past full — the denominator moves
            // with the end time, in one write, so the two can never disagree.
            timer().start()

            timer(now.plusSeconds(15)).extend(RestTimer.EXTENSION_STEP)

            assertEquals(now.plusSeconds(90), store.restEndsAt.first())
            assertEquals(Duration.ofSeconds(90), store.restTotal.first())
        }

    @Test
    fun `extending with no rest running starts nothing`() =
        runTest {
            timer().extend(RestTimer.EXTENSION_STEP)

            assertNull(store.restEndsAt.first(), "a stray tap must not invent a rest nobody earned")
            assertNull(store.restTotal.first())
        }

    @Test
    fun `a changed default is used by the next rest`() =
        runTest {
            store.setDefaultRest(Duration.ofSeconds(120))

            timer().start()

            assertEquals(now.plusSeconds(120), store.restEndsAt.first())
        }

    @Test
    fun `skipping clears the rest immediately`() =
        runTest {
            val timer = timer()
            timer.start()

            timer.skip()

            assertNull(store.restEndsAt.first())
            assertNull(timer.remaining().first())
        }

    @Test
    fun `starting again replaces the running rest rather than stacking`() =
        runTest {
            timer().start()

            timer(now.plusSeconds(10)).start()

            assertEquals(now.plusSeconds(70), store.restEndsAt.first(), "restarted from the second set")
        }

    // US-42's own promise: changing the default does not retime a rest already running. A
    // progress bar needs a total to draw against, and reading defaultRest live would visibly
    // break that promise — the bar's fraction would jump even though restEndsAt never moves.
    // total() must stay pinned to whatever the default was when *this* rest started.

    @Test
    fun `the running rest's total is what the default was when it started`() =
        runTest {
            store.setDefaultRest(Duration.ofSeconds(90))

            timer().start()

            assertEquals(Duration.ofSeconds(90), timer().total().first())
        }

    @Test
    fun `changing the default mid-rest does not change the running rest's total`() =
        runTest {
            timer().start()
            assertEquals(Duration.ofSeconds(60), timer().total().first())

            store.setDefaultRest(Duration.ofSeconds(120))

            assertEquals(Duration.ofSeconds(60), timer().total().first(), "pinned at start, not read live")
        }

    @Test
    fun `total is null when no rest is running`() =
        runTest {
            assertNull(timer().total().first())
        }

    @Test
    fun `skipping clears the total along with the end time`() =
        runTest {
            val timer = timer()
            timer.start()

            timer.skip()

            assertNull(timer.total().first())
        }

    @Test
    fun `restarting from the second set replaces the total, not just the end time`() =
        runTest {
            store.setDefaultRest(Duration.ofSeconds(60))
            timer().start()

            store.setDefaultRest(Duration.ofSeconds(45))
            timer(now.plusSeconds(10)).start()

            assertEquals(Duration.ofSeconds(45), timer().total().first())
        }

    @Test
    fun `the notification permission is asked once and remembered`() =
        runTest {
            assertTrue(store.shouldAskForNotificationPermission.first(), "not asked yet")

            store.markNotificationPermissionAsked()

            assertEquals(false, store.shouldAskForNotificationPermission.first())
        }

    private class FakeRestTimerStore : RestTimerStore {
        private val endsAt = MutableStateFlow<Instant?>(null)
        private val total = MutableStateFlow<Duration?>(null)
        private val default = MutableStateFlow(Duration.ofSeconds(60))
        private val asked = MutableStateFlow(false)

        override val restEndsAt = endsAt
        override val restTotal = total
        override val defaultRest = default
        override val shouldAskForNotificationPermission = asked.map { !it }

        override suspend fun setRestEndsAt(instant: Instant?) {
            endsAt.value = instant
            if (instant == null) total.value = null
        }

        override suspend fun setRest(
            endsAt: Instant,
            total: Duration,
        ) {
            this.endsAt.value = endsAt
            this.total.value = total
        }

        override suspend fun setDefaultRest(rest: Duration) {
            default.value = rest
        }

        override suspend fun markNotificationPermissionAsked() {
            asked.value = true
        }
    }
}
