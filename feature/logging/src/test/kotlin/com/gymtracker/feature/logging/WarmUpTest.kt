package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.warmup.WarmUpTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-28 as the screen sees it.
 *
 * What is asserted here is the *commands* — that starting records a start, that starting
 * twice does not throw away the eight minutes you are already into, that stopping discards
 * it, and above all that none of it writes a row. The counting itself belongs to
 * [com.gymtracker.core.domain.warmup.WarmUpTimer] and is asserted there against a fixed
 * clock; the per-second tick in the ViewModel is a redraw signal, not a source of truth.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WarmUpTest {
    private val now: Instant = Instant.parse("2026-08-08T18:00:00Z")
    private val member = UserId("alice")
    private val clock = MovableClock(now)
    private val store = FakeWarmUpTimerStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = WarmUpViewModel(WarmUpTimer(store, clock))

    @Test
    fun `no warm-up is running when the screen opens`() =
        runTest {
            viewModel().elapsed.test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `starting a warm-up records when it began`() =
        runTest {
            viewModel().onStartWarmUp()

            assertEquals(now, store.warmUpStartedAt.first())
        }

    @Test
    fun `starting again does not throw away a warm-up already running`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onStartWarmUp()

            clock.now = now.plusSeconds(480)
            viewModel.onStartWarmUp()

            assertEquals(now, store.warmUpStartedAt.first(), "still the original start, eight minutes in")
        }

    @Test
    fun `stopping discards it`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onStartWarmUp()

            viewModel.onStopWarmUp()

            assertNull(store.warmUpStartedAt.first())
        }

    @Test
    fun `stopping a warm-up that is not running is harmless`() =
        runTest {
            viewModel().onStopWarmUp()

            assertNull(store.warmUpStartedAt.first())
        }

    @Test
    fun `a warm-up writes no session, no exercise and no set`() =
        runTest {
            // ADR-0021 and constitution §1: a warm-up is not a second kind of thing a session
            // can contain. This is structural — WarmUpViewModel is not given a repository to
            // write to — and the assertion is here so that giving it one, and writing, breaks
            // a test rather than quietly introducing an activity type.
            val sessionExercises = FakeSessionExercises()
            val sets = FakeSets(sessionOf = { id -> sessionExercises.all.firstOrNull { it.id == id }?.sessionId })
            val sessions =
                FakeSessions(
                    listOf(
                        WorkoutSession(
                            id = SessionId("s1"),
                            userId = member,
                            gymName = null,
                            startedAt = now,
                            endedAt = null,
                            metrics = null,
                        ),
                    ),
                )
            sessionExercises.add(SessionExercise(SessionExerciseId("se-1"), SessionId("s1"), ExerciseId("bench"), 1))
            val before = Triple(sessions.all, sessionExercises.all, sets.all)

            val viewModel = viewModel()
            viewModel.onStartWarmUp()
            clock.now = now.plusSeconds(480)
            viewModel.onStopWarmUp()

            assertEquals(before.first, sessions.all, "the session is untouched — no duration, no column")
            assertEquals(before.second, sessionExercises.all, "no session_exercises row")
            assertEquals(before.third, sets.all, "no sets row")
            assertTrue(sets.all.isEmpty(), "eight minutes of treadmill logged nothing, which is the point")
        }

    /** A clock the test can move, standing in for time actually passing. */
    private class MovableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }
}
