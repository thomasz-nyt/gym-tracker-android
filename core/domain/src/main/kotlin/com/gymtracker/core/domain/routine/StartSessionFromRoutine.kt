package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.session.StartSessionResult
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession

/**
 * Starts Tuesday's routine as an ordinary session (US-29, ADR-0020; targets: US-30, ADR-0027;
 * provenance: US-32, ADR-0028).
 *
 * The copy is the whole mechanism, and it is one-way. The routine's movements — and each
 * movement's target, if it has one — are appended to a fresh session in order. The session also
 * carries the routine's name and id as dead provenance ([RoutineOrigin]), written once here and
 * never read back through [routines] or [items] afterward — there is still no foreign key, and
 * still nothing to join on. Every M1 story — US-02a/b/c, US-03, US-04, US-05a — therefore works
 * on the result unchanged, because the result is not a special kind of session.
 *
 * A copied target, and the copied provenance, are both snapshots, not pointers: editing the
 * routine afterward — its targets, its name, deleting it entirely — does not reach back into a
 * session already started, and editing today's session still never edits the routine (ADR-0020's
 * rule, unchanged by ADR-0027 and ADR-0028).
 *
 * With no link back to the plan, no screen can join a session to its routine — the structural
 * half of constitution §2.4 that ADR-0020 bought. ADR-0027 spent part of that guarantee
 * deliberately (a target now travels into the session) and replaced it with a labelling rule.
 * ADR-0028 spends a different, narrower part — cross-session identity, not planned-versus-actual
 * — and replaces it with the rule that [RoutineOrigin.id] is written but never read.
 */
class StartSessionFromRoutine(
    private val routines: RoutineRepository,
    private val items: RoutineItemRepository,
    private val startSession: StartSession,
    private val addExerciseToSession: AddExerciseToSession,
) {
    /**
     * @return how the session began, or null if [routineId] does not exist — in which case no
     *   session is created, so a mistyped id cannot leave an empty workout behind.
     *
     * If the member is already in a workout, US-01's one-active-session rule wins: the running
     * session is returned as [StartSessionResult.Resumed] and **nothing is copied into it**,
     * including the routine's provenance. Pouring six movements into a workout in progress would
     * be an unasked-for edit of what is on screen, so the caller is told what happened and
     * decides what to say about it.
     */
    suspend operator fun invoke(
        routineId: RoutineId,
        userId: UserId,
    ): StartSessionResult? {
        val routine = routines.find(routineId) ?: return null
        val movements = items.itemsOf(routineId)

        val result = startSession(userId, RoutineOrigin(id = routine.id.value, name = routine.name))
        if (result is StartSessionResult.Started) {
            // In sequence, never concurrently: AddExerciseToSession takes its position from
            // MAX(position) + 1, so parallel appends would read the same maximum and collide.
            movements.forEach {
                addExerciseToSession(result.session.id, it.exerciseId, it.target)
            }
        }
        return result
    }
}
