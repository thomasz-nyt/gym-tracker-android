package com.gymtracker.core.data.routine

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymtracker.core.data.exercise.ExerciseEntity
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.UserId
import java.time.Instant

/**
 * The `routines` table from `data-model.md` (US-29, ADR-0020).
 *
 * `created_at`, `updated_at` and `sync_state` carry no meaning until M2, but they are part of
 * the schema of record, so they are here from the start rather than added by a later
 * migration — the same reasoning `SessionEntity` records.
 */
@Entity(
    tableName = "routines",
    indices = [Index(value = ["user_id", "position"])],
)
data class RoutineEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
)

/**
 * The `routine_items` table.
 *
 * `target_sets`, `target_reps` and `target_weight_kg` arrived at v8 (US-30, ADR-0027),
 * superseding ADR-0020's "nowhere to store a target" — each is independently nullable, so a
 * movement can have all, some, or none of the three set.
 */
@Entity(
    tableName = "routine_items",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
        ),
    ],
    indices = [Index(value = ["routine_id", "position"]), Index(value = ["exercise_id"])],
)
data class RoutineItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
    @ColumnInfo(name = "target_sets") val targetSets: Int? = null,
    @ColumnInfo(name = "target_reps") val targetReps: Int? = null,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double? = null,
)

internal fun RoutineEntity.toDomain(): Routine =
    Routine(
        id = RoutineId(id),
        userId = UserId(userId),
        name = name,
        position = position,
    )

internal fun RoutineItemEntity.toDomain(): RoutineItem =
    RoutineItem(
        id = RoutineItemId(id),
        routineId = RoutineId(routineId),
        exerciseId = ExerciseId(exerciseId),
        position = position,
        target = toTarget(),
    )

/**
 * Used by both `addItem` and `updateItem` (US-30): the same row shape whether it is being
 * inserted for the first time or written back after [SetRoutineItemTarget][com.gymtracker.core
 * .domain.routine.SetRoutineItemTarget] changed its target.
 */
internal fun RoutineItem.toEntity(updatedAt: Long = Instant.now().toEpochMilli()): RoutineItemEntity =
    RoutineItemEntity(
        id = id.value,
        routineId = routineId.value,
        exerciseId = exerciseId.value,
        position = position,
        updatedAt = updatedAt,
        syncState = SYNC_STATE_PENDING,
        targetSets = target?.sets,
        targetReps = target?.reps,
        targetWeightKg = target?.weightKg,
    )

/**
 * Reconstructs a [MovementTarget], or null if every column is null — the row carries no plan,
 * not a plan of all-nulls (US-13's absence pattern, kept even though the domain type could
 * technically represent both).
 */
internal fun RoutineItemEntity.toTarget(): MovementTarget? =
    if (targetSets == null && targetReps == null && targetWeightKg == null) {
        null
    } else {
        MovementTarget(sets = targetSets, reps = targetReps, weightKg = targetWeightKg)
    }
