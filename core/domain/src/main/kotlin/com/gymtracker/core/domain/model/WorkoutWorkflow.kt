package com.gymtracker.core.domain.model

import java.time.Instant

/** Durable decisions belonging to this workout, never inferred performed data (US-63/65). */
data class WorkoutWorkflow(
    val selected: SessionExerciseId? = null,
    val originalRoutineItems: List<RoutineItem> = emptyList(),
    val reviewedStructure: String? = null,
    val structuralEdits: Boolean = false,
    val reopenEligible: Boolean = true,
    val reopened: ReopenedWorkout? = null,
)

/** The original finish and actual-set identities captured when a recent workout reopens. */
data class ReopenedWorkout(
    val endedAt: Instant,
    val setIds: Set<String>,
)

/** Explicit terminal choices, separate from meeting a planned set count. */
enum class MovementOutcome {
    DONE,
    ENDED_EARLY,
    SKIPPED,
}

/** What the actual records and explicit choice establish, without inventing missing work. */
enum class MovementStatus {
    NOT_STARTED,
    IN_PROGRESS,
    PLAN_MET,
    DONE,
    ENDED_EARLY,
    SKIPPED,
    ;

    /** Whether an explicit Next action may leave this movement. */
    val isFinished: Boolean get() = this != NOT_STARTED && this != IN_PROGRESS
}

/** Derives progress for one movement occurrence from its actual sets (US-63). */
fun SessionExercise.status(sets: List<ExerciseSet>): MovementStatus {
    val count = sets.count { it.sessionExerciseId == id }
    val planned = target?.sets
    return when {
        planned != null && count >= planned -> MovementStatus.PLAN_MET
        count == 0 && outcome == MovementOutcome.SKIPPED -> MovementStatus.SKIPPED
        count == 0 -> MovementStatus.NOT_STARTED
        outcome == MovementOutcome.DONE && planned == null -> MovementStatus.DONE
        outcome == MovementOutcome.ENDED_EARLY -> MovementStatus.ENDED_EARLY
        else -> MovementStatus.IN_PROGRESS
    }
}
