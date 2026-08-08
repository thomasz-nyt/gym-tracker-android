package com.gymtracker.feature.logging

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

/**
 * The +/− steppers either side of each number in set entry (ADR-0016).
 *
 * The gym reason they exist: the common edit between sets is one plate up or one rep down, and
 * that used to cost a keyboard. The rules worth pinning are what a step *means* in the member's
 * own unit, and the two floors the domain already had — reps never below 1 (US-03), and a
 * weight that steps down past the bottom becoming blank rather than zero, because a bodyweight
 * set is an absence and not a load of nothing (constitution §2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetEntryStepperTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val member = UserId("alice")

    private val catalog = FakeCatalog()
    private val sessionExercises = FakeSessionExercises()
    private val sets = FakeSets(sessionOf = { id -> sessionExercises.all.firstOrNull { it.id == id }?.sessionId })
    private val units = FakeUnitPreference()
    private val restStore = FakeRestTimerStore()
    private var nextSessionExercise = 1
    private var nextSet = 1

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
                guidedPlanStore = FakeGuidedPlanStore(),
                clock = clock,
            )
        }

    /** Opens set entry on a fresh session, ready for a stepper to be pressed. */
    private suspend fun openEntry(viewModel: ActiveSessionViewModel): SessionExerciseRow {
        viewModel.onExerciseChosen(ExerciseId("bench"))
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
    fun `stepping weight up from blank starts at one increment of the members unit`() =
        runTest {
            // The fake member reads pounds, so a step is 5 lb rather than 2.5 kg (ADR-0016).
            val viewModel = viewModel()
            openEntry(viewModel)

            viewModel.setEntry.stepWeight(1)

            assertEquals("5", entryOf(viewModel)?.weight)
        }

    @Test
    fun `stepping weight up in kilograms moves by two and a half`() =
        runTest {
            units.set(WeightUnit.KG)
            val viewModel = viewModel()
            openEntry(viewModel)

            viewModel.setEntry.stepWeight(1)
            viewModel.setEntry.stepWeight(1)

            assertEquals("5", entryOf(viewModel)?.weight, "two 2.5 kg steps, with no trailing zero")
        }

    @Test
    fun `a half-step weight rounds onto the increment rather than off it`() =
        runTest {
            units.set(WeightUnit.KG)
            val viewModel = viewModel()
            openEntry(viewModel)
            viewModel.setEntry.change(weight = "61.23")

            viewModel.setEntry.stepWeight(1)

            // 61.23 is 135 lb typed by someone reading kilograms. Stepping up should land on a
            // plate you can actually load, not carry the stray decimal forward.
            assertEquals("62.5", entryOf(viewModel)?.weight)
        }

    @Test
    fun `stepping weight below zero returns to blank, which is bodyweight`() =
        runTest {
            // Constitution §2: an absent load is absent, never zero. Zero would claim the bar
            // weighed nothing.
            val viewModel = viewModel()
            openEntry(viewModel)
            viewModel.setEntry.change(weight = "5")

            viewModel.setEntry.stepWeight(-1)

            assertEquals("", entryOf(viewModel)?.weight)
        }

    @Test
    fun `stepping reps down stops at one`() =
        runTest {
            // US-03: reps are whole numbers ≥ 1, so the stepper cannot walk below the floor.
            val viewModel = viewModel()
            openEntry(viewModel)
            viewModel.setEntry.change(reps = "1")

            viewModel.setEntry.stepReps(-1)

            assertEquals("1", entryOf(viewModel)?.reps)
        }

    @Test
    fun `stepping reps up from blank starts at one`() =
        runTest {
            val viewModel = viewModel()
            openEntry(viewModel)

            viewModel.setEntry.stepReps(1)

            assertEquals("1", entryOf(viewModel)?.reps)
        }

    @Test
    fun `stepping sets down stops at one, so the two-tap default survives`() =
        runTest {
            val viewModel = viewModel()
            openEntry(viewModel)

            viewModel.setEntry.stepSets(-1)

            assertEquals("1", entryOf(viewModel)?.sets)
        }

    @Test
    fun `a stepped set saves the value the stepper showed`() =
        runTest {
            // The steppers are not a display: what they show is what gets written.
            val viewModel = viewModel()
            openEntry(viewModel)

            viewModel.setEntry.stepWeight(1)
            viewModel.setEntry.stepReps(1)
            viewModel.setEntry.confirm()
            viewModel.uiState.first { it.setEntry == null }

            val logged = sets.all.single()
            assertEquals(1, logged.reps)
            // 5 lb in canonical kilograms (ADR-0006).
            assertEquals(2.27, logged.weightKg)
        }
}
