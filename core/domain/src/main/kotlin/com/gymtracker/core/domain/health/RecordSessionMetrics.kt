package com.gymtracker.core.domain.health

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.session.SessionRepository
import java.time.Instant

/**
 * Reads and stores a session's health metrics once it has ended (US-22).
 *
 * Combines the two independent gates ADR-0038 requires: [HealthIntegration] (the member's own
 * opt-in) and [HealthMetricsSource.metricsFor]'s own [HealthStatus.Ready] check. Neither alone
 * is enough — see [HealthStatus]'s class doc for why folding one into the other was tried and
 * reverted. A toggle-off member never reaches [HealthMetricsSource] at all, which matters for
 * [com.gymtracker.core.domain.health.NoOpHealthMetricsSource]'s callers just as much as for the
 * real one: no wasted read, not just a discarded result.
 */
class RecordSessionMetrics(
    private val healthIntegration: HealthIntegration,
    private val healthMetricsSource: HealthMetricsSource,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(
        id: SessionId,
        window: ClosedRange<Instant>,
    ) {
        if (!healthIntegration.current()) return
        val metrics = healthMetricsSource.metricsFor(window) ?: return
        sessions.saveMetrics(id, metrics)
    }
}
