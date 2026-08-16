package com.gymtracker.core.data.sessionexercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.data.session.SessionEntity
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import java.time.Instant

/**
 * The `session_exercises` table from `data-model.md` (ADR-0004).
 *
 * The index on `exercise_id` is what makes the US-03 prefill and the M4 charts reach a set's
 * exercise without a denormalised column on `sets`.
 *
 * `target_sets`, `target_reps` and `target_weight_kg` arrived at v8 (US-30, ADR-0027): a
 * snapshot [com.gymtracker.core.domain.routine.StartSessionFromRoutine] copies from a
 * [com.gymtracker.core.data.routine.RoutineItemEntity] when it has one, never a reference back
 * to it. Purely additive — `sessions` and `sets` gained nothing at this migration.
 */
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"]), Index(value = ["exercise_id"])],
)
data class SessionExerciseEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
    @ColumnInfo(name = "target_sets") val targetSets: Int? = null,
    @ColumnInfo(name = "target_reps") val targetReps: Int? = null,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double? = null,
)

internal fun SessionExerciseEntity.toDomain(): SessionExercise =
    SessionExercise(
        id = SessionExerciseId(id),
        sessionId = SessionId(sessionId),
        exerciseId = ExerciseId(exerciseId),
        position = position,
        target = toTarget(targetSets, targetReps, targetWeightKg),
    )

internal fun SessionExercise.toEntity(updatedAt: Instant = Instant.now()): SessionExerciseEntity =
    SessionExerciseEntity(
        id = id.value,
        sessionId = sessionId.value,
        exerciseId = exerciseId.value,
        position = position,
        updatedAt = updatedAt.toEpochMilli(),
        syncState = SYNC_STATE_PENDING,
        targetSets = target?.sets,
        targetReps = target?.reps,
        targetWeightKg = target?.weightKg,
    )

/**
 * Shares [MovementTarget]'s all-null-means-absent rule with
 * [com.gymtracker.core.data.routine.RoutineItemEntity.toTarget] without sharing an entity type
 * — `routine_items` and `session_exercises` are unrelated tables that happen to carry the same
 * three columns.
 */
private fun toTarget(
    sets: Int?,
    reps: Int?,
    weightKg: Double?,
): MovementTarget? =
    if (sets == null && reps == null && weightKg == null) null else MovementTarget(sets, reps, weightKg)
