package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertTrue

/**
 * US-04, "Correct a mistake": editing a logged set, and deleting one with five seconds to
 * change your mind.
 *
 * The set being corrected keeps its identity throughout — same id, same `setIndex`, same
 * `performedAt`. Only the three things the story names can change. That matters because
 * `performedAt` is when the set was *performed*, and correcting a typo an hour later must not
 * rewrite history to say you lifted it just now (constitution §2.4).
 *
 * Deleting reuses the shape ADR-0012 settled and US-02c copied: delete now, keep the row in
 * memory, put it back on undo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetEditTest {
    private val now: Instant = Instant.parse("2026-08-07T18:00:00Z")
    private val performed: Instant = Instant.parse("2026-08-07T17:30:00Z")
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

    @Test
    fun `the editor opens on the set you tapped, in your own unit`() =
        runTest {
            val viewModel = viewModelWithLoggedSet()

            viewModel.setEdit.open(loggedSet(), EXERCISE_NAME)

            val edit = editOf(viewModel)
            assertEquals(EXERCISE_NAME, edit?.exerciseName)
            // 61.23 kg is 135 lb, and this member reads pounds (ADR-0008).
            assertEquals("135", edit?.weight)
            assertEquals("8", edit?.reps)
            assertEquals(SET_ID, edit?.set?.id, "the editor must know which row it is correcting")
        }

    @Test
    fun `saving a correction keeps the set's identity and changes only what US-04 allows`() =
        runTest {
            val viewModel = viewModelWithLoggedSet()
            viewModel.setEdit.open(loggedSet(), EXERCISE_NAME)

            viewModel.setEdit.change(reps = "9")
            viewModel.setEdit.save()

            val stored = sets.all.single()
            assertEquals(9, stored.reps, "the correction landed")
            assertEquals(SET_ID, stored.id, "the row was corrected, not replaced")
            assertEquals(1, stored.setIndex, "its place in the exercise is unchanged")
            assertEquals(
                performed,
                stored.performedAt,
                "correcting a typo must not claim the set was performed just now",
            )
            assertNull(editOf(viewModel), "the editor closes once the write returns")
        }

    @Test
    fun `a weight stepped below the bottom becomes blank, never zero`() =
        runTest {
            // Constitution §2: a bodyweight set is an absence, not a load of nothing. Same rule
            // set entry already follows (ADR-0016).
            val viewModel = viewModelWithLoggedSet()
            viewModel.setEdit.open(loggedSet(), EXERCISE_NAME)

            repeat(TOO_MANY_STEPS) { viewModel.setEdit.stepWeight(-1) }
            viewModel.setEdit.save()

            assertNull(sets.all.single().weightKg, "no weight recorded is not a weight of zero")
        }

    @Test
    fun `reps never step below one`() =
        runTest {
            val viewModel = viewModelWithLoggedSet()
            viewModel.setEdit.open(loggedSet(), EXERCISE_NAME)

            repeat(TOO_MANY_STEPS) { viewModel.setEdit.stepReps(-1) }

            assertEquals("1", editOf(viewModel)?.reps)
        }

    @Test
    fun `deleting from the editor removes the set and offers it back`() =
        runTest {
            val viewModel = viewModelWithLoggedSet()
            viewModel.setEdit.open(loggedSet(), EXERCISE_NAME)

            viewModel.setEdit.delete()

            assertEquals(emptyList(), sets.all, "the row is gone before the list re-renders")
            assertNull(editOf(viewModel), "deleting closes the editor it was invoked from")
            viewModel.uiState.test {
                assertTrue(expectMostRecentItem().canUndoSetDelete, "five seconds to change your mind")
            }
        }

    @Test
    fun `undo puts the set back unchanged`() =
        runTest {
            val viewModel = viewModelWithLoggedSet()
            viewModel.setEdit.open(loggedSet(), EXERCISE_NAME)
            viewModel.setEdit.delete()

            viewModel.setEdit.undo()

            val restored = sets.all.single()
            assertEquals(SET_ID, restored.id, "same row, not a new one")
            assertEquals(1, restored.setIndex)
            assertEquals(8, restored.reps)
            assertEquals(WEIGHT_KG, restored.weightKg)
            assertEquals(performed, restored.performedAt)
        }

    @Test
    fun `undo expires after five seconds`() =
        runTest {
            // US-04's window, the same one US-02c and US-06a use.
            val viewModel = viewModelWithLoggedSet()
            viewModel.setEdit.open(loggedSet(), EXERCISE_NAME)
            viewModel.setEdit.delete()

            advanceTimeBy(Duration.ofSeconds(5).toMillis() + 1)

            viewModel.uiState.test {
                assertEquals(false, expectMostRecentItem().canUndoSetDelete)
            }
        }

    @Test
    fun `undo after the window has passed does nothing`() =
        runTest {
            val viewModel = viewModelWithLoggedSet()
            viewModel.setEdit.open(loggedSet(), EXERCISE_NAME)
            viewModel.setEdit.delete()
            advanceTimeBy(Duration.ofSeconds(5).toMillis() + 1)

            viewModel.setEdit.undo()

            assertEquals(emptyList(), sets.all, "the delete stands")
        }

    @Test
    fun `the rest panel offers the next set, and logs it in one tap`() =
        runTest {
            // ADR-0023. The panel is the whole point: same weight, same reps, next set, without
            // opening anything.
            val viewModel = viewModelWithLoggedSet()

            val next = viewModel.uiState.first { it.upNext != null }.upNext!!
            assertEquals(2, next.setNumber, "one set logged, so the next is 2 — never \"2 of N\"")

            viewModel.onLogNextSet(next)

            val logged = sets.all.sortedBy { it.setIndex }
            assertEquals(2, logged.size, "the set was written from the panel")
            assertEquals(8, logged.last().reps, "carried from the prefill")
            assertEquals(WEIGHT_KG, logged.last().weightKg)
        }

    @Test
    fun `there is nothing up next before anything has been logged`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.uiState.first { it.exercises.isNotEmpty() }

            assertNull(viewModel.uiState.first().upNext, "no set, no rest, nothing to be next")
        }

    private fun loggedSet() =
        ExerciseSet(
            id = SET_ID,
            sessionExerciseId = SessionExerciseId("se-1"),
            setIndex = 1,
            weightKg = WEIGHT_KG,
            reps = 8,
            rpe = null,
            performedAt = performed,
        )

    /** A session with one exercise and one set already logged against it. */
    private suspend fun viewModelWithLoggedSet(): ActiveSessionViewModel {
        val viewModel = viewModel()
        viewModel.onExerciseChosen(ExerciseId("bench"))
        viewModel.uiState.first { it.exercises.isNotEmpty() }
        sets.seed(loggedSet())
        return viewModel
    }

    private suspend fun editOf(viewModel: ActiveSessionViewModel): SetEdit? = viewModel.uiState.first().setEdit

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
                startSession = StartSession(repository, restStore, clock) { SessionId("new") },
                startSessionFromRoutine = fakeStartSessionFromRoutine(),
                addExerciseToSession =
                    AddExerciseToSession(sessionExercises) { SessionExerciseId("se-${nextSessionExercise++}") },
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

    private companion object {
        const val SET_ID = "set-logged"
        const val EXERCISE_NAME = "Bench Press"

        /** 61.23 kg is exactly 135 lb, the unit this household reads. */
        const val WEIGHT_KG = 61.23

        /** More presses than the value can absorb, so the floor is what is being asserted. */
        const val TOO_MANY_STEPS = 40
    }
}
