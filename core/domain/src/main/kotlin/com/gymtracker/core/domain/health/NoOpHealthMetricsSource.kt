package com.gymtracker.core.domain.health

import com.gymtracker.core.domain.model.SessionMetrics
import java.time.Instant

/**
 * The default [HealthMetricsSource] binding (`tech-stack.md`'s optional-feature contract):
 * always [HealthStatus.Unavailable], always `null` metrics. Every screen must render correctly
 * against this implementation — that is what the optional-feature test suite asserts.
 *
 * Lives in `:core:domain`, not `:feature:health`, so `:app` can bind the no-op path without
 * depending on the optional module at all (ADR-0038). Provided via `@Provides` in `:app`'s DI
 * wiring rather than `@Inject`-constructed — no domain class carries a Hilt annotation, the
 * same reason every use case in `DataModule.kt` is wired that way.
 */
class NoOpHealthMetricsSource : HealthMetricsSource {
    override suspend fun status(): HealthStatus = HealthStatus.Unavailable

    override suspend fun metricsFor(window: ClosedRange<Instant>): SessionMetrics? = null
}
