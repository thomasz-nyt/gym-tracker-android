package com.gymtracker.core.domain.model

/**
 * A saved shape: a name, and an order (US-29, ADR-0020).
 *
 * The routine itself still carries no number — its identity is *which movements, in what
 * order*. What each movement plans for is [RoutineItem.target] (US-30, ADR-0027), which is
 * where "no sets count, no rep count, no load" used to sit before the maintainer asked for it
 * back. See that ADR for why moving the absence from the routine's items to the routine object
 * itself keeps constitution §2.4 answerable: a target is still labelled as a target everywhere
 * it renders, never merged with what `sets` says was actually lifted.
 */
data class Routine(
    val id: RoutineId,
    val userId: UserId,
    val name: String,
    /** 1-based order in the member's list of routines. */
    val position: Int,
)

/**
 * One movement's place in a [Routine].
 *
 * [target] arrived with US-30 (ADR-0027), superseding the "carries no target, deliberately"
 * rule this class's KDoc used to state — see that ADR for what was given up to allow it, and
 * the labelling rule that replaces it: a target is always rendered as a target, never merged
 * with what [SessionExercise] shows as history. The same exercise may appear twice, as it may
 * in a session (US-02), and each appearance is its own row so they can be reordered, removed
 * and targeted separately.
 */
data class RoutineItem(
    val id: RoutineItemId,
    val routineId: RoutineId,
    val exerciseId: ExerciseId,
    /** 1-based order within the routine. */
    val position: Int,
    val target: MovementTarget? = null,
)
