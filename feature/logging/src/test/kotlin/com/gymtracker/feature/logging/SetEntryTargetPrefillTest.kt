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
import kotlin.test.assertNull

/**
 * US-30: "Add set" prefills from a movement's target when the session copied one from a
 * routine, falling back to the member's last performed set — exactly as it always has — for
 * whichever field the target leaves unset, and for every field when there is no target at all.
 *
 * The session-side half of ADR-0027; the routine editor's own half is
 * `RoutineEditorViewModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetEntryTargetPrefillTest {
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
                startSessionFromRoutine = fakeStartSessionFromRoutine(),
                addExerciseToSession = AddExerciseToSession(sessionExercises) { SessionExerciseId("unused") },
                endSession = EndSession(repository, sets, restStore, clock),
                workoutDetail = WorkoutDetail(repository, sessionExercises, sets, catalog),
                recordSessionMetrics = fakeRecordSessionMetrics(repository),
                personalRecordsAchievedIn =
                    PersonalRecordsAchievedIn(
                        DetectPersonalRecord(
                            PersonalRecordsOf(repository, sessionExercises, sets, ZoneOffset.UTC),
                            ZoneOffset.UTC,
                        ),
                    ),
                detectPersonalRecord =
                    DetectPersonalRecord(
                        PersonalRecordsOf(repository, sessionExercises, sets, ZoneOffset.UTC),
                        ZoneOffset.UTC,
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

    /**
     * Seeds the session's one movement directly with [target] — the shape
     * `StartSessionFromRoutine` would have left it in, rather than routing through
     * `onExerciseChosen`, which is the catalog path and never carries a target (US-02's own
     * call site passes none, on purpose).
     */
    private suspend fun openEntryWithTarget(
        viewModel: ActiveSessionViewModel,
        target: MovementTarget?,
    ): SessionExerciseRow {
        sessionExercises.add(SessionExercise(SessionExerciseId("se-1"), SessionId("s1"), bench, 1, target))
        val row =
            viewModel.uiState
                .first { it.exercises.isNotEmpty() }
                .exercises
                .single()
        viewModel.setEntry.open(row)
        return row
    }

    private suspend fun entryOf(viewModel: ActiveSessionViewModel): SetEntry? = viewModel.uiState.first().setEntry

    @Test
    fun `with a target and no history, add set prefills from the target`() =
        runTest {
            units.set(WeightUnit.KG)
            val viewModel = viewModel()

            openEntryWithTarget(viewModel, MovementTarget(sets = 3, reps = 8, weightKg = 45.0))

            val entry = entryOf(viewModel)
            assertEquals("8", entry?.reps)
            assertEquals("45", entry?.weight)
            assertEquals(true, entry?.prefilled, "a target is a prefill, so this is not 'first time'")
        }

    @Test
    fun `history wins over a target for weight and reps, per ADR-0031`() =
        runTest {
            // US-37 supersedes US-30's target-first order: the last real set beats a target.
            val logged = ExerciseSet("s1", SessionExerciseId("se-last-week"), 1, 60.0, 5, null, now)
            sets.seed(logged)
            sets.lastFor[bench] = logged.id
            units.set(WeightUnit.KG)
            val viewModel = viewModel()

            openEntryWithTarget(viewModel, MovementTarget(sets = 3, reps = 8, weightKg = 999.0))

            val entry = entryOf(viewModel)
            assertEquals("5", entry?.reps, "history's reps must win, not the target's 8")
            assertEquals("60", entry?.weight, "history's weight must win, not the target's 999")
        }

    @Test
    fun `a target's weight fills in when history's set was bodyweight`() =
        runTest {
            // US-30: "3 x 8, load unrecorded is a plan" — a target's load still has a job when
            // history has no weight to prefer (US-37).
            val logged = ExerciseSet("s1", SessionExerciseId("se-last-week"), 1, null, 5, null, now)
            sets.seed(logged)
            sets.lastFor[bench] = logged.id
            units.set(WeightUnit.KG)
            val viewModel = viewModel()

            openEntryWithTarget(viewModel, MovementTarget(sets = 3, reps = 8, weightKg = 60.0))

            val entry = entryOf(viewModel)
            assertEquals("5", entry?.reps, "history's reps still win")
            assertEquals("60", entry?.weight, "history's set had no load, so the target's stands in")
        }

    @Test
    fun `with no target, add set prefills from history exactly as before`() =
        runTest {
            val logged = ExerciseSet("s1", SessionExerciseId("se-last-week"), 1, 60.0, 8, null, now)
            sets.seed(logged)
            sets.lastFor[bench] = logged.id
            units.set(WeightUnit.KG)
            val viewModel = viewModel()

            openEntryWithTarget(viewModel, target = null)

            val entry = entryOf(viewModel)
            assertEquals("8", entry?.reps)
            assertEquals("60", entry?.weight)
        }

    @Test
    fun `with neither a target nor history, reps float to 12, sets and weight stay at ADR-0009's defaults`() =
        runTest {
            // US-37 (ADR-0031): reps float to 12 rather than opening blank; weight never
            // floors — an invented load is worse than an empty field. Sets stays at 1 with no
            // target to floor it from, confirmed against TwoTapSetLoggingTest on-device.
            val viewModel = viewModel()

            openEntryWithTarget(viewModel, target = null)

            val entry = entryOf(viewModel)
            assertEquals("12", entry?.reps)
            assertEquals("1", entry?.sets)
            assertEquals("", entry?.weight)
            assertEquals(false, entry?.prefilled)
        }

    @Test
    fun `a target with only a rep count still prefills that much`() =
        runTest {
            // US-30: each field of a target is optional on its own.
            val viewModel = viewModel()

            openEntryWithTarget(viewModel, MovementTarget(sets = null, reps = 5, weightKg = null))

            assertEquals("5", entryOf(viewModel)?.reps)
            assertNull(entryOf(viewModel)?.weight?.ifBlank { null }, "no target load and no history: blank")
        }
}
