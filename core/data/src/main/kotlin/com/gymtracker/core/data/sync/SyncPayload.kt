package com.gymtracker.core.data.sync

import kotlinx.serialization.Serializable

/**
 * The shape [SyncPayloadCodec] writes into `sync_queue.payload_json` (US-57, ADR-0043).
 *
 * Row-shaped, not domain-shaped like `BackupEnvelope.kt`'s DTOs: each type here mirrors exactly
 * the columns that table's own Postgres mirror in `data-model.md` §Postgres has, field for
 * field, including `updated_at` (which `BackupCodec` deliberately drops) — never `sync_state`,
 * which is Room-only bookkeeping with no Postgres column to receive it. `user_id` appears only
 * on [SyncSessionDto] and [SyncRoutineDto], matching the two tables that actually carry the
 * column in both schemas; the other three are reached through their parent's RLS instead
 * (ADR-0043's amendment).
 */
@Serializable
internal data class SyncSessionDto(
    val id: String,
    val userId: String,
    val gymName: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val activeKcal: Int? = null,
    val metricsSource: String? = null,
    val routineName: String? = null,
    val routineId: String? = null,
    val updatedAt: Long,
)

@Serializable
internal data class SyncSessionExerciseDto(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
    val targetRestSeconds: Int? = null,
    val updatedAt: Long,
)

@Serializable
internal data class SyncSetDto(
    val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val weightKg: Double? = null,
    val reps: Int,
    val rpe: Double? = null,
    val performedAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class SyncRoutineDto(
    val id: String,
    val userId: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class SyncRoutineItemDto(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
    val targetRestSeconds: Int? = null,
    val updatedAt: Long,
)
