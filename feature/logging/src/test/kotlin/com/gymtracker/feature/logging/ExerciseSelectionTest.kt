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
 * US-45 (ADR-0037): the machine was taken, so a set landed on a later exercise first — confirm
 * the earlier one is still reachable rather than lost for the rest of the session.
 *
 * A separate file from `ActiveSessionViewModelTest`, which detekt's `LargeClass` rule was
 * already close to before this story — the same reason [ExerciseSelectionController] is its own
 * production class rather than a few more lines on [ActiveSessionViewModel]. The setup below
 * duplicates that file's fakes and its `viewModel(...)` builder rather than sharing them, the
 * same deliberate call `FinishSummaryScreen.kt`'s own doc comment already makes for a short
 * private test-builder over reaching across files for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseSelectionTest {
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
            startSession = StartSession(repository, restStore, clock) { SessionId("new") },
            startSessionFromRoutine = fakeStartSessionFromRoutine(),
            addExerciseToSession =
                AddExerciseToSession(sessionExercises) { SessionExerciseId("se-${nextSessionExercise++}") },
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
            guidedPlanStore = guidedStore,
            clock = clock,
        )

    /** Three appearances (positions 1-3, ADR-0004 allows a repeat) with a fresh session. */
    private fun threeExerciseSession(): ActiveSessionViewModel {
        val repository = FakeSessions(listOf(session("s1")))
        val viewModel = viewModel(repository)
        viewModel.onExercisesChosen(listOf(ExerciseId("bench"), ExerciseId("squat"), ExerciseId("bench")))
        return viewModel
    }

    private fun idAt(position: Int) = sessionExercises.all.single { it.position == position }.id

    @Test
    fun `selecting an earlier exercise makes it the open row, overriding the derived default`() =
        runTest {
            val viewModel = threeExerciseSession()
            val firstId = idAt(1)

            viewModel.uiState.test {
                val lastRow = expectMostRecentItem().exercises.first { it.sessionExercise.position == 3 }
                viewModel.setEntry.open(lastRow)
                viewModel.setEntry.change(weight = "135", reps = "5")
                viewModel.setEntry.confirm()

                // Sanity check the derivation this is overriding: the highest-position
                // exercise with a logged set is open by default, exactly ADR-0029's rule.
                assertEquals(lastRow.sessionExercise.id, expectMostRecentItem().openSessionExerciseId)

                viewModel.selection.select(firstId)

                assertEquals(firstId, expectMostRecentItem().openSessionExerciseId)
            }
        }

    @Test
    fun `the one-tap log button follows the selection, not just openSessionExerciseId`() =
        runTest {
            // Seeded history so nextLoggableSet is non-null at all (ADR-0031: absent, not
            // invented, with neither history nor a target) -- otherwise there would be nothing
            // for the one-tap button to offer regardless of which row is open.
            sets.seed(ExerciseSet("old", SessionExerciseId("se-old"), 1, 61.23, 5, null, now))
            sets.lastFor[ExerciseId("bench")] = "old"
            val viewModel = threeExerciseSession()
            val firstId = idAt(1)

            viewModel.uiState.test {
                val lastRow = expectMostRecentItem().exercises.first { it.sessionExercise.position == 3 }
                viewModel.setEntry.open(lastRow)
                viewModel.setEntry.change(weight = "135", reps = "5")
                viewModel.setEntry.confirm()

                viewModel.selection.select(firstId)
                val nextLoggableSet = expectMostRecentItem().nextLoggableSet
                assertEquals(firstId, nextLoggableSet?.sessionExerciseId, "the button should target the open row")
                viewModel.onLogNextSet(nextLoggableSet!!)
                expectMostRecentItem()
            }

            assertEquals(firstId, sets.all.last().sessionExerciseId, "one-tap logged against the selected exercise")
        }

    @Test
    fun `the selection is sticky -- logging elsewhere does not snap the open row back`() =
        runTest {
            val viewModel = threeExerciseSession()
            val firstId = idAt(1)

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                val lastRow = state.exercises.first { it.sessionExercise.position == 3 }
                val middleRow = state.exercises.first { it.sessionExercise.position == 2 }
                viewModel.setEntry.open(lastRow)
                viewModel.setEntry.change(weight = "135", reps = "5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
                viewModel.selection.select(firstId)
                expectMostRecentItem()

                // A set logged on a third exercise -- not the one selected -- must not move
                // the open row out from under a member deliberately working on `firstId`.
                viewModel.setEntry.open(middleRow)
                viewModel.setEntry.change(weight = "95", reps = "8")
                viewModel.setEntry.confirm()

                assertEquals(firstId, expectMostRecentItem().openSessionExerciseId)
            }
        }

    @Test
    fun `removing the selected exercise falls back to the derived default without crashing`() =
        runTest {
            val viewModel = threeExerciseSession()
            val firstId = idAt(1)
            val lastId = idAt(3)

            viewModel.uiState.test {
                val lastRow = expectMostRecentItem().exercises.first { it.sessionExercise.position == 3 }
                viewModel.setEntry.open(lastRow)
                viewModel.setEntry.change(weight = "135", reps = "5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
                viewModel.selection.select(firstId)
                expectMostRecentItem()

                viewModel.removal.remove(firstId)

                assertEquals(lastId, expectMostRecentItem().openSessionExerciseId)
            }
        }
}
