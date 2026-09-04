package com.gymtracker.feature.logging.session

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.feature.logging.SessionExerciseRow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-53: "The next exercise **in the session's plan**, if any, is shown on the step under a
 * `THEN` label." `ActiveSession`'s own `exercises` parameter is US-02b's newest-first display
 * order (`rows.asReversed()`, in `ActiveSessionViewModel`), so reading its `firstOrNull()`
 * — the shape `WarmUpStep`'s call site used — names the most recently *added* exercise, not the
 * first one *in the plan*. [firstExerciseInPlanOrder] is the pure fix: plan order is
 * `SessionExercise.position`, ascending, regardless of what order the list arrives in.
 */
class WarmUpNextExerciseTest {
    @Test
    fun `picks the lowest position, not the head of a reversed list`() {
        // Same shape ActiveSession actually receives: newest-added (highest position) first.
        val newestFirst = listOf(row(position = 3, id = "c"), row(position = 2, id = "b"), row(position = 1, id = "a"))

        assertEquals("a", firstExerciseInPlanOrder(newestFirst)?.sessionExercise?.id?.value)
    }

    @Test
    fun `is independent of the list's own order`() {
        val outOfOrder = listOf(row(position = 2, id = "b"), row(position = 1, id = "a"), row(position = 3, id = "c"))

        assertEquals("a", firstExerciseInPlanOrder(outOfOrder)?.sessionExercise?.id?.value)
    }

    @Test
    fun `absent, not blank, when there are no exercises yet`() {
        assertNull(firstExerciseInPlanOrder(emptyList()))
    }

    private fun row(
        position: Int,
        id: String,
    ) = SessionExerciseRow(
        sessionExercise =
            SessionExercise(
                id = SessionExerciseId(id),
                sessionId = SessionId("session-1"),
                exerciseId = ExerciseId("exercise-$id"),
                position = position,
                target = null,
            ),
        exercise = null,
    )
}
