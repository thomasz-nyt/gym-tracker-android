package com.gymtracker.core.domain.history

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/**
 * One row of the history list (US-06).
 *
 * @property volumeKg total weight moved, or null when nothing weighted was logged — see
 *   [Volume]. Null is rendered as a dash, never as zero.
 */
data class SessionSummary(
    val id: SessionId,
    val startedAt: Instant,
    val endedAt: Instant,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeKg: Double?,
) {
    val duration: Duration get() = Duration.between(startedAt, endedAt)
}

/** Finished sessions, newest first (US-06). */
interface SessionHistory {
    fun observeHistory(userId: UserId): Flow<List<SessionSummary>>
}
