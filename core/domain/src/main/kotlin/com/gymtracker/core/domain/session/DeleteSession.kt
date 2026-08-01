package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first

/**
 * A deleted workout, complete enough to put back (US-06a, ADR-0012).
 *
 * Held in memory for the length of the undo window and nowhere else. Restoring it re-inserts
 * these exact rows, ids included, so an undone delete leaves the same workout rather than a
 * copy of it.
 */
data class DeletedSession(
    val session: WorkoutSession,
    val exercises: List<SessionExercise>,
    val sets: List<ExerciseSet>,
)

/**
 * Deletes a past workout, returning what it took so [RestoreSession] can undo it.
 *
 * The delete is real immediately — ADR-0012 rejected deferring it, because a deferred delete
 * is silently cancelled when the screen that scheduled it goes away, leaving the member with
 * a workout they asked to be rid of.
 *
 * Exercises and sets are not deleted here. `session_exercises.session_id` and
 * `sets.session_exercise_id` are `ON DELETE CASCADE` in the schema, which is the one place
 * that rule can be enforced for every caller.
 *
 * @return null if there was no such session, meaning there is nothing to undo either.
 */
class DeleteSession(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
) {
    suspend operator fun invoke(id: SessionId): DeletedSession? {
        val session = sessions.findSession(id) ?: return null
        val ids = listOf(id)

        // Read the children before the delete takes them.
        val snapshot =
            DeletedSession(
                session = session,
                exercises = sessionExercises.observeForSessions(ids).first(),
                sets = sets.observeForSessions(ids).first(),
            )

        sessions.deleteSession(id)
        return snapshot
    }
}

/**
 * Puts a deleted workout back (US-06a).
 *
 * Written in dependency order — session, then its exercises, then their sets — because the
 * foreign keys that cascade the delete also reject a child inserted before its parent.
 */
class RestoreSession(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
) {
    suspend operator fun invoke(deleted: DeletedSession) {
        sessions.restoreSession(deleted.session)
        deleted.exercises.forEach { sessionExercises.add(it) }
        deleted.sets.forEach { sets.add(it) }
    }
}
