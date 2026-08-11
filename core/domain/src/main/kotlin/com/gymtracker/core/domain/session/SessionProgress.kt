package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.WorkoutSession

/**
 * What a session knows about its own progress: how many movements are done, which one is
 * current, and which are still to come.
 *
 * Unwired groundwork, not yet read by any screen — see `roadmap.md`'s entry for the ADR this
 * revisits (`adr/0023-the-rest-period-earns-its-space.md`) and the follow-up ADR a UI needs
 * before this is shown anywhere.
 *
 * @property current the earliest movement, in position order, with no set logged yet. Null
 *   once every movement has at least one set — there is nothing left to name as next.
 * @property stillToCome every other untouched movement, after [current], in position order.
 *   A movement already done is never in this list even if a later one is still untouched
 *   (US-02 lets a member log out of order); "current" and "still to come" both read the plan's
 *   order, not the order sets happened to land in.
 * @property orderIsAPlan whether [SessionExercise.position] reflects a deliberate order rather
 *   than the order movements were added. True only for a session started from a routine
 *   (US-32) — ADR-0023 refused to render an order-implying claim ("then Seated Cable Rows")
 *   for a freestyle session, because a freestyle session's position is add-order, not a plan.
 */
data class SessionProgress(
    val movementsTotal: Int,
    val movementsDone: Int,
    val current: SessionExercise?,
    val stillToCome: List<SessionExercise>,
    val orderIsAPlan: Boolean,
) {
    companion object {
        /**
         * [exercises] and [sets] may cover several sessions, matching [SessionSummary.of]'s own
         * convention, so this filters to [session] rather than trusting its input to have been
         * narrowed already.
         */
        fun of(
            session: WorkoutSession,
            exercises: List<SessionExercise>,
            sets: List<ExerciseSet>,
        ): SessionProgress {
            val ordered = exercises.filter { it.sessionId == session.id }.sortedBy { it.position }
            val performedIds = sets.map { it.sessionExerciseId }.toSet()
            val untouched = ordered.filter { it.id !in performedIds }

            return SessionProgress(
                movementsTotal = ordered.size,
                movementsDone = ordered.size - untouched.size,
                current = untouched.firstOrNull(),
                stillToCome = untouched.drop(1),
                orderIsAPlan = session.routine != null,
            )
        }
    }
}
