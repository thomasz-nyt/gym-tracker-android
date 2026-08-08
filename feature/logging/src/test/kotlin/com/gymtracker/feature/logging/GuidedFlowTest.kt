package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
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
import com.gymtracker.core.domain.sessionexercise.RemoveExerciseFromSession
import com.gymtracker.core.domain.sessionexercise.RestoreExerciseToSession
import com.gymtracker.core.domain.set.DeleteSet
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.set.RestoreSet
import com.gymtracker.core.domain.set.UpdateSet
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
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-05a: being walked through an exercise, set by set.
 *
 * Its own class rather than more of `ActiveSessionViewModelTest`, which had outgrown detekt's
 * size limit. The fakes are shared, in `LoggingFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidedFlowTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
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
            startedAt = now,
            endedAt = null,
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

    private fun viewModel(repository: FakeSessions) =
        ActiveSessionViewModel(
            sessions = repository,
            sessionExercises = sessionExercises,
            catalog = catalog,
            currentMember = FakeCurrentMember(member),
            sets = sets,
            logSets = LogSets(LogSet(sets, clock) { "set-${nextSet++}" }),
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
            updateSet = UpdateSet(sets),
            deleteSet = DeleteSet(sets),
            restoreSet = RestoreSet(sets),
            guidedPlanStore = guidedStore,
            clock = clock,
        )

    /**
     * Starts the flow on a freshly added bench press, with a target of [targetSets] × [targetReps].
     *
     * The row is built from the repositories rather than read off `uiState`, which is
     * `WhileSubscribed` and therefore holds the placeholder whenever nothing is collecting —
     * the same trap `onExerciseChosen` documents.
     */
    private suspend fun beginGuided(
        viewModel: ActiveSessionViewModel,
        targetSets: String = "3",
        targetReps: String = "12",
        weight: String = "135",
    ): SessionExerciseRow {
        viewModel.onExerciseChosen(ExerciseId("bench"))
        val appearance = sessionExercises.all.last { it.exerciseId == ExerciseId("bench") }
        val row =
            SessionExerciseRow(
                sessionExercise = appearance,
                exercise = null,
                sets = sets.all.filter { it.sessionExerciseId == appearance.id },
            )

        viewModel.onStartExercise(row)
        viewModel.guided.changeSetup(weight = weight, reps = targetReps, sets = targetSets)
        viewModel.guided.begin()
        return row
    }

    @Test
    fun `starting an exercise offers the target, prefilled as set entry would be`() =
        runTest {
            sets.seed(ExerciseSet("old", SessionExerciseId("se-old"), 1, 61.23, 8, null, now))
            sets.lastFor[ExerciseId("bench")] = "old"
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.onStartExercise(row)

                val setup = checkNotNull(expectMostRecentItem().guided.setup)
                assertEquals("Bench Press", setup.exerciseName)
                assertEquals("135", setup.weight, "prefilled in the member's unit, as US-03 does")
                assertEquals("8", setup.reps)
                assertEquals("1", setup.sets)
            }
        }

    @Test
    fun `the flow counts the sets it has walked through`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel)

            viewModel.uiState.test {
                val running = checkNotNull(expectMostRecentItem().guided.running)
                assertEquals(0, running.setsDone)
                assertEquals(3, running.targetSets)
                assertEquals(12, running.targetReps)
                assertEquals(false, running.isComplete)
            }

            viewModel.guided.finishSet()

            viewModel.uiState.test {
                assertEquals(1, checkNotNull(expectMostRecentItem().guided.running).setsDone)
            }
        }

    @Test
    fun `finishing a set writes exactly one, with its own timestamp`() =
        runTest {
            // The point of ADR-0017 over ADR-0009: N-at-once shares one performed_at, "the
            // time they were recorded, not a guess at when each was performed". Here each set
            // is logged as it happens.
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel)

            viewModel.guided.finishSet()

            assertEquals(1, sets.all.size)
            assertEquals(12, sets.all.single().reps)
            assertEquals(61.23, sets.all.single().weightKg!!, 0.01, "135 lb in canonical kilograms")
        }

    @Test
    fun `an edited rep count is what gets logged, not the target`() =
        runTest {
            // Constitution §2.4. Planning 12 and managing 9 must record 9; the target is a
            // prefill, never a promise.
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel, targetReps = "12")

            viewModel.guided.changeReps("9")
            viewModel.guided.finishSet()

            assertEquals(listOf(9), sets.all.map { it.reps })
        }

    @Test
    fun `the rep field resets to the target after each set`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel, targetReps = "12")

            viewModel.guided.changeReps("9")
            viewModel.guided.finishSet()

            viewModel.uiState.test {
                assertEquals("12", checkNotNull(expectMostRecentItem().guided.running).reps)
            }
        }

    @Test
    fun `finishing a set starts the rest, exactly as logging one manually does`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel)

            viewModel.guided.finishSet()

            assertEquals(now.plusSeconds(60), restStore.restEndsAt.first())
        }

    @Test
    fun `the exercise completes once the target is met`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel, targetSets = "2")

            viewModel.guided.finishSet()
            viewModel.guided.finishSet()

            viewModel.uiState.test {
                val running = checkNotNull(expectMostRecentItem().guided.running)
                assertEquals(true, running.isComplete)
                assertEquals(2, running.setsDone)
                assertEquals(61.23 * 12 * 2, running.volumeKg!!, 0.1)
            }
        }

    @Test
    fun `the next exercise offered is one with nothing logged against it`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("squat"))
            beginGuided(viewModel, targetSets = "1")

            viewModel.guided.finishSet()

            viewModel.uiState.test {
                val running = checkNotNull(expectMostRecentItem().guided.running)
                assertEquals(true, running.isComplete)
                assertEquals(ExerciseId("squat"), running.nextUp?.sessionExercise?.exerciseId)
            }
        }

    @Test
    fun `nothing is offered next when every exercise has been logged`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel, targetSets = "1")

            viewModel.guided.finishSet()

            viewModel.uiState.test {
                assertNull(checkNotNull(expectMostRecentItem().guided.running).nextUp)
            }
        }

    @Test
    fun `stopping leaves every set logged so far exactly where it is`() =
        runTest {
            // Guided mode is a lens over the session, never a separate place the data lives.
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel, targetSets = "3")
            viewModel.guided.finishSet()

            viewModel.guided.stop()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertNull(state.guided.running)
                val only = state.exercises.single()
                assertEquals(1, only.sets.size)
            }
        }

    @Test
    fun `an exercise part-logged by hand does not read as already finished`() =
        runTest {
            // setsAtStart is why progress is a subtraction rather than a count.
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val existing = sessionExercises.all.single()
            sets.seed(ExerciseSet("by-hand", existing.id, 1, 60.0, 10, null, now))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.onStartExercise(row)
                viewModel.guided.changeSetup(weight = "135", reps = "12", sets = "2")
                viewModel.guided.begin()

                val running = checkNotNull(expectMostRecentItem().guided.running)
                assertEquals(0, running.setsDone, "the set logged by hand is not one of these two")
                assertEquals(false, running.isComplete)
            }
        }

    @Test
    fun `a target that will not parse does not begin the flow`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.onStartExercise(row)
                viewModel.guided.changeSetup(reps = "", sets = "3")
                viewModel.guided.begin()

                assertNull(expectMostRecentItem().guided.running)
            }
            assertEquals(emptyList(), sets.all)
        }
}
