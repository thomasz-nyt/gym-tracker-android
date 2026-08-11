package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.WorkoutSession
import java.time.Duration

/**
 * One row of the history list (US-06): a finished session with the counts and volume its
 * sets add up to.
 *
 * @property exerciseCount appearances of an exercise **that have at least one set logged** —
 *   coming back to an exercise later counts it twice (ADR-0004), but adding one and never
 *   getting to it counts zero. That second case is common once a session can start from a
 *   routine (US-29): every movement is copied in whether or not the member reaches it, so
 *   counting rows rather than work would make this figure describe the plan, not the session
 *   (constitution §2.4).
 * @property volumeKg total weight moved, summed over the sets that have a weight recorded.
 *   Null when none of them did.
 * @property bodyweightSetCount sets logged without a weight. Reported separately and never
 *   folded into [volumeKg] as zero: a bodyweight set is one whose load is unknown, not one
 *   that moved nothing (constitution §2).
 */
data class SessionSummary(
    val session: WorkoutSession,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeKg: Double?,
    val bodyweightSetCount: Int,
) {
    /** How long the session ran, or null while it is still open — never a guess. */
    val duration: Duration?
        get() = session.endedAt?.let { Duration.between(session.startedAt, it) }

    companion object {
        /**
         * Summarises one session.
         *
         * [exercises] and [sets] may cover several sessions — history fetches them for the
         * whole list in one query each — so this filters to [session] rather than trusting
         * its input to have been narrowed already.
         */
        fun of(
            session: WorkoutSession,
            exercises: List<SessionExercise>,
            sets: List<ExerciseSet>,
        ): SessionSummary {
            val appearances = exercises.filter { it.sessionId == session.id }.map { it.id }.toSet()
            val performed = sets.filter { it.sessionExerciseId in appearances }
            val weighted = performed.mapNotNull { set -> set.weightKg?.let { it * set.reps } }
            val appearancesWithSets = performed.map { it.sessionExerciseId }.toSet()

            return SessionSummary(
                session = session,
                exerciseCount = appearancesWithSets.size,
                setCount = performed.size,
                volumeKg = if (weighted.isEmpty()) null else weighted.sum(),
                bodyweightSetCount = performed.count { it.weightKg == null },
            )
        }
    }
}
