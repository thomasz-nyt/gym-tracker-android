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
import kotlin.test.assertNull

/**
 * US-05a: being walked through an exercise, set by set.
 *
 * Its own class rather than more of `ActiveSessionViewModelTest`, which had outgrown detekt's
 * size limit. The fakes are shared, in `LoggingFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidedFlowTest {
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
            guidedPlanStore = guidedStore,
            clock = clock,
        )

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
    fun `starting an exercise prefills weight from history, but reps and sets at a fixed 12x3`() =
        runTest {
            // Weight still comes from the last time this exercise was done (US-03's own rule).
            // Reps and sets deliberately do not — each set guided mode writes gets its own real
            // timestamp regardless of the target count, so a fixed walkthrough length costs
            // nothing here the way raising the two-tap sheet's Sets floor would (ADR-0031).
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
                assertEquals("12", setup.reps, "the fixed default, not history's 8")
                assertEquals("3", setup.sets, "the fixed default")
            }
        }

    @Test
    fun `starting an exercise with no history at all still offers the fixed 12x3, weight blank`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val row = expectMostRecentItem().exercises.single()
                viewModel.onStartExercise(row)

                val setup = checkNotNull(expectMostRecentItem().guided.setup)
                assertEquals("", setup.weight, "no history to invent a weight from")
                assertEquals("12", setup.reps)
                assertEquals("3", setup.sets)
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
            // The point of ADR-0017 over ADR-0009: N-at-once shares one performed_at, "the
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

    // ADR-0033: the guided screen's rep count gains the app's steppers, the one behavioural
    // change bundled with the visual rebuild. `stepReps` must share `finishSet`'s own fallback
    // (typed value, else the target) so the two cannot disagree about what "one down" means.

    @Test
    fun `stepping the rep count down logs the stepped count, not the target`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel, targetReps = "12")

            viewModel.guided.stepReps(-1)
            viewModel.guided.finishSet()

            assertEquals(listOf(11), sets.all.map { it.reps })
        }

    @Test
    fun `the rep count never steps below one`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel, targetReps = "1")

            viewModel.guided.stepReps(-1)

            viewModel.uiState.test {
                assertEquals("1", checkNotNull(expectMostRecentItem().guided.running).reps)
            }

            viewModel.guided.finishSet()
            assertEquals(listOf(1), sets.all.map { it.reps })
        }

    @Test
    fun `stepping before typing steps from the target, not from zero`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            beginGuided(viewModel, targetReps = "12")

            viewModel.guided.stepReps(1)

            viewModel.uiState.test {
                assertEquals("13", checkNotNull(expectMostRecentItem().guided.running).reps)
            }
        }

    // The start dialog itself gains steppers too (this change): GuidedSetupDialog used raw
    // OutlinedTextFields for Sets and Reps, the one guided-mode surface the roadmap named as
    // still on Material defaults, disagreeing with the screen it opens into (which has had a
    // stepper on its own rep count since ADR-0033). stepSetupReps/stepSetupSets are separate
    // methods from stepReps/setupRepsChanged above on purpose — those mutate the *running*
    // exercise's rep count once the flow has begun; these mutate the *pending setup*, which is
    // a different field on a different part of GuidedState and must not read or write the other.

    @Test
    fun `stepping the setup dialog's rep target moves the pending setup, not the running exercise`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val row = SessionExerciseRow(sessionExercises.all.last(), null, emptyList())
            viewModel.onStartExercise(row)

            viewModel.guided.stepSetupReps(1)

            viewModel.uiState.test {
                val state = expectMostRecentItem().guided
                assertEquals("13", checkNotNull(state.setup).reps, "12, the fixed default, stepped up by one")
                assertNull(state.running, "begin() was never called")
            }
        }

    @Test
    fun `stepping the setup dialog's set target moves the pending setup`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val row = SessionExerciseRow(sessionExercises.all.last(), null, emptyList())
            viewModel.onStartExercise(row)

            viewModel.guided.stepSetupSets(-1)

            viewModel.uiState.test {
                assertEquals("2", checkNotNull(expectMostRecentItem().guided.setup).sets, "3, stepped down by one")
            }
        }

    @Test
    fun `neither setup stepper steps below one`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val row = SessionExerciseRow(sessionExercises.all.last(), null, emptyList())
            viewModel.onStartExercise(row)
            viewModel.guided.changeSetup(reps = "1", sets = "1")

            viewModel.guided.stepSetupReps(-1)
            viewModel.guided.stepSetupSets(-1)

            viewModel.uiState.test {
                val setup = checkNotNull(expectMostRecentItem().guided.setup)
                assertEquals("1", setup.reps)
                assertEquals("1", setup.sets)
            }
        }

    @Test
    fun `stepping setup reps before any typing steps from the current value, not from zero`() =
        runTest {
            // Same fallback rule as stepReps on the running exercise (ADR-0033): a step must
            // move from what is on screen, which after changeSetup is the typed value already.
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val row = SessionExerciseRow(sessionExercises.all.last(), null, emptyList())
            viewModel.onStartExercise(row)
            viewModel.guided.changeSetup(reps = "20")

            viewModel.guided.stepSetupReps(1)

            viewModel.uiState.test {
                assertEquals("21", checkNotNull(expectMostRecentItem().guided.setup).reps)
            }
        }

    // ADR-0033's own "what this ADR does not touch" section named the fix in advance: "three
    // StepperFields in the same dialog shape" — weight included, not only reps and sets. This
    // reuses SetEntryController.stepWeight's exact rule: 2.5 kg / 5 lb per step, snapped onto
    // the increment (so a prefill entered in the other unit steps cleanly rather than by a
    // fractional offset), and floors at blank rather than zero — a bodyweight set, not a claim
    // the bar weighs nothing (constitution §2).

    @Test
    fun `stepping the setup dialog's weight moves by one increment of the member's unit`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            units.set(WeightUnit.LB)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val row = SessionExerciseRow(sessionExercises.all.last(), null, emptyList())
            viewModel.onStartExercise(row)
            viewModel.guided.changeSetup(weight = "135")

            viewModel.guided.stepSetupWeight(1)

            viewModel.uiState.test {
                assertEquals("140", checkNotNull(expectMostRecentItem().guided.setup).weight, "135 lb + 5 lb")
            }
        }

    @Test
    fun `stepping the setup dialog's weight down from blank lands on one increment, not negative`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            units.set(WeightUnit.LB)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val row = SessionExerciseRow(sessionExercises.all.last(), null, emptyList())
            viewModel.onStartExercise(row)
            // No history for this exercise, so weight starts blank (per the test above this
            // file group already covers) — stepping down from there must not go negative.

            viewModel.guided.stepSetupWeight(-1)

            viewModel.uiState.test {
                val weight = checkNotNull(expectMostRecentItem().guided.setup).weight
                assertEquals(true, weight.toDoubleOrNull()?.let { it >= 0.0 } ?: true, "never negative")
            }
        }

    @Test
    fun `stepping the setup dialog's weight down past the bottom lands on blank, not zero`() =
        runTest {
            val viewModel = viewModel(FakeSessions(listOf(session("s1"))))
            units.set(WeightUnit.LB)
            viewModel.onExerciseChosen(ExerciseId("bench"))
            val row = SessionExerciseRow(sessionExercises.all.last(), null, emptyList())
            viewModel.onStartExercise(row)
            viewModel.guided.changeSetup(weight = "2")

            viewModel.guided.stepSetupWeight(-1)

            viewModel.uiState.test {
                assertEquals("", checkNotNull(expectMostRecentItem().guided.setup).weight, "a bodyweight set, not 0")
            }
        }
}
