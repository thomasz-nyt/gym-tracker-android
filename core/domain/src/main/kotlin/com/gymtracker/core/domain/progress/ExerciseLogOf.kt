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
 * One session's worth of one exercise, for [ExerciseLogOf] rather than [ExerciseTrendOf]'s
 * chart: the sets themselves are kept, not discarded into an aggregate.
 */
data class ExerciseLogEntry(
    val performedOn: LocalDate,
    val sets: List<ExerciseSet>,
    /** The heaviest set actually lifted that day. Null when nothing was loaded. */
    val topSetKg: Double?,
    /** The best [Epley] estimate of the day, and an estimate wherever it is shown. */
    val estimatedOneRepMaxKg: Double?,
)

/**
 * One exercise's log, a row per session (US-34): what was actually done, newest first — the
 * opposite reading direction from [ExerciseTrendOf]'s chart.
 *
 * Applies the same "which sessions counted" rule [ExerciseTrendOf] does — only finished
 * sessions, and only ones with at least one set actually logged for this exercise — so the
 * chart and the log can never disagree about which sessions counted. The three-read shape is
 * deliberately duplicated rather than shared: the sets survive here instead of being folded
 * into one point, which [ExerciseTrendOf]'s return type has no room for.
 *
 * @param zone the member's zone, for turning an instant into the day they would call it.
 */
class ExerciseLogOf(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
    private val zone: ZoneId,
) {
    suspend operator fun invoke(
        exerciseId: ExerciseId,
        member: UserId,
    ): List<ExerciseLogEntry> {
        val finished = sessions.observeFinishedSessions(member).first()
        if (finished.isEmpty()) return emptyList()

        val sessionIds = finished.map { it.id }
        val appearancesBySession =
            sessionExercises
                .observeForSessions(sessionIds)
                .first()
                .filter { it.exerciseId == exerciseId }
                .groupBy { it.sessionId }
        val setsByAppearance = sets.observeForSessions(sessionIds).first().groupBy { it.sessionExerciseId }

        return finished
            .sortedByDescending { it.startedAt }
            .mapNotNull { session ->
                val performed =
                    appearancesBySession[session.id]
                        .orEmpty()
                        .flatMap { setsByAppearance[it.id].orEmpty() }
                performed.takeIf { it.isNotEmpty() }?.toEntry(session.startedAt.atZone(zone).toLocalDate())
            }
    }

    private fun List<ExerciseSet>.toEntry(performedOn: LocalDate): ExerciseLogEntry {
        val loaded = filter { it.weightKg != null }

        return ExerciseLogEntry(
            performedOn = performedOn,
            sets = this,
            topSetKg = loaded.maxOfOrNull { it.weightKg!! },
            estimatedOneRepMaxKg = mapNotNull { Epley.oneRepMax(it.weightKg, it.reps) }.maxOrNull(),
        )
    }
}
