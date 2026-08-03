package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.guided.GuidedPlan
import com.gymtracker.core.domain.guided.GuidedPlanStore
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.EndSession
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionHistory
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.session.WorkoutDetail
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import com.gymtracker.core.domain.sessionexercise.RemoveExerciseFromSession
import com.gymtracker.core.domain.sessionexercise.RestoreExerciseToSession
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

/** US-01 as the screen sees it. Hand-written fakes, per `specs/testing-strategy.md`. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val member = UserId("alice")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun session(
        id: String,
        startedAt: Instant = now,
        endedAt: Instant? = null,
    ) = WorkoutSession(
        id = SessionId(id),
        userId = member,
        gymName = null,
        startedAt = startedAt,
        endedAt = endedAt,
        metrics = null,
    )

    private val catalog = FakeCatalog()

    // The cascade runs at call time, by which point `sets` below is initialised.
    private val sessionExercises = FakeSessionExercises(cascade = { id -> sets.cascadeDeleteExercise(id) })
    private val sets = FakeSets(sessionOf = { id -> sessionExercises.all.firstOrNull { it.id == id }?.sessionId })
    private val units = FakeUnitPreference()
    private val restStore = FakeRestTimerStore()
    private val guidedStore = FakeGuidedPlanStore()
    private var nextSessionExercise = 1
    private var nextSet = 1

    /**
     * Sessions wired to delete their children with them, as `ON DELETE CASCADE` does in Room
     * (ADR-0012). Nothing in the domain deletes them explicitly, so nothing in the fake should.
     */
    private fun sessionsOf(vararg initial: WorkoutSession) =
        FakeSessions(initial.toList()) { id ->
            // Sets first: this fake finds a set's session by looking its appearance up in
            // sessionExercises, so clearing that first would leave the sets unreachable and
            // therefore undeleted. SQLite has the real graph and does not care about order.
            sets.cascadeDelete(id)
            sessionExercises.cascadeDelete(id)
        }

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
            guidedPlanStore = guidedStore,
            clock = clock,
        )

    @Test
    fun `with no session the screen offers to start one`() =
        runTest {
            viewModel(FakeSessions()).uiState.test {
                val state = awaitItem()
                assertNull(state.activeSession)
                assertNull(state.stalePrompt)
            }
        }

    @Test
    fun `starting a workout puts the session on screen`() =
        runTest {
            val repository = FakeSessions()
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                assertNull(awaitItem().activeSession)

                viewModel.onStartWorkout()

                assertEquals(SessionId("new"), awaitItem().activeSession?.id)
            }
        }

    @Test
    fun `reopening with an active session returns to it rather than starting a second`() =
        runTest {
            val existing = session("existing", startedAt = now.minus(Duration.ofMinutes(20)))
            val repository = FakeSessions(listOf(existing))

            viewModel(repository).uiState.test {
                assertEquals(SessionId("existing"), awaitItem().activeSession?.id)
            }
            assertEquals(1, repository.all.size, "opening the screen must not create a session")
        }

    @Test
    fun `an abandoned empty session is offered for discard on open`() =
        runTest {
            val stale = session("stale", startedAt = now.minus(Duration.ofHours(5)))

            viewModel(FakeSessions(listOf(stale))).uiState.test {
                val prompt = expectMostRecentItem().stalePrompt
                assertEquals(StaleSessionPrompt.Discard(stale), prompt)
            }
        }

    @Test
    fun `a session with recent activity is not flagged as abandoned`() =
        runTest {
            val fresh = session("fresh", startedAt = now.minus(Duration.ofMinutes(30)))

            viewModel(FakeSessions(listOf(fresh))).uiState.test {
                assertNull(expectMostRecentItem().stalePrompt)
            }
        }

    @Test
    fun `a long workout with a recent set is not abandoned`() =
        runTest {
            // The bug this replaced: staleness was measured from the session's start, so
            // someone five hours into a workout who logged a set ten minutes ago was told
            // they had left it running. Last activity is the test, not session age (US-01).
            val longSession = session("s1", startedAt = now.minus(Duration.ofHours(5)))
            sets.seed(
                ExerciseSet("recent", inSession("s1"), 1, 60.0, 5, null, now.minus(Duration.ofMinutes(10))),
            )

            viewModel(FakeSessions(listOf(longSession))).uiState.test {
                assertNull(expectMostRecentItem().stalePrompt)
            }
        }

    @Test
    fun `a long workout whose last set is old is abandoned`() =
        runTest {
            val longSession = session("s1", startedAt = now.minus(Duration.ofHours(9)))
            sets.seed(
                ExerciseSet("old", inSession("s1"), 1, 60.0, 5, null, now.minus(Duration.ofHours(6))),
            )

            viewModel(FakeSessions(listOf(longSession))).uiState.test {
                val prompt = expectMostRecentItem().stalePrompt
                assertEquals(
                    StaleSessionPrompt.Finish(longSession, now.minus(Duration.ofHours(6))),
                    prompt,
                    "ended at the last set, never at now",
                )
            }
        }

    @Test
    fun `discarding an abandoned session clears both the prompt and the session`() =
        runTest {
            val stale = session("stale", startedAt = now.minus(Duration.ofHours(5)))
            val repository = FakeSessions(listOf(stale))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                assertEquals(StaleSessionPrompt.Discard(stale), expectMostRecentItem().stalePrompt)

                viewModel.onResolveStale(StaleSessionPrompt.Discard(stale))

                val state = expectMostRecentItem()
                assertNull(state.stalePrompt)
                assertNull(state.activeSession)
            }
            assertEquals(emptyList(), repository.all)
        }

    @Test
    fun `finishing an abandoned session ends it at its last set, never at now`() =
        runTest {
            val lastSetAt = now.minus(Duration.ofHours(6))
            val stale = session("stale", startedAt = now.minus(Duration.ofHours(8)))
            val repository = FakeSessions(listOf(stale))
            val viewModel = viewModel(repository)

            viewModel.onResolveStale(StaleSessionPrompt.Finish(stale, lastSetAt))

            assertEquals(lastSetAt, repository.all.single().endedAt)
        }

    @Test
    fun `an exercise picked on the browse screen is appended to the session`() =
        runTest {
            // Choosing which exercise moved to :feature:catalog at M3 (US-12), so this screen
            // no longer owns a search — it is handed an id and appends it (US-02).
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                assertEquals(listOf("Bench Press"), expectMostRecentItem().exercises.map { it.exercise?.name })
            }
        }

    @Test
    fun `a whole visit to the browse screen is appended in pick order`() =
        runTest {
            // US-02a: one visit, several exercises. They arrive as a list because the browse
            // screen stays up across picks.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.onExercisesChosen(listOf(ExerciseId("squat"), ExerciseId("bench")))

            viewModel.uiState.test {
                val rows = expectMostRecentItem().exercises
                // Newest first on screen (US-02b), so the pick order reads bottom-up.
                assertEquals(listOf("Bench Press", "Squat"), rows.map { it.exercise?.name })
            }
        }

    @Test
    fun `every exercise in one visit gets its own position`() =
        runTest {
            // The reason onExercisesChosen appends in one coroutine: AddExerciseToSession
            // takes MAX(position) + 1, so appending concurrently would hand two of them the
            // same position and the session would render in an arbitrary order.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.onExercisesChosen(
                listOf(ExerciseId("bench"), ExerciseId("squat"), ExerciseId("bench")),
            )

            val positions = sessionExercises.all.map { it.position }
            assertEquals(listOf(1, 2, 3), positions.sorted())
            assertEquals(3, positions.toSet().size, "no two exercises share a position")
        }

    @Test
    fun `an empty visit adds nothing and does not touch the session`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.onExercisesChosen(emptyList())

            assertEquals(emptyList(), sessionExercises.all)
        }

    @Test
    fun `the same exercise can be added twice`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val rows = expectMostRecentItem().exercises
                assertEquals(2, rows.size)
                // Newest first (US-02b), so the second appearance leads.
                assertEquals(listOf(2, 1), rows.map { it.sessionExercise.position })
            }
        }

    @Test
    fun `the newest exercise is at the top of the session`() =
        runTest {
            // US-02b: the one just added should be under the thumb, not at the bottom of a
            // growing list. `position` still records the order it was performed in.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.onExerciseChosen(ExerciseId("squat"))

            viewModel.uiState.test {
                val rows = expectMostRecentItem().exercises
                assertEquals(listOf("Squat", "Bench Press"), rows.map { it.exercise?.name })
                assertEquals(listOf(2, 1), rows.map { it.sessionExercise.position })
            }
        }

    @Test
    fun `removing an exercise takes it and its sets off the screen`() =
        runTest {
            // US-02c: the machine was taken.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.onExerciseChosen(ExerciseId("squat"))
            val doomed = sessionExercises.all.first { it.exerciseId == ExerciseId("bench") }

            viewModel.removal.remove(doomed.id)

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(listOf("Squat"), state.exercises.map { it.exercise?.name })
                assertEquals(true, state.canUndoRemoval)
            }
        }

    @Test
    fun `undo puts a removed exercise back where it was`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.onExerciseChosen(ExerciseId("squat"))
            val doomed = sessionExercises.all.first { it.exerciseId == ExerciseId("bench") }

            viewModel.removal.remove(doomed.id)
            viewModel.removal.undo()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(listOf("Squat", "Bench Press"), state.exercises.map { it.exercise?.name })
                assertEquals(listOf(2, 1), state.exercises.map { it.sessionExercise.position })
                assertEquals(false, state.canUndoRemoval)
            }
        }

    @Test
    fun `removing the last exercise leaves an empty session, not a finished one`() =
        runTest {
            // US-02c: ending or discarding the session is still US-01 and US-06.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val only = sessionExercises.all.single()

            viewModel.removal.remove(only.id)

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(emptyList(), state.exercises)
                assertEquals(SessionId("s1"), state.activeSession?.id)
            }
        }

    @Test
    fun `choosing an exercise with no active session does nothing`() =
        runTest {
            val viewModel = viewModel(FakeSessions())

            viewModel.onExerciseChosen(ExerciseId("bench"))

            assertEquals(emptyList(), sessionExercises.all)
        }

    @Test
    fun `set entry opens empty for an exercise never performed`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)

                val entry = expectMostRecentItem().setEntry
                assertEquals("", entry?.weight)
                assertEquals("", entry?.reps)
                assertEquals(false, entry?.prefilled)
            }
        }

    @Test
    fun `set entry prefills from the last set, in the members unit`() =
        runTest {
            // US-03: two taps only works when the numbers are already right.
            sets.seed(ExerciseSet("old", SessionExerciseId("se-old"), 1, 61.23, 5, null, now))
            sets.lastFor[ExerciseId("bench")] = "old"
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)

                val entry = expectMostRecentItem().setEntry
                assertEquals("135", entry?.weight, "61.23 kg shown as 135 lb")
                assertEquals("5", entry?.reps)
                assertEquals(true, entry?.prefilled)
            }
        }

    @Test
    fun `confirming a set persists it in canonical kilograms and closes entry`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(weight = "135")
                viewModel.setEntry.change(reps = "5")
                viewModel.setEntry.confirm()

                assertEquals(null, expectMostRecentItem().setEntry, "entry closes only after the save")
            }
            val logged = sets.all.single()
            assertEquals(61.23, logged.weightKg)
            assertEquals(5, logged.reps)
        }

    @Test
    fun `three sets of twelve writes three rows`() =
        runTest {
            // ADR-0009: an input shorthand, not a stored concept.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "12", sets = "3")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
            }

            assertEquals(3, sets.all.size)
            assertEquals(listOf(1, 2, 3), sets.all.map { it.setIndex })
            assertEquals(listOf(12, 12, 12), sets.all.map { it.reps })
        }

    @Test
    fun `set entry defaults to one set so the two-tap path is unchanged`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)

                assertEquals("1", expectMostRecentItem().setEntry?.sets)
            }
        }

    @Test
    fun `rpe is recorded when given and left absent when blank`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "5", rpe = "8.5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()

                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
            }

            assertEquals(listOf(8.5, null), sets.all.map { it.rpe }, "blank is absent, not zero")
        }

    @Test
    fun `rpe is never carried forward by the prefill`() =
        runTest {
            sets.seed(ExerciseSet("old", SessionExerciseId("se-old"), 1, 61.23, 5, 9.0, now))
            sets.lastFor[ExerciseId("bench")] = "old"
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)

                assertEquals("", expectMostRecentItem().setEntry?.rpe, "how hard last week felt is not today's data")
            }
        }

    @Test
    fun `a blank weight logs a bodyweight set rather than zero`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "12")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
            }

            assertNull(sets.all.single().weightKg)
        }

    @Test
    fun `confirming with unusable reps does nothing`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
            }

            assertEquals(emptyList(), sets.all)
        }

    @Test
    fun `logging a set starts the rest automatically`() =
        runTest {
            // US-05, first criterion. Sixty seconds is the default until changed.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
            }

            assertEquals(now.plusSeconds(60), restStore.restEndsAt.first())
        }

    @Test
    fun `a failed save leaves no rest running`() =
        runTest {
            // The rest starts after the write, so a set that never saved cannot leave a
            // timer counting down for it.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "")
                viewModel.setEntry.confirm()
                expectMostRecentItem()
            }

            assertNull(restStore.restEndsAt.first())
            assertEquals(emptyList(), sets.all)
        }

    @Test
    fun `skipping the rest clears it`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            restStore.setRestEndsAt(now.plusSeconds(60))

            viewModel.rest.skip()

            assertNull(restStore.restEndsAt.first())
        }

    // ---- US-06: finishing a workout, and history -------------------------------------------

    @Test
    fun `finishing a workout with sets ends it and returns to home`() =
        runTest {
            val repository = sessionsOf(session("s1"))
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)
                viewModel.setEntry.change(reps = "5")
                viewModel.setEntry.confirm()
                expectMostRecentItem()

                viewModel.onFinishWorkout()

                assertNull(expectMostRecentItem().activeSession, "US-06 returns me to home")
            }
            assertEquals(now, repository.all.single().endedAt)
        }

    @Test
    fun `finishing a workout with no sets discards it rather than saving it`() =
        runTest {
            val repository = sessionsOf(session("s1"))
            val viewModel = viewModel(repository)

            viewModel.onFinishWorkout()

            assertEquals(emptyList(), repository.all, "US-06: an empty session is not history")
        }

    @Test
    fun `finishing with no session running does nothing`() =
        runTest {
            val repository = sessionsOf()
            val viewModel = viewModel(repository)

            viewModel.onFinishWorkout()

            assertEquals(emptyList(), repository.all)
        }

    @Test
    fun `history lists finished workouts with their counts and volume`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0, 60.0))
            val viewModel = viewModel(repository)

            viewModel.history.open()

            viewModel.uiState.test {
                val history = expectMostRecentItem().history
                assertEquals(true, history.isOpen)
                val row = history.sessions.single()
                assertEquals(SessionId("last-week"), row.session.id)
                assertEquals(1, row.exerciseCount)
                assertEquals(2, row.setCount)
                assertEquals(1200.0, row.volumeKg)
            }
        }

    @Test
    fun `the workout in progress is not offered for deletion`() =
        runTest {
            val repository = sessionsOf(session("today"), finished("last-week", now.minus(Duration.ofDays(7))))
            val viewModel = viewModel(repository)

            viewModel.history.open()

            viewModel.uiState.test {
                assertEquals(
                    listOf(SessionId("last-week")),
                    expectMostRecentItem().history.sessions.map { it.session.id },
                )
            }
        }

    // ---- US-05a: being walked through an exercise ------------------------------------------

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
            // The point of ADR-0016 over ADR-0009: N-at-once shares one performed_at, "the
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

    // ---- US-06b: what a past workout contained ---------------------------------------------

    @Test
    fun `opening a past workout shows what it contained`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0, 60.0))
            val viewModel = viewModel(repository)
            viewModel.history.open()

            viewModel.history.openWorkout(SessionId("last-week"))

            viewModel.uiState.test {
                val detail = checkNotNull(expectMostRecentItem().history.detail)
                val performed = detail.exercises.single()
                assertEquals("Bench Press", performed.exercise?.name)
                assertEquals(2, performed.sets.size)
                assertEquals(listOf(2), performed.groups.map { it.count }, "identical sets group")
            }
        }

    @Test
    fun `nothing is read until a workout is actually opened`() =
        runTest {
            // History is a side trip, and the detail is a side trip from that. The core loop
            // should pay for neither.
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0))
            val viewModel = viewModel(repository)

            viewModel.history.open()

            viewModel.uiState.test {
                assertNull(expectMostRecentItem().history.detail)
            }
        }

    @Test
    fun `closing a workout goes back to the list, not out of history`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0))
            val viewModel = viewModel(repository)
            viewModel.history.open()
            viewModel.history.openWorkout(SessionId("last-week"))

            viewModel.history.closeWorkout()

            viewModel.uiState.test {
                val history = expectMostRecentItem().history
                assertNull(history.detail)
                assertEquals(true, history.isOpen)
            }
        }

    // ---- US-06a: deleting a past workout ---------------------------------------------------

    @Test
    fun `deleting a past workout removes it and its sets, and offers undo`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0))
            val viewModel = viewModel(repository)
            viewModel.history.open()

            viewModel.history.delete(SessionId("last-week"))

            viewModel.uiState.test {
                val history = expectMostRecentItem().history
                assertEquals(emptyList(), history.sessions)
                assertEquals(true, history.canUndo)
            }
            assertEquals(emptyList(), repository.all)
            assertEquals(emptyList(), sets.all, "the sets went with it")
        }

    @Test
    fun `undo brings the workout back with its sets`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            seedWorkout(SessionId("last-week"), weights = listOf(60.0, 62.5))
            val viewModel = viewModel(repository)
            viewModel.history.open()
            viewModel.history.delete(SessionId("last-week"))

            viewModel.history.undo()

            viewModel.uiState.test {
                val history = expectMostRecentItem().history
                assertEquals(listOf(SessionId("last-week")), history.sessions.map { it.session.id })
                assertEquals(2, history.sessions.single().setCount)
                assertEquals(false, history.canUndo, "there is nothing left to undo")
            }
        }

    @Test
    fun `undo expires after five seconds`() =
        runTest {
            // US-04's window, reused for US-06a so the two destructive actions behave alike.
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            val viewModel = viewModel(repository)
            viewModel.history.open()
            viewModel.history.delete(SessionId("last-week"))

            advanceTimeBy(Duration.ofSeconds(5).toMillis() + 1)

            viewModel.uiState.test {
                assertEquals(false, expectMostRecentItem().history.canUndo)
            }
        }

    @Test
    fun `undo after the window has passed does nothing`() =
        runTest {
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))))
            val viewModel = viewModel(repository)
            viewModel.history.open()
            viewModel.history.delete(SessionId("last-week"))
            advanceTimeBy(Duration.ofSeconds(5).toMillis() + 1)

            viewModel.history.undo()

            assertEquals(emptyList(), repository.all, "the delete stands")
        }

    @Test
    fun `deleting the workout holding my last set changes what the next set prefills with`() =
        runTest {
            // US-06a's last criterion. The prefill reads the database, so a deleted set cannot
            // come back through it — which is the whole point of deleting test data.
            val repository = sessionsOf(finished("last-week", now.minus(Duration.ofDays(7))), session("today"))
            seedWorkout(SessionId("last-week"), weights = listOf(61.23))
            sets.lastFor[ExerciseId("bench")] = "seed-0"
            val viewModel = viewModel(repository)
            viewModel.history.open()

            viewModel.history.delete(SessionId("last-week"))
            viewModel.history.close()
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.setEntry.open(row)

                assertEquals("", expectMostRecentItem().setEntry?.weight, "the deleted set is gone for good")
            }
        }

    @Test
    fun `closing history returns to the session screen`() =
        runTest {
            val viewModel = viewModel(sessionsOf(session("s1")))
            viewModel.history.open()

            viewModel.history.close()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(false, state.history.isOpen)
                assertEquals(SessionId("s1"), state.activeSession?.id)
            }
        }

    private fun finished(
        id: String,
        startedAt: Instant,
    ) = session(id, startedAt = startedAt, endedAt = startedAt.plus(Duration.ofHours(1)))

    /**
     * Registers an appearance of an exercise in a session and returns its id.
     *
     * A set reaches its session only through `session_exercises` (ADR-0004), so a set seeded
     * against an appearance nobody declared belongs to no session at all.
     */
    private suspend fun inSession(session: String): SessionExerciseId {
        val id = SessionExerciseId("se-${nextSessionExercise++}")
        sessionExercises.add(SessionExercise(id, SessionId(session), ExerciseId("bench"), 1))
        return id
    }

    /** One exercise in [session], with a set for each weight given. */
    private suspend fun seedWorkout(
        session: SessionId,
        weights: List<Double?>,
    ) {
        val appearance = SessionExercise(SessionExerciseId("seed-se"), session, ExerciseId("bench"), 1)
        sessionExercises.add(appearance)
        weights.forEachIndexed { index, weight ->
            sets.add(ExerciseSet("seed-$index", appearance.id, index + 1, weight, 10, null, now))
        }
    }

    private class FakeGuidedPlanStore : GuidedPlanStore {
        private val state = MutableStateFlow<GuidedPlan?>(null)

        override val plan: Flow<GuidedPlan?> = state

        override suspend fun setPlan(plan: GuidedPlan?) {
            state.value = plan
        }
    }

    private class FakeRestTimerStore : RestTimerStore {
        private val endsAt = MutableStateFlow<java.time.Instant?>(null)
        private val default = MutableStateFlow(Duration.ofSeconds(60))
        private val asked = MutableStateFlow(false)

        override val restEndsAt = endsAt
        override val defaultRest = default
        override val shouldAskForNotificationPermission = asked.map { !it }

        override suspend fun setRestEndsAt(instant: java.time.Instant?) {
            endsAt.value = instant
        }

        override suspend fun setDefaultRest(rest: Duration) {
            default.value = rest
        }

        override suspend fun markNotificationPermissionAsked() {
            asked.value = true
        }
    }

    private class FakeUnitPreference : UnitPreference {
        private val state = MutableStateFlow(WeightUnit.LB)

        override fun observe(): Flow<WeightUnit> = state

        override suspend fun current(): WeightUnit = state.value

        override suspend fun set(unit: WeightUnit) {
            state.value = unit
        }
    }

    /**
     * @param sessionOf stands in for the join through `session_exercises` that gives a set its
     *   session (ADR-0004). Sets know their appearance; only that table knows the session.
     */
    private class FakeSets(
        private val sessionOf: (SessionExerciseId) -> SessionId?,
    ) : SetRepository {
        private val state = MutableStateFlow(emptyList<ExerciseSet>())
        val lastFor = mutableMapOf<ExerciseId, String>()

        val all: List<ExerciseSet> get() = state.value

        fun seed(set: ExerciseSet) {
            state.value = state.value + set
        }

        /** Stands in for the `ON DELETE CASCADE` from `sessions` through `session_exercises`. */
        fun cascadeDelete(sessionId: SessionId) {
            state.value = state.value.filterNot { sessionOf(it.sessionExerciseId) == sessionId }
        }

        /** Stands in for the `ON DELETE CASCADE` from one `session_exercises` row (US-02c). */
        fun cascadeDeleteExercise(sessionExerciseId: SessionExerciseId) {
            state.value = state.value.filterNot { it.sessionExerciseId == sessionExerciseId }
        }

        override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
            state.map { rows -> rows.filter { it.sessionExerciseId == sessionExerciseId }.sortedBy { it.setIndex } }

        override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<ExerciseSet>> =
            state.map { rows -> rows.filter { sessionOf(it.sessionExerciseId) in sessionIds } }

        override suspend fun lastSetOf(
            exerciseId: ExerciseId,
            member: UserId,
        ): ExerciseSet? = lastFor[exerciseId]?.let { id -> state.value.firstOrNull { it.id == id } }

        override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? =
            state.value.filter { sessionOf(it.sessionExerciseId) == sessionId }.maxOfOrNull { it.performedAt }

        override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int =
            state.value.count { it.sessionExerciseId == sessionExerciseId } + 1

        override suspend fun add(set: ExerciseSet) {
            state.value = state.value + set
        }
    }

    private class FakeCatalog : ExerciseCatalog {
        private fun exercise(
            id: String,
            name: String,
        ) = Exercise(
            id = ExerciseId(id),
            name = name,
            aliases = emptyList(),
            primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(),
            equipment = Equipment.BARBELL,
            instructions = emptyList(),
            mediaUrl = null,
            mediaType = null,
            youtubeUrl = null,
            source = "test",
        )

        private val all = listOf(exercise("bench", "Bench Press"), exercise("squat", "Squat"))

        // Ranking is all this has to supply now; narrowing is CatalogQuery's, and the
        // interface's search() runs it for us. The fake no longer reimplements matching,
        // so it cannot drift from the real thing.
        override fun observeRanked(forMember: UserId): Flow<List<Exercise>> = MutableStateFlow(all)
    }

    private class FakeSessionExercises(
        private val cascade: (SessionExerciseId) -> Unit = {},
    ) : SessionExerciseRepository {
        private val state = MutableStateFlow(emptyList<SessionExercise>())

        val all: List<SessionExercise> get() = state.value

        /** Stands in for the `ON DELETE CASCADE` on `session_exercises.session_id`. */
        fun cascadeDelete(sessionId: SessionId) {
            state.value = state.value.filterNot { it.sessionId == sessionId }
        }

        override fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>> =
            state.map { rows -> rows.filter { it.sessionId == sessionId }.sortedBy { it.position } }

        override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<SessionExercise>> =
            state.map { rows -> rows.filter { it.sessionId in sessionIds }.sortedBy { it.position } }

        override suspend fun find(id: SessionExerciseId): SessionExercise? = state.value.firstOrNull { it.id == id }

        override suspend fun add(sessionExercise: SessionExercise) {
            state.value = state.value + sessionExercise
        }

        override suspend fun remove(id: SessionExerciseId) {
            state.value = state.value.filterNot { it.id == id }
            cascade(id)
        }

        // MAX(position) + 1, as the DAO does it. A count would reuse a position after a
        // removal from the middle of a session (US-02c).
        override suspend fun nextPosition(sessionId: SessionId): Int =
            (state.value.filter { it.sessionId == sessionId }.maxOfOrNull { it.position } ?: 0) + 1
    }

    private class FakeCurrentMember(
        private val id: UserId,
    ) : CurrentMember {
        override suspend fun id(): UserId = id
    }

    private class FakeSessions(
        initial: List<WorkoutSession> = emptyList(),
        private val cascade: (SessionId) -> Unit = {},
    ) : SessionRepository {
        private val state = MutableStateFlow(initial)

        val all: List<WorkoutSession> get() = state.value

        override fun observeActiveSession(userId: UserId): Flow<WorkoutSession?> =
            state.map { sessions -> sessions.lastOrNull { it.userId == userId && it.endedAt == null } }

        override fun observeFinishedSessions(userId: UserId): Flow<List<WorkoutSession>> =
            state.map { sessions ->
                sessions
                    .filter { it.userId == userId && it.endedAt != null }
                    .sortedByDescending { it.startedAt }
            }

        override suspend fun findActiveSession(userId: UserId): WorkoutSession? =
            state.value.lastOrNull { it.userId == userId && it.endedAt == null }

        override suspend fun findSession(id: SessionId): WorkoutSession? = state.value.firstOrNull { it.id == id }

        override suspend fun startSession(session: WorkoutSession) {
            state.value = state.value + session
        }

        override suspend fun restoreSession(session: WorkoutSession) {
            state.value = state.value + session
        }

        override suspend fun endSession(
            id: SessionId,
            endedAt: Instant,
        ) {
            state.value = state.value.map { if (it.id == id) it.copy(endedAt = endedAt) else it }
        }

        override suspend fun deleteSession(id: SessionId) {
            state.value = state.value.filterNot { it.id == id }
            cascade(id)
        }
    }
}
