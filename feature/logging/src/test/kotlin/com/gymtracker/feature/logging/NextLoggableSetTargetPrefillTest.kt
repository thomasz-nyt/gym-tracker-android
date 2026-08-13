package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.progress.DetectPersonalRecord
import com.gymtracker.core.domain.progress.PersonalRecordsAchievedIn
import com.gymtracker.core.domain.progress.PersonalRecordsOf
import com.gymtracker.core.domain.rest.DetermineUpNextSet
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.session.EndSession
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
import com.gymtracker.core.domain.units.WeightUnit
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * US-30, US-35, US-37: the movement list's one-tap log button is a second prefill surface
 * beside `Add set` (ADR-0029), and the ViewModel's own doc on `nextLoggableSet` says both must
 * pick up history and a target the same way rather than drift apart — this is that other half,
 * mirroring `SetEntryTargetPrefillTest`. The precedence is history first (ADR-0031).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NextLoggableSetTargetPrefillTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val member = UserId("alice")
    private val bench = ExerciseId("bench")

    private val catalog = FakeCatalog()
    private val sessionExercises = FakeSessionExercises()
    private val sets = FakeSets(sessionOf = { id -> sessionExercises.all.firstOrNull { it.id == id }?.sessionId })
    private val units = FakeUnitPreference()
    private val restStore = FakeRestTimerStore()
    private var nextSet = 1

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        FakeSessions(listOf(session("s1"))).let { repository ->
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
                startSession = StartSession(repository, restStore, clock) { SessionId("new") },
                addExerciseToSession = AddExerciseToSession(sessionExercises) { SessionExerciseId("unused") },
                endSession = EndSession(repository, sets, clock),
                workoutDetail = WorkoutDetail(repository, sessionExercises, sets, catalog),
                personalRecordsAchievedIn =
                    PersonalRecordsAchievedIn(
                        DetectPersonalRecord(
                            PersonalRecordsOf(repository, sessionExercises, sets, ZoneOffset.UTC),
                            ZoneOffset.UTC,
                        ),
                    ),
                removeExerciseFromSession = RemoveExerciseFromSession(sessionExercises, sets),
                restoreExerciseToSession = RestoreExerciseToSession(sessionExercises, sets),
                determineUpNextSet = DetermineUpNextSet(sessionExercises, sets, PrefillFromLastSet(sets)),
                updateSet = UpdateSet(sets),
                deleteSet = DeleteSet(sets),
                restoreSet = RestoreSet(sets),
                guidedPlanStore = FakeGuidedPlanStore(),
                clock = clock,
            )
        }

    private fun session(id: String) =
        WorkoutSession(
            id = SessionId(id),
            userId = member,
            gymName = null,
            startedAt = now,
            endedAt = null,
            metrics = null,
        )

    @Test
    fun `the one-tap button prefills from the target when history has nothing`() =
        runTest {
            units.set(WeightUnit.KG)
            sessionExercises.add(
                SessionExercise(
                    SessionExerciseId("se-1"),
                    SessionId("s1"),
                    bench,
                    1,
                    MovementTarget(sets = 3, reps = 8, weightKg = 45.0),
                ),
            )
            val viewModel = viewModel()

            val next = viewModel.uiState.first { it.nextLoggableSet != null }.nextLoggableSet

            assertNotNull(next)
            assertEquals(8, next.prefill.reps)
            assertEquals(45.0, next.prefill.weight)
        }

    @Test
    fun `history wins over a target for the one-tap button, per ADR-0031`() =
        runTest {
            // US-37 supersedes US-30's target-first order: the last real set beats a target
            // for both reps and weight, even though a target also exists here.
            val logged = ExerciseSet("s1", SessionExerciseId("se-last-week"), 1, 60.0, 5, null, now)
            sets.seed(logged)
            sets.lastFor[bench] = logged.id
            units.set(WeightUnit.KG)
            sessionExercises.add(
                SessionExercise(
                    SessionExerciseId("se-1"),
                    SessionId("s1"),
                    bench,
                    1,
                    MovementTarget(sets = 3, reps = 8, weightKg = 999.0),
                ),
            )
            val viewModel = viewModel()

            val next = viewModel.uiState.first { it.nextLoggableSet != null }.nextLoggableSet

            assertNotNull(next)
            assertEquals(5, next.prefill.reps, "history's reps must win, not the target's 8")
            assertEquals(60.0, next.prefill.weight, "history's weight must win, not the target's 999")
        }

    @Test
    fun `the target's weight fills in when history's set was bodyweight`() =
        runTest {
            // history.reps always wins (non-null by SetPrefill's own contract); weight is the
            // one field that can genuinely be absent from a real set, so this is where a
            // target's weight still has a job under the new precedence.
            val logged = ExerciseSet("s1", SessionExerciseId("se-last-week"), 1, null, 5, null, now)
            sets.seed(logged)
            sets.lastFor[bench] = logged.id
            units.set(WeightUnit.KG)
            sessionExercises.add(
                SessionExercise(
                    SessionExerciseId("se-1"),
                    SessionId("s1"),
                    bench,
                    1,
                    MovementTarget(sets = 3, reps = 8, weightKg = 45.0),
                ),
            )
            val viewModel = viewModel()

            val next = viewModel.uiState.first { it.nextLoggableSet != null }.nextLoggableSet

            assertNotNull(next)
            assertEquals(5, next.prefill.reps, "history's reps still win")
            assertEquals(45.0, next.prefill.weight, "history's set had no load, so the target's stands in")
        }

    @Test
    fun `with no target and no history, there is no one-tap button`() =
        runTest {
            sessionExercises.add(SessionExercise(SessionExerciseId("se-1"), SessionId("s1"), bench, 1, null))
            val viewModel = viewModel()

            val state = viewModel.uiState.first { it.exercises.isNotEmpty() }

            assertNull(state.nextLoggableSet)
        }
}
