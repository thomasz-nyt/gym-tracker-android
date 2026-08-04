package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.EndSession
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionHistory
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val sessionExercises = FakeSessionExercises()
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
            deleteSession = DeleteSession(repository, sessionExercises, sets),
            restoreSession = RestoreSession(repository, sessionExercises, sets),
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
    fun `the same exercise can be added twice`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.onExerciseChosen(ExerciseId("bench"))
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val rows = expectMostRecentItem().exercises
                assertEquals(2, rows.size)
                assertEquals(listOf(1, 2), rows.map { it.sessionExercise.position })
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
            // US-05, first criterion. Ninety seconds is the default until changed.
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

            assertEquals(now.plusSeconds(90), restStore.restEndsAt.first())
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
            restStore.setRestEndsAt(now.plusSeconds(90))

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
}
