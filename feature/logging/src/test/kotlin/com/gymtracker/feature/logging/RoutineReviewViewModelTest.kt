package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.model.WorkoutWorkflow
import com.gymtracker.core.domain.routine.FakeRoutineItemRepository
import com.gymtracker.core.domain.routine.FakeRoutineRepository
import com.gymtracker.core.domain.routine.RoutineReuse
import com.gymtracker.core.domain.routine.RoutineReuseChoice
import com.gymtracker.core.domain.routine.RoutineTemplateStore
import com.gymtracker.core.domain.routine.RoutineTemplateWrite
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineReviewViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun cleanup() = Dispatchers.resetMain()

    @Test
    fun `review waits for one delayed template save and repeated taps cannot save twice`() =
        runTest {
            val owner = UserId("alice")
            val routines = FakeRoutineRepository()
            val items = FakeRoutineItemRepository()
            val sessions = FakeSessionRepository()
            val movements = FakeSessionExerciseRepository()
            val routine = Routine(RoutineId("upper"), owner, "Upper A", 1)
            val baseline = RoutineItem(RoutineItemId("source"), routine.id, ExerciseId("bench"), 1)
            val workout =
                WorkoutSession(
                    SessionId("visit"),
                    owner,
                    null,
                    Instant.EPOCH,
                    Instant.EPOCH.plusSeconds(60),
                    null,
                    RoutineOrigin(routine.id.value, routine.name),
                    WorkoutWorkflow(originalRoutineItems = listOf(baseline)),
                )
            routines.add(routine)
            items.addItem(baseline)
            sessions.startSession(workout)
            movements.add(SessionExercise(SessionExerciseId("changed"), workout.id, ExerciseId("row"), 1))
            var writes = 0
            val store =
                object : RoutineTemplateStore {
                    override suspend fun reviewed(
                        session: WorkoutSession,
                        signature: String,
                    ) = true

                    override suspend fun apply(write: RoutineTemplateWrite): Boolean {
                        writes++
                        delay(100)
                        return true
                    }
                }
            val reuse = RoutineReuse(sessions, movements, routines, items, store) { "new" }
            val vm = RoutineReviewViewModel(reuse, FakeCurrentMember(owner), FakeCatalog(), FakeUnitPreference())
            vm.open(workout.id)
            runCurrent()
            assertNotNull(vm.uiState.value.review)
            vm.choose(RoutineReuseChoice.UPDATE_ORIGINAL)
            vm.choose(RoutineReuseChoice.UPDATE_ORIGINAL)
            assertTrue(vm.uiState.value.isSaving)
            assertNotNull(vm.uiState.value.review)
            advanceUntilIdle()
            assertEquals(1, writes)
            assertNull(vm.uiState.value.review)
            assertEquals(workout.endedAt, sessions.findSession(workout.id)?.endedAt)
        }
}
