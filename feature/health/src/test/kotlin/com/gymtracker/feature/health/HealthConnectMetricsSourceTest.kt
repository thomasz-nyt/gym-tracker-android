package com.gymtracker.feature.health

import androidx.health.connect.client.HealthConnectClient
import com.gymtracker.core.domain.health.HealthPermission
import com.gymtracker.core.domain.health.HealthStatus
import com.gymtracker.core.domain.model.SessionMetrics
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `specs/health-connect.md` test matrix that does not need a device: every branch of
 * [HealthConnectMetricsSource] is a pure function of [FakeHealthConnectGateway], which touches
 * neither the real SDK nor `HealthIntegration` — see [HealthStatus]'s class doc for why the
 * toggle is deliberately not one of this class's inputs.
 *
 * Runs under Robolectric only because the fault-injection case exercises a real
 * `android.util.Log.w()` call — every other case here is plain JVM logic.
 */
@RunWith(RobolectricTestRunner::class)
class HealthConnectMetricsSourceTest {
    private val window = Instant.parse("2026-08-19T18:00:00Z")..Instant.parse("2026-08-19T19:00:00Z")

    @Test
    fun `not installed is Unavailable`() =
        runTest {
            val gateway = FakeHealthConnectGateway(sdkStatus = HealthConnectClient.SDK_UNAVAILABLE)

            assertEquals(HealthStatus.Unavailable, source(gateway).status())
        }

    @Test
    fun `needs a provider update is Unavailable, the same as not installed`() =
        runTest {
            val gateway =
                FakeHealthConnectGateway(
                    sdkStatus = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
                )

            assertEquals(HealthStatus.Unavailable, source(gateway).status())
        }

    @Test
    fun `installed, no permissions granted yet is PermissionRequired`() =
        runTest {
            val gateway = FakeHealthConnectGateway(sdkStatus = HealthConnectClient.SDK_AVAILABLE, granted = emptySet())

            assertEquals(HealthStatus.PermissionRequired, source(gateway).status())
        }

    @Test
    fun `at least one permission granted is Ready`() =
        runTest {
            val gateway = ready(granted = setOf(HealthPermission.HEART_RATE.id))

            assertEquals(HealthStatus.Ready, source(gateway).status())
        }

    @Test
    fun `a permission revoked between two calls is reflected on the next call, never cached`() =
        runTest {
            val gateway = ready(granted = setOf(HealthPermission.HEART_RATE.id))
            val underTest = source(gateway)

            assertEquals(HealthStatus.Ready, underTest.status())

            gateway.granted = emptySet()

            assertEquals(HealthStatus.PermissionRequired, underTest.status())
        }

    // --- metricsFor (US-22) ---

    @Test
    fun `not Ready reads nothing at all and returns null`() =
        runTest {
            val gateway = FakeHealthConnectGateway(sdkStatus = HealthConnectClient.SDK_UNAVAILABLE)

            assertNull(source(gateway).metricsFor(window))
            assertEquals(0, gateway.heartRateCalls)
        }

    @Test
    fun `heart rate samples average and peak, active calories sum`() =
        runTest {
            val gateway =
                ready(
                    granted = setOf(HealthPermission.HEART_RATE.id, HealthPermission.ACTIVE_CALORIES.id),
                    heartRateBpm = listOf(110L, 130L, 150L),
                    activeCaloriesKcal = listOf(120.0, 90.4),
                )

            val metrics = source(gateway).metricsFor(window)

            assertEquals(SessionMetrics(130, 150, 210, "health_connect"), metrics)
        }

    @Test
    fun `Ready but no samples stores nulls with a source, not null overall`() =
        runTest {
            val gateway = ready(granted = setOf(HealthPermission.HEART_RATE.id, HealthPermission.ACTIVE_CALORIES.id))

            val metrics = source(gateway).metricsFor(window)

            assertEquals(SessionMetrics(null, null, null, "health_connect"), metrics)
        }

    @Test
    fun `a metric with no permission granted is never read and stays null`() =
        runTest {
            // Only ACTIVE_CALORIES granted — heart rate must not even be queried.
            val gateway =
                ready(
                    granted = setOf(HealthPermission.ACTIVE_CALORIES.id),
                    heartRateBpm = listOf(999L),
                    activeCaloriesKcal = listOf(50.0),
                )

            val metrics = source(gateway).metricsFor(window)

            assertEquals(0, gateway.heartRateCalls)
            assertEquals(SessionMetrics(null, null, 50, "health_connect"), metrics)
        }

    @Test
    fun `the exercise permission narrows the window the other two are read over`() =
        runTest {
            val narrower = Instant.parse("2026-08-19T18:10:00Z")..Instant.parse("2026-08-19T18:50:00Z")
            val gateway =
                ready(
                    granted = setOf(HealthPermission.HEART_RATE.id, HealthPermission.EXERCISE.id),
                    heartRateBpm = listOf(120L),
                    exerciseWindow = narrower,
                )

            source(gateway).metricsFor(window)

            assertEquals(narrower, gateway.lastHeartRateWindow)
        }

    @Test
    fun `without the exercise permission the raw window is used unchanged`() =
        runTest {
            val gateway = ready(granted = setOf(HealthPermission.HEART_RATE.id), heartRateBpm = listOf(120L))

            source(gateway).metricsFor(window)

            assertEquals(window, gateway.lastHeartRateWindow)
        }

    @Test
    fun `a real SDK failure degrades to null rather than propagating`() =
        runTest {
            // Caught live on device (API 36): a real HealthConnectException can come out of
            // readRecords for reasons this class has no control over — this is an enhancement
            // layer (constitution §3), so a read that fails must never surface as a crash or a
            // written-but-wrong result.
            val gateway =
                FaultyHealthConnectGateway(
                    granted = setOf(HealthPermission.HEART_RATE.id),
                    failure = IllegalStateException("Incorrect health permission state"),
                )

            assertNull(source(gateway).metricsFor(window))
        }

    private fun ready(
        granted: Set<String>,
        heartRateBpm: List<Long> = emptyList(),
        activeCaloriesKcal: List<Double> = emptyList(),
        exerciseWindow: ClosedRange<Instant>? = null,
    ) = FakeHealthConnectGateway(
        sdkStatus = HealthConnectClient.SDK_AVAILABLE,
        granted = granted,
        heartRateBpm = heartRateBpm,
        activeCaloriesKcal = activeCaloriesKcal,
        exerciseWindow = exerciseWindow,
    )

    private fun source(gateway: HealthConnectGateway) = HealthConnectMetricsSource(gateway)
}

private class FakeHealthConnectGateway(
    private val sdkStatus: Int,
    var granted: Set<String> = emptySet(),
    private val heartRateBpm: List<Long> = emptyList(),
    private val activeCaloriesKcal: List<Double> = emptyList(),
    private val exerciseWindow: ClosedRange<Instant>? = null,
) : HealthConnectGateway {
    var heartRateCalls: Int = 0
        private set
    var lastHeartRateWindow: ClosedRange<Instant>? = null
        private set

    override fun sdkStatus(): Int = sdkStatus

    override suspend fun grantedPermissions(): Set<String> = granted

    override suspend fun heartRateBpm(window: ClosedRange<Instant>): List<Long> {
        heartRateCalls++
        lastHeartRateWindow = window
        return heartRateBpm
    }

    override suspend fun activeCaloriesKcal(window: ClosedRange<Instant>): List<Double> = activeCaloriesKcal

    override suspend fun exerciseSessionWindow(window: ClosedRange<Instant>): ClosedRange<Instant>? = exerciseWindow
}

/** SDK available and Ready, but every read throws — the real failure mode found on device. */
private class FaultyHealthConnectGateway(
    private val granted: Set<String>,
    private val failure: Throwable,
) : HealthConnectGateway {
    override fun sdkStatus(): Int = HealthConnectClient.SDK_AVAILABLE

    override suspend fun grantedPermissions(): Set<String> = granted

    override suspend fun heartRateBpm(window: ClosedRange<Instant>): List<Long> = throw failure

    override suspend fun activeCaloriesKcal(window: ClosedRange<Instant>): List<Double> = throw failure

    override suspend fun exerciseSessionWindow(window: ClosedRange<Instant>): ClosedRange<Instant>? = throw failure
}
