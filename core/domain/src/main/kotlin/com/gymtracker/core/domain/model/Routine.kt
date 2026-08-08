package com.gymtracker.core.domain.model

/**
 * A saved shape: a name, and an order (US-29, ADR-0020).
 *
 * Read the absences here as the design. There is no sets count, no rep count and no load,
 * because a routine says *which movements, in what order* and nothing about what to lift.
 * The numbers the routine screens put beside each movement come from `sets` through
 * `PrefillFromLastSet` — they are what someone actually lifted, labelled as history.
 *
 * That is what keeps ADR-0009's and ADR-0017's rejection of a prescription entity intact
 * while still answering the audit's finding 01. A list of names is not a value, so
 * constitution §2.4 has nothing to be dishonest about.
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
 * Carries no target, deliberately — see [Routine]. The same exercise may appear twice, as it
 * may in a session (US-02), and each appearance is its own row so they can be reordered and
 * removed separately.
 */
data class RoutineItem(
    val id: RoutineItemId,
    val routineId: RoutineId,
    val exerciseId: ExerciseId,
    /** 1-based order within the routine. */
    val position: Int,
)
