package com.gymtracker.feature.logging

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
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
import com.gymtracker.core.domain.session.StaleSessionPrompt
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
import kotlinx.coroutines.flow.map
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
    // Explicit types on both: they reference each other — the cascade needs `sets`, and
    // `sets` finds a set's session through `sessionExercises` — and Kotlin cannot infer
    // either end of a cycle.
    private val sessionExercises: FakeSessionExercises =
        FakeSessionExercises(cascade = { id -> sets.cascadeDeleteExercise(id) })
    private val sets: FakeSets =
        FakeSets(sessionOf = { id -> sessionExercises.all.firstOrNull { it.id == id }?.sessionId })
    private val units = FakeUnitPreference()
    private val restStore = FakeRestTimerStore()
    private val guidedStore = FakeGuidedPlanStore()
    private var nextSessionExercise = 1
    private var nextSet = 1

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

    // ---- US-31: finish as a summary ----

    @Test
    fun `finishing a session with sets reaches the summary, with any record it set`() =
        runTest {
            // History: bench 100x5 in a past, already-finished session.
            val historyAppearance = inSession("hist")
            sets.seed(ExerciseSet("hist-set", historyAppearance, 1, 100.0, 5, null, now.minusSeconds(86400)))

            val repository =
                FakeSessions(
                    listOf(
                        session("hist", startedAt = now.minusSeconds(90000), endedAt = now.minusSeconds(86400)),
                        session("s1"),
                    ),
                )
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val today = sessionExercises.all.single { it.sessionId == SessionId("s1") }
            sets.seed(ExerciseSet("today-set", today.id, 1, 102.5, 5, null, now))

            viewModel.finish.confirm()

            viewModel.uiState.test {
                val finish = expectMostRecentItem().finish
                check(finish is FinishFlow.Ready) { "expected Ready, got $finish" }
                assertEquals(1, finish.detail.summary.exerciseCount)
                assertEquals(1, finish.detail.summary.setCount)
                assertEquals(512.5, finish.detail.summary.volumeKg)
                assertEquals(102.5, finish.records.single().weightKg)
                assertEquals(5, finish.records.single().reps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `finishing an empty session shows no summary, matching today's discard`() =
        runTest {
            // US-06: a session with no sets is discarded, not saved. Nothing to summarize.
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)

            viewModel.finish.confirm()

            viewModel.uiState.test {
                assertNull(expectMostRecentItem().finish)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(emptyList(), repository.all)
        }

    @Test
    fun `dismissing the summary clears it`() =
        runTest {
            val historyAppearance = inSession("hist")
            sets.seed(ExerciseSet("hist-set", historyAppearance, 1, 60.0, 8, null, now.minusSeconds(86400)))
            val repository =
                FakeSessions(
                    listOf(
                        session("hist", startedAt = now.minusSeconds(90000), endedAt = now.minusSeconds(86400)),
                        session("s1"),
                    ),
                )
            val viewModel = viewModel(repository)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val today = sessionExercises.all.single { it.sessionId == SessionId("s1") }
            sets.seed(ExerciseSet("today-set", today.id, 1, 60.0, 8, null, now))
            viewModel.finish.confirm()

            viewModel.finish.dismiss()

            viewModel.uiState.test {
                assertNull(expectMostRecentItem().finish)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
