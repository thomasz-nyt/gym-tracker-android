package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetGroup
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * One exercise as it was performed in a past workout (US-06b).
 *
 * @property exercise the catalog entry, or null if the row somehow outlived it. The schema
 *   forbids that; the type stays honest about it rather than forcing a lie.
 * @property groups the sets, collapsed for reading by [SetGroup] (ADR-0009). The rows
 *   underneath stay separate.
 * @property volumeKg weight moved on this exercise, over the sets that recorded one. Null
 *   when none did — never zero, per constitution §2.4.
 * @property bodyweightSetCount sets logged without a weight, reported rather than folded in.
 */
data class PerformedExercise(
    val sessionExercise: SessionExercise,
    val exercise: Exercise?,
    val sets: List<ExerciseSet>,
    val groups: List<SetGroup>,
    val volumeKg: Double?,
    val bodyweightSetCount: Int,
)

/**
 * What a past workout actually contained (US-06b).
 *
 * History gives the totals; this gives the contents, so "what did I do on Tuesday, and at what
 * weight" has an answer. [summary] is the same [SessionSummary] the list row shows, so the two
 * can never disagree about a count.
 *
 * @property exercises in `position` order — the order the workout was performed in, unlike the
 *   active session, which shows the newest first (US-02b).
 */
data class SessionDetail(
    val summary: SessionSummary,
    val exercises: List<PerformedExercise>,
)

/**
 * Reads one past workout in full (US-06b).
 *
 * Reuses the queries history already has rather than adding per-session ones: both repositories
 * take a list of session ids, and a list of one is a perfectly good list.
 */
class WorkoutDetail(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
    private val catalog: ExerciseCatalog,
) {
    /** Null while the session does not exist — deleted from under the screen, say (US-06a). */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        id: SessionId,
        member: UserId,
    ): Flow<SessionDetail?> =
        sessions.observeFinishedSessions(member).flatMapLatest { finished ->
            val session = finished.firstOrNull { it.id == id }
            if (session == null) {
                flowOf(null)
            } else {
                val ids = listOf(id)
                combine(
                    sessionExercises.observeForSessions(ids),
                    sets.observeForSessions(ids),
                    catalog.search("", member),
                ) { appearances, performed, allExercises ->
                    val byId = allExercises.associateBy(Exercise::id)
                    SessionDetail(
                        summary = SessionSummary.of(session, appearances, performed),
                        exercises =
                            appearances.map { appearance ->
                                performedExercise(appearance, performed, byId[appearance.exerciseId])
                            },
                    )
                }
            }
        }

    private fun performedExercise(
        appearance: SessionExercise,
        allSets: List<ExerciseSet>,
        exercise: Exercise?,
    ): PerformedExercise {
        val mine = allSets.filter { it.sessionExerciseId == appearance.id }.sortedBy { it.setIndex }
        val weighted = mine.mapNotNull { set -> set.weightKg?.let { it * set.reps } }

        return PerformedExercise(
            sessionExercise = appearance,
            exercise = exercise,
            sets = mine,
            groups = SetGroup.of(mine),
            volumeKg = if (weighted.isEmpty()) null else weighted.sum(),
            bodyweightSetCount = mine.count { it.weightKg == null },
        )
    }
}
