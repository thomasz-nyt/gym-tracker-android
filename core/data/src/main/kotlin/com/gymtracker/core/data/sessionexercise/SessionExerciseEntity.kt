package com.gymtracker.core.data.sessionexercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymtracker.core.data.session.SessionEntity
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId

/**
 * The `session_exercises` table from `data-model.md` (ADR-0004).
 *
 * The index on `exercise_id` is what makes the US-03 prefill and the M4 charts reach a set's
 * exercise without a denormalised column on `sets`.
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
)

internal fun SessionExerciseEntity.toDomain(): SessionExercise =
    SessionExercise(
        id = SessionExerciseId(id),
        sessionId = SessionId(sessionId),
        exerciseId = ExerciseId(exerciseId),
        position = position,
    )
