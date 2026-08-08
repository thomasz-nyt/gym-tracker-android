package com.gymtracker.core.data.routine

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymtracker.core.data.exercise.ExerciseEntity
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.UserId

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
 * There is no weight, rep or set column, and that is the ADR-0020 decision expressed in the
 * schema: with nowhere to store a target, no screen can grow one by accident.
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
    )
