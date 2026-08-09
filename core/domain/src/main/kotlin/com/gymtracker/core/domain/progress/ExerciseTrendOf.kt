package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * One exercise's history, a point per session (US-16).
 *
 * Reads finished sessions only. A workout in progress is not yet a data point — half of it
 * has not happened, and a chart that moved while you were still lifting would be reporting a
 * day that is not over.
 *
 * **A point is a session, not a date.** Two workouts in one day are two points, because two
 * workouts are what happened; merging them would report a day nobody trained that way. They
 * are therefore ordered by the *instant* a session started rather than by its date — ordering
 * by date is stable, so same-day sessions kept the repository's newest-first order and the
 * chart ran backwards through them. Two *appearances of the same exercise* within one session
 * are one point, because that is one day's work on that movement (US-02 allows it twice).
 *
 * @param zone the member's zone, for turning an instant into the day they would call it.
 */
class ExerciseTrendOf(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
    private val zone: ZoneId,
) {
    suspend operator fun invoke(
        exerciseId: ExerciseId,
        member: UserId,
    ): ExerciseTrend {
        val finished = sessions.observeFinishedSessions(member).first()
        val points = if (finished.isEmpty()) emptyList() else pointsOf(exerciseId, finished)

        return when (points.size) {
            0 -> ExerciseTrend.NoData
            1 -> ExerciseTrend.SinglePoint(points.single())
            else -> ExerciseTrend.Series(points)
        }
    }

    /**
     * Three reads, whatever the length of the history: the sessions, their appearances of this
     * exercise, and their sets — then grouped in memory. Not a query per session, which is what
     * the milestone's 200-session criterion is really checking.
     */
    private suspend fun pointsOf(
        exerciseId: ExerciseId,
        finished: List<com.gymtracker.core.domain.model.WorkoutSession>,
    ): List<ExerciseTrendPoint> {
        val sessionIds = finished.map { it.id }
        val appearancesBySession =
            sessionExercises
                .observeForSessions(sessionIds)
                .first()
                .filter { it.exerciseId == exerciseId }
                .groupBy { it.sessionId }
        val setsByAppearance = sets.observeForSessions(sessionIds).first().groupBy { it.sessionExerciseId }

        return finished
            // By the instant, not the day. Two workouts on one date are two points, and
            // sorting on the date alone is stable — so they kept whatever order the repository
            // returned, which is newest first, and the chart ran backwards through them.
            .sortedBy { it.startedAt }
            .mapNotNull { session ->
                val performed =
                    appearancesBySession[session.id]
                        .orEmpty()
                        .flatMap { setsByAppearance[it.id].orEmpty() }
                // An exercise added and never performed is not a point: drawing it would put a
                // zero on the chart for a day nothing was lifted (§2.4).
                performed.takeIf { it.isNotEmpty() }?.toPoint(session.startedAt.atZone(zone).toLocalDate())
            }
    }

    /**
     * Everything the day is worth saying, computed only from sets that carried a load.
     *
     * A bodyweight set contributes to [ExerciseTrendPoint.sets] and to nothing else: it has no
     * weight to be the top set, nothing to multiply into volume, and nothing to estimate a
     * maximum from. The nulls that fall out of an all-bodyweight day are the point — zero
     * would claim the session was weightless.
     */
    private fun List<ExerciseSet>.toPoint(performedOn: LocalDate): ExerciseTrendPoint {
        val loaded = filter { it.weightKg != null }

        return ExerciseTrendPoint(
            performedOn = performedOn,
            topSetKg = loaded.maxOfOrNull { it.weightKg!! },
            volumeKg = loaded.takeIf { it.isNotEmpty() }?.sumOf { it.weightKg!! * it.reps },
            estimatedOneRepMaxKg = mapNotNull { Epley.oneRepMax(it.weightKg, it.reps) }.maxOrNull(),
            sets = size,
        )
    }
}
