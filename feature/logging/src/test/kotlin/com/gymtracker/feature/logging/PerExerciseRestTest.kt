package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseId
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

/**
 * US-05 and US-30 as amended by ADR-0050: the rest that follows a set is the movement's own when
 * its target names one, else the member's default — on every path that starts a rest from this
 * screen. The one-tap path is `LogUpNextSet`'s and is pinned in `:core:domain`; these are the
 * sheet's and guided mode's, which start their rest through `RestController`.
 *
 * Same fixture as `SetEntryTargetPrefillTest`: the movement is seeded directly with its target,
 * the shape `StartSessionFromRoutine` leaves it in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerExerciseRestTest {
    private val now: Instant = Instant.parse("2026-09-05T18:00:00Z")
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

    private suspend fun seed(
        viewModel: ActiveSessionViewModel,
        target: MovementTarget,
    ): SessionExerciseRow {
        sessionExercises.add(SessionExercise(SessionExerciseId("se-1"), SessionId("s1"), bench, 1, target))
        return viewModel.uiState
            .first { it.exercises.isNotEmpty() }
            .exercises
            .single()
    }

    @Test
    fun `saving a set on a movement whose target names a rest starts that rest, not the default`() =
        runTest {
            val viewModel = viewModel()
            val row = seed(viewModel, MovementTarget(sets = 3, reps = 8, weightKg = 45.0, restSeconds = 90))
            viewModel.setEntry.open(row)
            viewModel.uiState.first { it.setEntry != null }

            viewModel.setEntry.confirm()

            assertEquals(now.plusSeconds(90), restStore.restEndsAt.first())
            assertEquals(Duration.ofSeconds(90), restStore.restTotal.first(), "the band reads 'of 1:30'")
        }

    @Test
    fun `a movement whose target names no rest takes the default, as before`() =
        runTest {
            val viewModel = viewModel()
            val row = seed(viewModel, MovementTarget(sets = 3, reps = 8, weightKg = 45.0))
            viewModel.setEntry.open(row)
            viewModel.uiState.first { it.setEntry != null }

            viewModel.setEntry.confirm()

            assertEquals(now.plusSeconds(60), restStore.restEndsAt.first())
        }

    @Test
    fun `a set finished in guided mode earns the movement's own rest too`() =
        runTest {
            // Guided mode's own targets (sets, reps, weight typed in its start dialog) are a
            // separate concept — but the rest it starts is the session's, through the same
            // RestController, so the movement's rest reaches it without guided mode knowing.
            val viewModel = viewModel()
            val row = seed(viewModel, MovementTarget(sets = 3, reps = 8, weightKg = 45.0, restSeconds = 120))
            viewModel.onStartExercise(row)
            viewModel.guided.changeSetup(weight = "100", reps = "8", sets = "3")
            viewModel.guided.begin()
            viewModel.uiState.first { it.guided.running != null }

            viewModel.guided.finishSet()

            assertEquals(now.plusSeconds(120), restStore.restEndsAt.first())
            assertEquals(Duration.ofMinutes(2), restStore.restTotal.first())
        }
}
