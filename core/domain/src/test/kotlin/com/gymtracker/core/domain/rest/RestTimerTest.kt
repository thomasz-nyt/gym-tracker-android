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

    @Test
    fun `the notification permission is asked once and remembered`() =
        runTest {
            assertTrue(store.shouldAskForNotificationPermission.first(), "not asked yet")

            store.markNotificationPermissionAsked()

            assertEquals(false, store.shouldAskForNotificationPermission.first())
        }

    private class FakeRestTimerStore : RestTimerStore {
        private val endsAt = MutableStateFlow<Instant?>(null)
        private val default = MutableStateFlow(Duration.ofSeconds(60))
        private val asked = MutableStateFlow(false)

        override val restEndsAt = endsAt
        override val defaultRest = default
        override val shouldAskForNotificationPermission = asked.map { !it }

        override suspend fun setRestEndsAt(instant: Instant?) {
            endsAt.value = instant
        }

        override suspend fun setDefaultRest(rest: Duration) {
            default.value = rest
        }

        override suspend fun markNotificationPermissionAsked() {
            asked.value = true
        }
    }
}
