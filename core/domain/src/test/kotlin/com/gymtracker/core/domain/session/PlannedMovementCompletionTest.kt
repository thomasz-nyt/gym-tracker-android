package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class PlannedMovementCompletionTest {
    private val id = SessionId("workout")
    private val now = Instant.parse("2026-10-29T14:00:00Z")
    private val session = WorkoutSession(id, UserId("alice"), null, now, null, null)
    private val movement =
        SessionExercise(SessionExerciseId("bench"), id, ExerciseId("bench"), 1, MovementTarget(3, 12, 50.0))

    @Test
    fun `one of three actual sets does not complete the planned movement`() {
        val actual = listOf(ExerciseSet("s1", movement.id, 1, 50.0, 12, null, now))

        assertEquals(0, SessionProgress.of(session, listOf(movement), actual).movementsDone)
        assertEquals(movement.id, SessionProgress.of(session, listOf(movement), actual).current?.id)
    }

    @Test
    fun `three actual sets meet the count even when reps differ from the target`() {
        val actual =
            listOf(12, 10, 8).mapIndexed { index, reps ->
                ExerciseSet("s$index", movement.id, index + 1, 50.0, reps, null, now)
            }

        assertEquals(1, SessionProgress.of(session, listOf(movement), actual).movementsDone)
    }

    @Test
    fun `an unplanned set count is not automatically done after one set`() {
        val unplanned = movement.copy(target = null)
        val actual = listOf(ExerciseSet("s1", movement.id, 1, null, 10, null, now))

        assertEquals(0, SessionProgress.of(session, listOf(unplanned), actual).movementsDone)
    }
}
