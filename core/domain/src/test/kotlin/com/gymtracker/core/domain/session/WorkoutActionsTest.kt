package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementOutcome
import com.gymtracker.core.domain.model.MovementStatus
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.model.status
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutActionsTest {
    private val member = UserId("alice")
    private val id = SessionId("workout")
    private val now = Instant.EPOCH
    private val sessions = FakeSessionRepository()
    private val movements = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val first = SessionExercise(SessionExerciseId("a"), id, ExerciseId("bench"), 1, MovementTarget(3, 12, 50.0))
    private val second = first.copy(id = SessionExerciseId("b"), position = 2)

    @Test
    fun `selection survives reconstruction and next requires an explicit selection`() =
        runTest {
            sessions.startSession(WorkoutSession(id, member, null, now, null, null))
            movements.add(first)
            movements.add(second)
            WorkoutActions(sessions, movements, sets).select(id, second.id)
            assertEquals(second.id, sessions.findSession(id)?.workflow?.selected)
            sets.add(ExerciseSet("s1", second.id, 1, 50.0, 10, null, now))
            assertEquals(second.id, sessions.findSession(id)?.workflow?.selected)
        }

    @Test
    fun `outcomes require actual records and never manufacture sets`() =
        runTest {
            sessions.startSession(WorkoutSession(id, member, null, now, null, null))
            movements.add(first)
            val actions = WorkoutActions(sessions, movements, sets)
            assertFalse(actions.outcome(first.id, MovementOutcome.DONE))
            assertFalse(actions.outcome(first.id, MovementOutcome.ENDED_EARLY))
            assertTrue(actions.outcome(first.id, MovementOutcome.SKIPPED))
            sets.add(ExerciseSet("s1", first.id, 1, 50.0, 10, null, now))
            assertFalse(actions.outcome(first.id, MovementOutcome.SKIPPED))
            assertTrue(actions.outcome(first.id, MovementOutcome.ENDED_EARLY))
            assertEquals(
                MovementStatus.ENDED_EARLY,
                movements.find(first.id)!!.status(sets.observeForSessionExercise(first.id).first()),
            )
            assertEquals(1, sets.observeForSessionExercise(first.id).first().size)
        }

    @Test
    fun `moving preserves occurrence identity targets and actual sets`() =
        runTest {
            sessions.startSession(WorkoutSession(id, member, null, now, null, null))
            movements.add(first)
            movements.add(second)
            WorkoutActions(sessions, movements, sets).move(second.id, -1)
            assertEquals(listOf(second.id, first.id), movements.observeForSession(id).first().map { it.id })
            assertEquals(first.target, movements.find(first.id)?.target)
            assertTrue(sessions.findSession(id)!!.workflow.structuralEdits)
        }
}
