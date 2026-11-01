package com.gymtracker.core.data.session

import com.gymtracker.core.data.backup.RoutineItemDto
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.ReopenedWorkout
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.WorkoutWorkflow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** Additive stored workflow state, shared by Room and the complete backup (US-63/65). */
@Serializable
internal data class WorkflowDto(
    val selected: String? = null,
    val originalRoutineItems: List<RoutineItemDto> = emptyList(),
    val reviewedStructure: String? = null,
    val structuralEdits: Boolean = false,
    val reopenEligible: Boolean = true,
    val reopenedEndedAt: Long? = null,
    val reopenedSetIds: Set<String> = emptySet(),
)

internal object WorkflowCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(workflow: WorkoutWorkflow): String? =
        if (workflow == WorkoutWorkflow()) null else json.encodeToString(WorkflowDto.serializer(), toDto(workflow))

    fun decode(raw: String?): WorkoutWorkflow =
        raw?.let { fromDto(json.decodeFromString(WorkflowDto.serializer(), it)) } ?: WorkoutWorkflow()

    fun toDto(workflow: WorkoutWorkflow): WorkflowDto =
        WorkflowDto(
            selected = workflow.selected?.value,
            originalRoutineItems = workflow.originalRoutineItems.map { it.dto() },
            reviewedStructure = workflow.reviewedStructure,
            structuralEdits = workflow.structuralEdits,
            reopenEligible = workflow.reopenEligible,
            reopenedEndedAt = workflow.reopened?.endedAt?.toEpochMilli(),
            reopenedSetIds = workflow.reopened?.setIds.orEmpty(),
        )

    fun fromDto(dto: WorkflowDto?): WorkoutWorkflow =
        dto?.let {
            WorkoutWorkflow(
                selected = it.selected?.let(::SessionExerciseId),
                originalRoutineItems = it.originalRoutineItems.map { row -> row.domain() },
                reviewedStructure = it.reviewedStructure,
                structuralEdits = it.structuralEdits,
                reopenEligible = it.reopenEligible,
                reopened =
                    it.reopenedEndedAt?.let { end ->
                        ReopenedWorkout(Instant.ofEpochMilli(end), it.reopenedSetIds)
                    },
            )
        } ?: WorkoutWorkflow()

    private fun RoutineItem.dto() =
        RoutineItemDto(
            id.value,
            routineId.value,
            exerciseId.value,
            position,
            target?.sets,
            target?.reps,
            target?.weightKg,
            target?.restSeconds,
        )

    private fun RoutineItemDto.domain(): RoutineItem {
        val target =
            if (listOfNotNull(targetSets, targetReps, targetWeightKg, targetRestSeconds).isEmpty()) {
                null
            } else {
                MovementTarget(targetSets, targetReps, targetWeightKg, targetRestSeconds)
            }
        return RoutineItem(RoutineItemId(id), RoutineId(routineId), ExerciseId(exerciseId), position, target)
    }
}
