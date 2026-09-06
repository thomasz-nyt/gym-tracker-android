package com.gymtracker.core.data.sync

import com.gymtracker.core.data.routine.RoutineEntity
import com.gymtracker.core.data.routine.RoutineItemEntity
import com.gymtracker.core.data.session.SessionEntity
import com.gymtracker.core.data.sessionexercise.SessionExerciseEntity
import com.gymtracker.core.data.set.SetEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Serialises a Room entity into `sync_queue.payload_json` (US-57). See `SyncPayload.kt`'s KDoc
 * for why this is a separate, row-shaped codec rather than a reuse of `BackupCodec`.
 */
class SyncPayloadCodec
    @Inject
    constructor(
        private val json: Json,
    ) {
        fun encode(entity: SessionEntity): String = json.encodeToString(SyncSessionDto.serializer(), entity.toSyncDto())

        fun encode(entity: SessionExerciseEntity): String =
            json.encodeToString(SyncSessionExerciseDto.serializer(), entity.toSyncDto())

        fun encode(entity: SetEntity): String = json.encodeToString(SyncSetDto.serializer(), entity.toSyncDto())

        fun encode(entity: RoutineEntity): String = json.encodeToString(SyncRoutineDto.serializer(), entity.toSyncDto())

        fun encode(entity: RoutineItemEntity): String =
            json.encodeToString(SyncRoutineItemDto.serializer(), entity.toSyncDto())
    }

private fun SessionEntity.toSyncDto() =
    SyncSessionDto(
        id = id,
        userId = userId,
        gymName = gymName,
        startedAt = startedAt,
        endedAt = endedAt,
        avgHr = avgHr,
        maxHr = maxHr,
        activeKcal = activeKcal,
        metricsSource = metricsSource,
        routineName = routineName,
        routineId = routineId,
        updatedAt = updatedAt,
    )

private fun SessionExerciseEntity.toSyncDto() =
    SyncSessionExerciseDto(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        position = position,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightKg = targetWeightKg,
        targetRestSeconds = targetRestSeconds,
        updatedAt = updatedAt,
    )

private fun SetEntity.toSyncDto() =
    SyncSetDto(
        id = id,
        sessionExerciseId = sessionExerciseId,
        setIndex = setIndex,
        weightKg = weightKg,
        reps = reps,
        rpe = rpe,
        performedAt = performedAt,
        updatedAt = updatedAt,
    )

private fun RoutineEntity.toSyncDto() =
    SyncRoutineDto(
        id = id,
        userId = userId,
        name = name,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun RoutineItemEntity.toSyncDto() =
    SyncRoutineItemDto(
        id = id,
        routineId = routineId,
        exerciseId = exerciseId,
        position = position,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightKg = targetWeightKg,
        targetRestSeconds = targetRestSeconds,
        updatedAt = updatedAt,
    )
