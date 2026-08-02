package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.exercise.ExerciseCatalog
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
    fun `opening search shows the catalog`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.onAddExerciseClicked()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(true, state.isSearching)
                assertEquals(listOf("Bench Press", "Squat"), state.results.map { it.name })
            }
        }

    @Test
    fun `choosing an exercise appends it and leaves the search open`() =
        runTest {
            // US-02a. It used to close on every pick, so a three-exercise workout was three
            // trips through the search field.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onAddExerciseClicked()

            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(true, state.isSearching)
                assertEquals(listOf("Bench Press"), state.exercises.map { it.exercise?.name })
                assertEquals(listOf(ExerciseId("bench")), state.addedThisVisit)
            }
        }

    @Test
    fun `several exercises can be added in one visit to the search`() =
        runTest {
            // US-02a, and the reason the FAB can report a count.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onAddExerciseClicked()

            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.onExerciseChosen(ExerciseId("squat"))

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(true, state.isSearching)
                assertEquals(2, state.addedThisVisit.size)
                assertEquals(2, state.exercises.size)
            }
        }

    @Test
    fun `the added count is per visit, not for all time`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onAddExerciseClicked()
            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.onSearchDismissed()

            viewModel.onAddExerciseClicked()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(emptyList(), state.addedThisVisit)
                assertEquals(1, state.exercises.size, "the exercise itself is still in the session")
            }
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

        override fun search(
            query: String,
            forMember: UserId,
        ): Flow<List<Exercise>> = MutableStateFlow(all.filter { it.name.contains(query, ignoreCase = true) })
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
