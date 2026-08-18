package com.gymtracker.core.domain.health

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-20: the default binding must be silent. Both members of the contract — [HealthStatus] and
 * the metrics read — are pinned here so a future edit cannot make the no-op path report
 * anything but absence.
 */
class NoOpHealthMetricsSourceTest {
    private val source = NoOpHealthMetricsSource()

    @Test
    fun `status is always Unavailable`() =
        runTest {
            assertEquals(HealthStatus.Unavailable, source.status())
        }

    @Test
    fun `metrics are always null, for any window`() =
        runTest {
            val window = Instant.parse("2026-08-18T09:00:00Z")..Instant.parse("2026-08-18T10:00:00Z")

            assertNull(source.metricsFor(window))
        }
}
