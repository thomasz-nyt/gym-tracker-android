package com.gymtracker.core.data.session

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import java.time.Instant

/**
 * The `sessions` table from `data-model.md`. Instants are stored as epoch milliseconds.
 *
 * `updated_at` and `sync_state` carry no meaning until the M2 sync engine exists, but they
 * are part of the schema of record, so they are here from the start rather than added by a
 * migration later.
 */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["user_id", "started_at"])],
)
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "gym_name") val gymName: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "avg_hr") val avgHr: Int?,
    @ColumnInfo(name = "max_hr") val maxHr: Int?,
    @ColumnInfo(name = "active_kcal") val activeKcal: Int?,
    @ColumnInfo(name = "metrics_source") val metricsSource: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
)

/** Not yet synced. The only value M1 ever writes; see `data-model.md` § Sync. */
const val SYNC_STATE_PENDING = "PENDING"

internal fun SessionEntity.toDomain(): WorkoutSession =
    WorkoutSession(
        id = SessionId(id),
        userId = UserId(userId),
        gymName = gymName,
        startedAt = Instant.ofEpochMilli(startedAt),
        endedAt = endedAt?.let(Instant::ofEpochMilli),
        metrics = metrics(),
    )

/** Null unless a health source actually provided something — never a zero-filled object. */
private fun SessionEntity.metrics(): SessionMetrics? {
    val hasNothing = avgHr == null && maxHr == null && activeKcal == null && metricsSource == null
    return if (hasNothing) {
        null
    } else {
        SessionMetrics(
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            activeKilocalories = activeKcal,
            source = metricsSource,
        )
    }
}

internal fun WorkoutSession.toEntity(updatedAt: Instant = Instant.now()): SessionEntity =
    SessionEntity(
        id = id.value,
        userId = userId.value,
        gymName = gymName,
        startedAt = startedAt.toEpochMilli(),
        endedAt = endedAt?.toEpochMilli(),
        avgHr = metrics?.avgHeartRate,
        maxHr = metrics?.maxHeartRate,
        activeKcal = metrics?.activeKilocalories,
        metricsSource = metrics?.source,
        updatedAt = updatedAt.toEpochMilli(),
        syncState = SYNC_STATE_PENDING,
    )
