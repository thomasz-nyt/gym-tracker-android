package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
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
import kotlinx.coroutines.test.runCurrent
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
 * A rest countdown progress bar needs a denominator ([SessionUiState.restTotal]) alongside the
 * numerator that already existed ([SessionUiState.restRemaining]) — `RestPanel.kt`'s own
 * deferral comment named exactly this as the blocker. Threaded through the same
 * [RestController.reading] pipeline `restRemaining` already used, so the two can never be
 * populated independently of one another (see that class's own doc for why they are read
 * together in one tick rather than as two separately combined flows).
 *
 * Its own class rather than more of `ActiveSessionViewModelTest`, for the same reason
 * `GuidedFlowTest` already is one: that class had outgrown detekt's size limit. The fakes are
 * shared, in `LoggingFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestCountdownTest {
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

    @Test
    fun `resting carries both the remaining time and the total it started with`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()

                // RestController.reading() is a per-second poll (RestPanel.kt's own countdown
                // needs the tick, not a push) — the store was written by confirm() above, but
                // the combine's cached reading() value only catches up on its next tick. One
                // tick forward is what a real countdown would do a second later regardless.
                advanceTimeBy(REST_TICK_MILLIS)
                runCurrent()

                val state = expectMostRecentItem()
                assertEquals(Duration.ofSeconds(60), state.restRemaining)
                assertEquals(Duration.ofSeconds(60), state.restTotal)
            }
        }

    @Test
    fun `restTotal is null when no rest is running, same as restRemaining`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertNull(state.restRemaining)
                assertNull(state.restTotal)
            }
        }

    @Test
    fun `changing the default rest mid-rest does not change the running rest's total`() =
        runTest {
            // US-42's own promise: "a rest already running is not retimed underneath me." A
            // progress bar reading restTotal live off the changed default would visibly break
            // that promise even though restRemaining itself stays correct.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
                advanceTimeBy(REST_TICK_MILLIS)
                runCurrent()
                expectMostRecentItem()
            }

            restStore.setDefaultRest(Duration.ofSeconds(120))

            viewModel.uiState.test {
                advanceTimeBy(REST_TICK_MILLIS)
                runCurrent()
                assertEquals(Duration.ofSeconds(60), expectMostRecentItem().restTotal, "pinned at start, not read live")
            }
        }

    @Test
    fun `skipping the rest clears the total along with the remaining time`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            restStore.setRest(now.plusSeconds(60), Duration.ofSeconds(60))

            viewModel.rest.skip()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertNull(state.restRemaining)
                assertNull(state.restTotal)
            }
        }

    private companion object {
        /**
         * Matches [RestController]'s own private tick interval — the rest countdown re-polls
         * the store once a second, so a test observing a value written just before a tick needs
         * virtual time to cross that boundary before the new value is visible.
         */
        const val REST_TICK_MILLIS = 1_000L
    }

    @Test
    fun `finishing the workout ends the rest that was running`() =
        runTest {
            // US-56 as amended (2026-09-05): the rest belongs to the session, and the session is
            // over — otherwise the countdown outlived the workout and "Rest over" arrived after it.
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val row =
                viewModel.uiState
                    .first { it.exercises.isNotEmpty() }
                    .exercises
                    .single()
            viewModel.setEntry.open(row)
            viewModel.uiState.first { it.setEntry != null }
            viewModel.setEntry.confirm()
            assertEquals(now.plusSeconds(60), restStore.restEndsAt.first(), "a rest is running")

            viewModel.finish.confirm()

            assertNull(restStore.restEndsAt.first())
            assertNull(restStore.restTotal.first())
        }
}
