package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.WorkoutSession

/** A reviewed template replacement and its compare-before-write preconditions (US-65). */
data class RoutineTemplateWrite(
    val expectedRoutine: Routine?,
    val expectedItems: List<RoutineItem>,
    val replacement: Routine,
    val replacementItems: List<RoutineItem>,
    val session: WorkoutSession,
    val signature: String,
)

/** Template replacement and the handled-workout marker share one persistence transaction. */
interface RoutineTemplateStore {
    /** False means the original or finished workout changed; neither template nor marker is written. */
    suspend fun apply(write: RoutineTemplateWrite): Boolean

    /** Marks Today only without rewriting a template or an end time; refuses an active workout. */
    suspend fun reviewed(
        session: WorkoutSession,
        signature: String,
    ): Boolean
}
