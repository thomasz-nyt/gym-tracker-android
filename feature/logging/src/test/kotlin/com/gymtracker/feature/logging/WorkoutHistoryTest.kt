package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.EndSession
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionHistory
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.session.WorkoutDetail
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import com.gymtracker.core.domain.sessionexercise.FinishExercise
import com.gymtracker.core.domain.sessionexercise.RemoveExerciseFromSession
import com.gymtracker.core.domain.sessionexercise.RestoreExerciseToSession
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Past workouts: what one contained (US-06b), and deleting one with undo (US-06a).
 *
 * Split out of `ActiveSessionViewModelTest` when it outgrew detekt's size limit. The fakes are
 * shared, in `LoggingFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val member = UserId("alice")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun finished(
        id: String,
        startedAt: Instant,
    ) = WorkoutSession(
        id = SessionId(id),
        userId = member,
        gymName = null,
        startedAt = startedAt,
        endedAt = startedAt.plus(Duration.ofHours(1)),
        metrics = null,
    )

    /** An active session, for the tests that finish one. */
    private fun session(
        id: String,
        startedAt: Instant = now,
        endedAt: Instant? = null,
    ) = WorkoutSession(
        id = SessionId(id),
        userId = member,
        gymName = null,
        startedAt = startedAt,
        endedAt = endedAt,
        metrics = null,
    )

    private val catalog = FakeCatalog()

    // The cascade runs at call time, by which point `sets` below is initialised.
    // Explicit types on both: they reference each other — the cascade needs `sets`, and
    // `sets` finds a set's session through `sessionExercises` — and Kotlin cannot infer
    // either end of a cycle.
    private val sessionExercises: FakeSessionExercises =
        FakeSessionExercises(cascade = { id -> sets.cascadeDeleteExercise(id) })
    private val sets: FakeSets =
        FakeSets(sessionOf = { id -> sessionExercises.all.firstOrNull { it.id == id }?.sessionId })
    private val units = FakeUnitPreference()
    private val restStore = FakeRestTimerStore()
    private val guidedStore = FakeGuidedPlanStore()
    private var nextSessionExercise = 1
    private var nextSet = 1

    /**
     * Sessions wired to delete their children with them, as `ON DELETE CASCADE` does in Room
     * (ADR-0012). Nothing in the domain deletes them explicitly, so nothing in the fake should.
     */
    private fun sessionsOf(vararg initial: WorkoutSession) =
        FakeSessions(initial.toList()) { id ->
            // Sets first: this fake finds a set's session by looking its appearance up in
            // sessionExercises, so clearing that first would leave the sets unreachable and
            // therefore undeleted. SQLite has the real graph and does not care about order.
            sets.cascadeDelete(id)
            sessionExercises.cascadeDelete(id)
        }

    private fun viewModel(repository: FakeSessions) =
        ActiveSessionViewModel(
            sessions = repository,
            sessionExercises = sessionExercises,
            catalog = catalog,
            currentMember = FakeCurrentMember(member),
            sets = sets,
            logSets = LogSets(LogSet(sets, sessionExercises, clock) { "set-${nextSet++}" }),
            restTimer = RestTimer(restStore, clock),
            restTimerStore = restStore,
            prefillFromLastSet = PrefillFromLastSet(sets),
            unitPreference = units,
            startSession = StartSession(repository, clock) { SessionId("new") },
            addExerciseToSession =
                AddExerciseToSession(sessionExercises) { SessionExerciseId("se-${nextSessionExercise++}") },
            endSession = EndSession(repository, sets, clock),
            sessionHistory = SessionHistory(repository, sessionExercises, sets),
            workoutDetail = WorkoutDetail(repository, sessionExercises, sets, catalog),
            deleteSession = DeleteSession(repository, sessionExercises, sets),
            restoreSession = RestoreSession(repository, sessionExercises, sets),
            removeExerciseFromSession = RemoveExerciseFromSession(sessionExercises, sets),
            restoreExerciseToSession = RestoreExerciseToSession(sessionExercises, sets),
            finishExercise = FinishExercise(sessionExercises, clock),
            guidedPlanStore = guidedStore,
            clock = clock,
        )

    @Test
    fun `opening a past workout shows what it contained`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0, 60.0))
            val viewModel = viewModel(repository)
            viewModel.history.open()

            viewModel.history.openWorkout(SessionId("last-week"))

            viewModel.uiState.test {
                val detail = checkNotNull(expectMostRecentItem().history.detail)
                val performed = detail.exercises.single()
                assertEquals("Bench Press", performed.exercise?.name)
                assertEquals(2, performed.sets.size)
                assertEquals(listOf(2), performed.groups.map { it.count }, "identical sets group")
            }
        }

    @Test
    fun `nothing is read until a workout is actually opened`() =
        runTest {
            // History is a side trip, and the detail is a side trip from that. The core loop
            // should pay for neither.
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0))
            val viewModel = viewModel(repository)

            viewModel.history.open()

            viewModel.uiState.test {
                assertNull(expectMostRecentItem().history.detail)
            }
        }

    @Test
    fun `closing a workout goes back to the list, not out of history`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0))
            val viewModel = viewModel(repository)
            viewModel.history.open()
            viewModel.history.openWorkout(SessionId("last-week"))

            viewModel.history.closeWorkout()

            viewModel.uiState.test {
                val history = expectMostRecentItem().history
                assertNull(history.detail)
                assertEquals(true, history.isOpen)
            }
        }

    // ---- US-06a: deleting a past workout ---------------------------------------------------

    @Test
    fun `deleting a past workout removes it and its sets, and offers undo`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0))
            val viewModel = viewModel(repository)
            viewModel.history.open()

            viewModel.history.delete(SessionId("last-week"))

            viewModel.uiState.test {
                val history = expectMostRecentItem().history
                assertEquals(emptyList(), history.sessions)
                assertEquals(true, history.canUndo)
            }
            assertEquals(emptyList(), repository.all)
            assertEquals(emptyList(), sets.all, "the sets went with it")
        }

    @Test
    fun `undo brings the workout back with its sets`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0, 62.5))
            val viewModel = viewModel(repository)
            viewModel.history.open()
            viewModel.history.delete(SessionId("last-week"))

            viewModel.history.undo()

            viewModel.uiState.test {
                val history = expectMostRecentItem().history
                assertEquals(listOf(SessionId("last-week")), history.sessions.map { it.session.id })
                assertEquals(2, history.sessions.single().setCount)
                assertEquals(false, history.canUndo, "there is nothing left to undo")
            }
        }

    @Test
    fun `undo expires after five seconds`() =
        runTest {
            // US-04's window, reused for US-06a so the two destructive actions behave alike.
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            val viewModel = viewModel(repository)
            viewModel.history.open()
            viewModel.history.delete(SessionId("last-week"))

            advanceTimeBy(Duration.ofSeconds(5).toMillis() + 1)

            viewModel.uiState.test {
                assertEquals(false, expectMostRecentItem().history.canUndo)
            }
        }

    @Test
    fun `undo after the window has passed does nothing`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            val viewModel = viewModel(repository)
            viewModel.history.open()
            viewModel.history.delete(SessionId("last-week"))
            advanceTimeBy(Duration.ofSeconds(5).toMillis() + 1)

            viewModel.history.undo()

            assertEquals(emptyList(), repository.all, "the delete stands")
        }

    @Test
    fun `deleting the workout holding my last set changes what the next set prefills with`() =
        runTest {
            // US-06a's last criterion. The prefill reads the database, so a deleted set cannot
            // come back through it — which is the whole point of deleting test data.
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))), session("today"))
            seedWorkout(SessionId("last-week"), weights = listOf(61.23))
            sets.lastFor[ExerciseId("bench")] = "seed-0"
            val viewModel = viewModel(repository)
            viewModel.history.open()

            viewModel.history.delete(SessionId("last-week"))
            viewModel.history.close()
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)

                assertEquals("", expectMostRecentItem().setEntry?.weight, "the deleted set is gone for good")
            }
        }

    @Test
    fun `closing history returns to the session screen`() =
        runTest {
            val viewModel = viewModel(sessionsOf(session("s1")))
            viewModel.history.open()

            viewModel.history.close()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(false, state.history.isOpen)
                assertEquals(SessionId("s1"), state.activeSession?.id)
            }
        }

    /** One exercise in [session], with a set for each weight given. */
    private suspend fun seedWorkout(
        session: SessionId,
        weights: List<Double?>,
    ) {
        val appearance = SessionExercise(SessionExerciseId("seed-se"), session, ExerciseId("bench"), 1)
        sessionExercises.add(appearance)
        weights.forEachIndexed { index, weight ->
            sets.add(ExerciseSet("seed-$index", appearance.id, index + 1, weight, 10, null, now))
        }
    }

    // ---- US-06: finishing a workout, and history -------------------------------------------

    @Test
    fun `finishing a workout with sets ends it and returns to home`() =
        runTest {
            val repository = sessionsOf(session("s1"))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()

                viewModel.onFinishWorkout()

                assertNull(expectMostRecentItem().activeSession, "US-06 returns me to home")
            }
            assertEquals(now, repository.all.single().endedAt)
        }

    @Test
    fun `finishing a workout with no sets discards it rather than saving it`() =
        runTest {
            val repository = sessionsOf(session("s1"))
            val viewModel = viewModel(repository)

            viewModel.onFinishWorkout()

            assertEquals(emptyList(), repository.all, "US-06: an empty session is not history")
        }

    @Test
    fun `finishing with no session running does nothing`() =
        runTest {
            val repository = sessionsOf()
            val viewModel = viewModel(repository)

            viewModel.onFinishWorkout()

            assertEquals(emptyList(), repository.all)
        }

    @Test
    fun `history lists finished workouts with their counts and volume`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0, 60.0))
            val viewModel = viewModel(repository)

            viewModel.history.open()

            viewModel.uiState.test {
                val history = expectMostRecentItem().history
                assertEquals(true, history.isOpen)
                val row = history.sessions.single()
                assertEquals(SessionId("last-week"), row.session.id)
                assertEquals(1, row.exerciseCount)
                assertEquals(2, row.setCount)
                assertEquals(1200.0, row.volumeKg)
            }
        }

    @Test
    fun `the workout in progress is not offered for deletion`() =
        runTest {
            val repository = sessionsOf(session("today"), finished("last-week", now.minus(Duration.ofDays(7))))
            val viewModel = viewModel(repository)

            viewModel.history.open()

            viewModel.uiState.test {
                assertEquals(
                    listOf(SessionId("last-week")),
                    expectMostRecentItem().history.sessions.map { it.session.id },
                )
            }
        }
}
