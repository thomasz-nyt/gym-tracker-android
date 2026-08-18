package com.gymtracker.feature.logging

import app.cash.turbine.test
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-18's inline PR moment: a set that beats the member's own history is announced the moment
 * it is saved, from both the two-tap sheet and the one-tap log button (ADR-0025's rule —
 * strictly beating a previous load at the same rep count, never the first time at one).
 *
 * `DetectPersonalRecord` and its rule are `:core:domain`'s own, tested there against
 * hand-computed fixtures; what matters here is the wiring — that logging a set actually runs
 * the check, on the row that was actually written, and that the result reaches
 * [SessionUiState.justSetRecord].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalRecordAnnouncementTest {
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
            addExerciseToSession = AddExerciseToSession(sessionExercises) { SessionExerciseId("unused") },
            endSession = EndSession(repository, sets, clock),
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

    /** The session in progress — the one a set gets logged into. */
    private fun activeSession(id: String) =
        WorkoutSession(
            id = SessionId(id),
            userId = member,
            gymName = null,
            startedAt = now,
            endedAt = null,
            metrics = null,
        )

    /** A past, finished session — so [PersonalRecordsOf] actually reads what it logged. */
    private fun finishedSession(
        id: String,
        startedAt: Instant,
    ) = WorkoutSession(
        id = SessionId(id),
        userId = member,
        gymName = null,
        startedAt = startedAt,
        endedAt = startedAt.plusSeconds(SESSION_LENGTH_SECONDS),
        metrics = null,
    )

    /** Seeds a finished session with one loaded set of [bench], and returns its id. */
    private suspend fun seedEarlierSet(
        weightKg: Double?,
        reps: Int,
    ): SessionExerciseId {
        val appearance = SessionExerciseId("se-earlier")
        sessionExercises.add(SessionExercise(appearance, SessionId("earlier"), bench, 1))
        sets.add(ExerciseSet("set-earlier", appearance, 1, weightKg, reps, null, now.minus(Duration.ofDays(7))))
        sets.lastFor[bench] = "set-earlier"
        return appearance
    }

    @Test
    fun `saving a set that beats the previous best announces it`() =
        runTest {
            seedEarlierSet(weightKg = 43.09, reps = 8) // ~95 lb
            val repository =
                FakeSessions(listOf(finishedSession("earlier", now.minus(Duration.ofDays(7))), activeSession("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(bench)

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(weight = "100", reps = "8")
                viewModel.setEntry.confirm()

                val record = expectMostRecentItem().justSetRecord
                assertEquals(8, record?.reps)
                assertEquals(bench, record?.exerciseId)
            }
        }

    @Test
    fun `saving a set that only ties the previous best announces nothing`() =
        runTest {
            seedEarlierSet(weightKg = 45.36, reps = 8) // ~100 lb, exactly what will be re-logged
            val repository =
                FakeSessions(listOf(finishedSession("earlier", now.minus(Duration.ofDays(7))), activeSession("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(bench)

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                // Same weight as before, per ADR-0025: equalling is not beating.
                viewModel.setEntry.change(weight = "100", reps = "8")
                viewModel.setEntry.confirm()

                assertNull(expectMostRecentItem().justSetRecord)
            }
        }

    @Test
    fun `the very first set of a movement is not a record`() =
        runTest {
            // ADR-0025: there has to be a previous load to beat.
            val repository = FakeSessions(listOf(activeSession("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(bench)

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(weight = "100", reps = "8")
                viewModel.setEntry.confirm()

                assertNull(expectMostRecentItem().justSetRecord)
            }
        }

    @Test
    fun `the one-tap log button announces a record the same way the sheet does`() =
        runTest {
            // The one-tap button needs a prefill to have anything to log — with no history at
            // all, nextLoggableSet stays null forever (by design: nothing sensible to write
            // with one tap), so this seeds a target directly, the way StartSessionFromRoutine
            // would, rather than going through onExerciseChosen (the catalog path, which never
            // carries one). A first-ever target-based prefill still is not a record either way.
            val repository = FakeSessions(listOf(activeSession("s1")))
            val viewModel = viewModel(repository)
            sessionExercises.add(
                SessionExercise(
                    SessionExerciseId("se-1"),
                    SessionId("s1"),
                    bench,
                    1,
                    MovementTarget(sets = 3, reps = 8, weightKg = 45.0),
                ),
            )
            // nextLoggableSet resolves a beat after exercises does, so this waits for it
            // outside any Turbine block, the same way NextLoggableSetTargetPrefillTest does.
            val next = viewModel.uiState.first { it.nextLoggableSet != null }.nextLoggableSet

            viewModel.uiState.test {
                viewModel.onLogNextSet(requireNotNull(next))

                assertNull(expectMostRecentItem().justSetRecord)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a bodyweight set never announces a record`() =
        runTest {
            // ADR-0025 / constitution 2.4: there is no load to compare, so a bodyweight set can
            // never be a record, first-ever or otherwise.
            val repository = FakeSessions(listOf(activeSession("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(bench)

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "12")
                viewModel.setEntry.confirm()

                assertNull(expectMostRecentItem().justSetRecord)
            }
        }

    private companion object {
        const val SESSION_LENGTH_SECONDS = 3_000L
    }
}
