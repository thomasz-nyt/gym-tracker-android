package com.gymtracker.core.data.set

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymtracker.core.data.sessionexercise.SessionExerciseEntity
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import java.time.Instant

/**
 * The `sets` table from `data-model.md`, hanging off `session_exercises` per ADR-0004.
 *
 * `weight_kg` is nullable because a bodyweight movement has no weight — absent, never zero
 * (constitution §2).
 */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_exercise_id", "performed_at"])],
)
data class SetEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_exercise_id") val sessionExerciseId: String,
    @ColumnInfo(name = "set_index") val setIndex: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    @ColumnInfo(name = "reps") val reps: Int,
    @ColumnInfo(name = "rpe") val rpe: Double?,
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
)

internal fun SetEntity.toDomain(): ExerciseSet =
    ExerciseSet(
        id = id,
        sessionExerciseId = SessionExerciseId(sessionExerciseId),
        setIndex = setIndex,
        weightKg = weightKg,
        reps = reps,
        rpe = rpe,
        performedAt = Instant.ofEpochMilli(performedAt),
    )
