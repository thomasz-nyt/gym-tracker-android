package com.gymtracker.core.domain.warmup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-28 / ADR-0021: the warm-up counts up, and it is a stored *start* time.
 *
 * The mirror image of [com.gymtracker.core.domain.rest.RestTimer], which stores an end
 * time because it counts down. Both store an absolute instant for the same reason: the
 * process dying is then a non-event.
 */
class WarmUpTimerTest {
    private val now: Instant = Instant.parse("2026-08-08T18:00:00Z")
    private val store = FakeWarmUpTimerStore()

    private fun timer(at: Instant = now) = WarmUpTimer(store, Clock.fixed(at, ZoneOffset.UTC))

    @Test
    fun `no warm-up is running until one is started`() =
        runTest {
            assertNull(timer().elapsed().first())
        }

    @Test
    fun `starting stores when it began, not how long it has run`() =
        runTest {
            timer().start()

            assertEquals(now, store.warmUpStartedAt.first())
        }

    @Test
    fun `a warm-up that has just started reads as zero, not as absent`() =
        runTest {
            // Null means "not running". A warm-up one millisecond old is running, so
            // ZERO is the honest answer and null would be a lie.
            timer().start()

            assertEquals(Duration.ZERO, timer().elapsed().first())
        }

    @Test
    fun `elapsed time is derived from the clock`() =
        runTest {
            timer().start()

            val after4m12s = timer(now.plusSeconds(252)).elapsed().first()

            assertEquals(Duration.ofSeconds(252), after4m12s)
        }

    @Test
    fun `a warm-up running when the app was killed keeps counting across the gap`() =
        runTest {
            // The point of storing a start instant: reopening shows the time that has
            // actually passed, not zero and not a paused value (US-28).
            timer().start()

            assertEquals(Duration.ofMinutes(8), timer(now.plus(Duration.ofMinutes(8))).elapsed().first())
        }

    @Test
    fun `starting again while one is running does not reset it`() =
        runTest {
            timer().start()

            timer(now.plusSeconds(90)).start()

            assertEquals(now, store.warmUpStartedAt.first(), "still the original start")
            assertEquals(Duration.ofSeconds(90), timer(now.plusSeconds(90)).elapsed().first())
        }

    @Test
    fun `stopping clears it`() =
        runTest {
            val timer = timer()
            timer.start()

            timer.stop()

            assertNull(store.warmUpStartedAt.first())
            assertNull(timer.elapsed().first())
        }

    @Test
    fun `a warm-up stopped and started again begins from zero`() =
        runTest {
            timer().start()
            timer().stop()

            timer(now.plusSeconds(600)).start()

            assertEquals(Duration.ZERO, timer(now.plusSeconds(600)).elapsed().first())
        }

    @Test
    fun `the warm-up has no end, so it keeps counting well past any rest`() =
        runTest {
            // There is no target and nothing to expire: ADR-0021 gives it no duration to
            // finish, which is what keeps it from being a session step.
            timer().start()

            assertEquals(Duration.ofHours(2), timer(now.plus(Duration.ofHours(2))).elapsed().first())
        }

    private class FakeWarmUpTimerStore : WarmUpTimerStore {
        private val startedAt = MutableStateFlow<Instant?>(null)

        override val warmUpStartedAt = startedAt

        override suspend fun setWarmUpStartedAt(instant: Instant?) {
            startedAt.value = instant
        }
    }
}
