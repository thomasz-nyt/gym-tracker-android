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
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
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
    private val sessionExercises = FakeSessionExercises()
    private val sets = FakeSets()
    private val units = FakeUnitPreference()
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
            prefillFromLastSet = PrefillFromLastSet(sets),
            unitPreference = units,
            startSession = StartSession(repository, clock) { SessionId("new") },
            addExerciseToSession =
                AddExerciseToSession(sessionExercises) { SessionExerciseId("se-${nextSessionExercise++}") },
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
    fun `choosing an exercise appends it to the session and closes search`() =
        runTest {
            val repository = FakeSessions(listOf(session("s1")))
            val viewModel = viewModel(repository)
            viewModel.onAddExerciseClicked()

            viewModel.onExerciseChosen(ExerciseId("bench"))

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(false, state.isSearching)
                assertEquals(listOf("Bench Press"), state.exercises.map { it.exercise?.name })
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

    private class FakeUnitPreference : UnitPreference {
        private val state = MutableStateFlow(WeightUnit.LB)

        override fun observe(): Flow<WeightUnit> = state

        override suspend fun current(): WeightUnit = state.value

        override suspend fun set(unit: WeightUnit) {
            state.value = unit
        }
    }

    private class FakeSets : SetRepository {
        private val state = MutableStateFlow(emptyList<ExerciseSet>())
        val lastFor = mutableMapOf<ExerciseId, String>()

        val all: List<ExerciseSet> get() = state.value

        fun seed(set: ExerciseSet) {
            state.value = state.value + set
        }

        override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
            state.map { rows -> rows.filter { it.sessionExerciseId == sessionExerciseId }.sortedBy { it.setIndex } }

        override suspend fun lastSetOf(
            exerciseId: ExerciseId,
            member: UserId,
        ): ExerciseSet? = lastFor[exerciseId]?.let { id -> state.value.firstOrNull { it.id == id } }

        override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? =
            state.value.maxOfOrNull { it.performedAt }

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

    private class FakeSessionExercises : SessionExerciseRepository {
        private val state = MutableStateFlow(emptyList<SessionExercise>())

        val all: List<SessionExercise> get() = state.value

        override fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>> =
            state.map { rows -> rows.filter { it.sessionId == sessionId }.sortedBy { it.position } }

        override suspend fun add(sessionExercise: SessionExercise) {
            state.value = state.value + sessionExercise
        }

        override suspend fun nextPosition(sessionId: SessionId): Int =
            state.value.count { it.sessionId == sessionId } + 1
    }

    private class FakeCurrentMember(
        private val id: UserId,
    ) : CurrentMember {
        override suspend fun id(): UserId = id
    }

    private class FakeSessions(
        initial: List<WorkoutSession> = emptyList(),
    ) : SessionRepository {
        private val state = MutableStateFlow(initial)

        val all: List<WorkoutSession> get() = state.value

        override fun observeActiveSession(userId: UserId): Flow<WorkoutSession?> =
            state.map { sessions -> sessions.lastOrNull { it.userId == userId && it.endedAt == null } }

        override suspend fun findActiveSession(userId: UserId): WorkoutSession? =
            state.value.lastOrNull { it.userId == userId && it.endedAt == null }

        override suspend fun startSession(session: WorkoutSession) {
            state.value = state.value + session
        }

        override suspend fun endSession(
            id: SessionId,
            endedAt: Instant,
        ) {
            state.value = state.value.map { if (it.id == id) it.copy(endedAt = endedAt) else it }
        }

        override suspend fun discardSession(id: SessionId) {
            state.value = state.value.filterNot { it.id == id }
        }
    }
}
