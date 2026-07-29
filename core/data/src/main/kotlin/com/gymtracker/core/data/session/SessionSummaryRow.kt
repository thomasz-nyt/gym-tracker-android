package com.gymtracker.core.data.session

import androidx.room.ColumnInfo

/**
 * The aggregate a history row needs, computed in SQL rather than by loading every set.
 *
 * `volume_kg` is null when the session has no weighted sets, because `SUM` over an empty
 * set is null — which is exactly the meaning US-06 wants (see `Volume`). Bodyweight sets
 * are skipped by the `weight_kg IS NOT NULL` filter rather than counted as zero.
 */
data class SessionSummaryRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "exercise_count") val exerciseCount: Int,
    @ColumnInfo(name = "set_count") val setCount: Int,
    @ColumnInfo(name = "volume_kg") val volumeKg: Double?,
)
