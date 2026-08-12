package com.gymtracker.feature.routines

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.routine.AddExerciseToRoutine
import com.gymtracker.core.domain.routine.CreateRoutine
import com.gymtracker.core.domain.routine.StartSessionFromRoutine
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import kotlin.test.assertTrue

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

/** US-29 as the routines list sees it. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {
    private val now: Instant = Instant.parse("2026-08-08T18:00:00Z")
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")

    private val items = FakeRoutineItems()
    private val routines = FakeRoutines(cascade = { items.cascadeDelete(it) })
    private val sessions = FakeSessions()
    private val sessionExercises = FakeSessionExercises()
    private var nextRoutine = 1
    private var nextItem = 1
    private var nextAppearance = 1

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val addToRoutine = AddExerciseToRoutine(items) { RoutineItemId("i-${nextItem++}") }

    private fun viewModel() =
        RoutinesViewModel(
            routines = routines,
            items = items,
            currentMember = FakeCurrentMember(alice),
            createRoutine = CreateRoutine(routines) { RoutineId("r-${nextRoutine++}") },
            startSessionFromRoutine =
                StartSessionFromRoutine(
                    routines = routines,
                    items = items,
                    startSession =
                        StartSession(sessions, FakeRestTimerStore(), Clock.fixed(now, ZoneOffset.UTC)) {
                            SessionId("s-1")
                        },
                    addExerciseToSession =
                        AddExerciseToSession(sessionExercises) { SessionExerciseId("se-${nextAppearance++}") },
                ),
        )

    @Test
    fun `an empty list says so rather than showing nothing`() =
        runTest {
            viewModel().uiState.test {
                val state = awaitItem()
                assertTrue(state.routines.isEmpty())
                assertEquals(false, state.isLoading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `routines are listed in order with their movement counts`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onCreateRoutine("Upper A")
            addToRoutine(RoutineId("r-1"), bench)
            addToRoutine(RoutineId("r-1"), squat)
            viewModel.onCreateRoutine("Lower B")

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(listOf("Upper A", "Lower B"), state.routines.map { it.routine.name })
                assertEquals(listOf(2, 0), state.routines.map { it.movements })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a routine with no movements is still offered, and says it is empty`() =
        runTest {
            // The US-13 absence pattern rather than hiding it: you just made it, and hiding it
            // would look like the create failed.
            val viewModel = viewModel()
            viewModel.onCreateRoutine("New routine")

            viewModel.uiState.test {
                assertEquals(0, expectMostRecentItem().routines.single().movements)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `starting a routine copies its movements into a new session`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onCreateRoutine("Upper A")
            addToRoutine(RoutineId("r-1"), bench)
            addToRoutine(RoutineId("r-1"), squat)

            viewModel.onStartRoutine(RoutineId("r-1"))

            assertEquals(1, sessions.all.size)
            assertEquals(
                listOf(bench, squat),
                sessionExercises.forSession(SessionId("s-1")).map { it.exerciseId },
            )
        }

    @Test
    fun `starting reports that the workout began, so the screen can go to it`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onCreateRoutine("Upper A")

            viewModel.onStartRoutine(RoutineId("r-1"))

            assertEquals(RoutineStart.Started, viewModel.startOutcome.value)
        }

    @Test
    fun `starting while a workout is running says so instead of pouring into it`() =
        runTest {
            sessions.startSession(WorkoutSession(SessionId("running"), alice, null, now, null, null))
            val viewModel = viewModel()
            viewModel.onCreateRoutine("Upper A")
            addToRoutine(RoutineId("r-1"), bench)

            viewModel.onStartRoutine(RoutineId("r-1"))

            assertEquals(RoutineStart.AlreadyRunning, viewModel.startOutcome.value)
            assertTrue(
                sessionExercises.forSession(SessionId("running")).isEmpty(),
                "the running workout gained nothing",
            )
        }

    @Test
    fun `the outcome is cleared once the screen has acted on it`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onCreateRoutine("Upper A")
            viewModel.onStartRoutine(RoutineId("r-1"))

            viewModel.onStartHandled()

            assertEquals(null, viewModel.startOutcome.value, "so rotating the phone does not navigate twice")
        }
}
