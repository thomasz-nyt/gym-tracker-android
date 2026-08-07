package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * US-02d: marking an exercise done — the mark, the partition, and the set that takes it back.
 *
 * Its own class for ADR-0017's reason: the fakes are shared in `LoggingFakes.kt`, and
 * `ActiveSessionViewModelTest` is at detekt's size limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinishedExerciseTest {
    private val start: Instant = Instant.parse("2026-08-04T18:00:00Z")

    /** Settable, unlike `Clock.fixed`: two marks in a row must not tie on `finished_at`. */
    private var now: Instant = start
    private val clock: Clock =
        object : Clock() {
            override fun instant(): Instant = now

            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = this
        }

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
            startedAt = start,
            endedAt = null,
            metrics = null,
        )

    private val catalog = FakeCatalog()
    private val sessionExercises: FakeSessionExercises =
        FakeSessionExercises(cascade = { id -> sets.cascadeDeleteExercise(id) })
    private val sets: FakeSets =
        FakeSets(sessionOf = { id -> sessionExercises.all.firstOrNull { it.id == id }?.sessionId })
    private val units = FakeUnitPreference()
    private val restStore = FakeRestTimerStore()
    private val guidedStore = FakeGuidedPlanStore()
    private var nextSessionExercise = 1
    private var nextSet = 1

    private fun viewModel(repository: FakeSessions = FakeSessions(listOf(session("s1")))) =
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

    private fun SessionExerciseRow.exercise() = sessionExercise.exerciseId.value

    private fun List<SessionExerciseRow>.ids() = map { it.exercise() }

    @Test
    fun `marking done moves the exercise below the ones in progress`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExercisesChosen(listOf(ExerciseId("bench"), ExerciseId("squat")))

            viewModel.uiState.test {
                val rows = expectMostRecentItem().exercises
                assertEquals(listOf("squat", "bench"), rows.ids(), "newest first, per US-02b")

                viewModel.onToggleFinished(rows.first())

                val after = expectMostRecentItem().exercises
                assertEquals(listOf("bench", "squat"), after.ids(), "done sinks below in progress")
                assertEquals(now, after.last().sessionExercise.finishedAt)
            }
        }

    @Test
    fun `the finished group reads newest finished first`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExercisesChosen(listOf(ExerciseId("bench"), ExerciseId("squat"), ExerciseId("curl")))

            viewModel.uiState.test {
                val rows = expectMostRecentItem().exercises
                assertEquals(listOf("curl", "squat", "bench"), rows.ids())

                // Finish bench first, then curl: the one finished last sits nearest the
                // in-progress group — recent floats up in both halves.
                viewModel.onToggleFinished(rows.single { it.exercise() == "bench" })
                now = now.plusSeconds(60)
                viewModel.onToggleFinished(
                    expectMostRecentItem().exercises.single { it.exercise() == "curl" },
                )

                assertEquals(listOf("squat", "curl", "bench"), expectMostRecentItem().exercises.ids())
            }
        }

    @Test
    fun `the toggle takes a mis-tap back`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExercisesChosen(listOf(ExerciseId("bench")))

            viewModel.uiState.test {
                viewModel.onToggleFinished(expectMostRecentItem().exercises.single())
                val marked = expectMostRecentItem().exercises.single()
                assertNotNull(marked.sessionExercise.finishedAt)

                viewModel.onToggleFinished(marked)

                val unmarked = expectMostRecentItem().exercises.single()
                assertNull(unmarked.sessionExercise.finishedAt)
            }
        }

    @Test
    fun `logging a set against a done exercise puts it back in progress`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExercisesChosen(listOf(ExerciseId("bench"), ExerciseId("squat")))

            viewModel.uiState.test {
                viewModel.onToggleFinished(expectMostRecentItem().exercises.single { it.exercise() == "bench" })

                // The machine freed up: two taps later there is a new bench set, and the
                // mark must not outlive it (ADR-0019).
                viewModel.setEntry.open(expectMostRecentItem().exercises.last())
                viewModel.setEntry.change(weight = "135", reps = "10")
                viewModel.setEntry.confirm()

                val after = expectMostRecentItem().exercises
                assertNull(after.single { it.exercise() == "bench" }.sessionExercise.finishedAt)
                assertEquals(
                    listOf("squat", "bench"),
                    after.ids(),
                    "back in the in-progress group, in its US-02b place — squat is still the newest added",
                )
            }
        }

    @Test
    fun `completing a guided walkthrough marks the exercise done`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val appearance = sessionExercises.all.single()

            viewModel.onStartExercise(SessionExerciseRow(appearance, exercise = null, sets = emptyList()))
            viewModel.guided.changeSetup(weight = "135", reps = "12", sets = "1")
            viewModel.guided.begin()
            viewModel.guided.finishSet()

            assertEquals(now, sessionExercises.all.single().finishedAt)
        }

    @Test
    fun `leaving the guided walkthrough early marks nothing`() =
        runTest {
            // US-05a: leaving "returns me to the session with every set logged so far
            // intact" — abandoning is not finishing (US-02d).
            val viewModel = viewModel()
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val appearance = sessionExercises.all.single()

            viewModel.onStartExercise(SessionExerciseRow(appearance, exercise = null, sets = emptyList()))
            viewModel.guided.changeSetup(weight = "135", reps = "12", sets = "3")
            viewModel.guided.begin()
            viewModel.guided.finishSet()
            viewModel.guided.stop()

            assertNull(sessionExercises.all.single().finishedAt)
            assertEquals(1, sets.all.size, "the set logged before leaving stays")
        }
}
