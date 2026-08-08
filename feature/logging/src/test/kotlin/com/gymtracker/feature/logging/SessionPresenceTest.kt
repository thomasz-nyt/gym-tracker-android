package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * The bottom bar's hide condition (ADR-0024): "is a workout running", read straight from Room
 * rather than through `ActiveSessionViewModel`.
 *
 * This is the smallest slice of that ViewModel's `activeSession` signal, lifted out so the
 * NavHost can decide whether to show the bar without depending on the screen it drives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPresenceTest {
    private val member = UserId("alice")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun session(id: String) =
        WorkoutSession(
            id = SessionId(id),
            userId = member,
            gymName = null,
            startedAt = Instant.parse("2026-08-07T18:00:00Z"),
            endedAt = null,
            metrics = null,
        )

    @Test
    fun `no active session reports false`() =
        runTest {
            val viewModel = SessionPresenceViewModel(FakeSessions(), FakeCurrentMember(member))

            viewModel.hasActiveSession.test {
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `an active session reports true`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = SessionPresenceViewModel(repository, FakeCurrentMember(member))

            viewModel.hasActiveSession.test {
                assertEquals(true, expectMostRecentItem())
            }
        }

    @Test
    fun `finishing the session flips it back to false`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = SessionPresenceViewModel(repository, FakeCurrentMember(member))

            viewModel.hasActiveSession.test {
                assertEquals(true, expectMostRecentItem())

                repository.endSession(SessionId("s1"), Instant.parse("2026-08-07T19:00:00Z"))

                assertEquals(false, expectMostRecentItem())
            }
        }
}
