package com.gymtracker.core.domain.model

import java.time.Instant

/**
 * A gym session: a thin container that groups sets (`data-model.md`).
 *
 * @property endedAt null while the session is active. It is only ever set from a real
 *   timestamp — the app never invents an end time (US-01).
 * @property metrics null unless a health source provided them. Absence is a first-class
 *   state, never zero (constitution §2, §5).
 */
data class WorkoutSession(
    val id: SessionId,
    val userId: UserId,
    val gymName: String?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val metrics: SessionMetrics?,
) {
    /** True while this session is the member's active one. */
    val isActive: Boolean get() = endedAt == null
}

/** Wearable-derived session summary. Every field is nullable, always. */
data class SessionMetrics(
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val activeKilocalories: Int?,
    val source: String?,
)
