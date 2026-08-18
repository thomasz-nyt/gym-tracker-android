package com.gymtracker.feature.health

import androidx.health.connect.client.HealthConnectClient
import com.gymtracker.core.domain.health.HealthStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `specs/health-connect.md` test matrix that does not need a device or Robolectric: every
 * branch of [HealthConnectMetricsSource.status] is a pure function of [FakeHealthConnectGateway],
 * which touches neither the real SDK nor `HealthIntegration` — see [HealthStatus]'s class doc for
 * why the toggle is deliberately not one of this class's inputs.
 */
class HealthConnectMetricsSourceTest {
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
            val gateway =
                FakeHealthConnectGateway(
                    sdkStatus = HealthConnectClient.SDK_AVAILABLE,
                    granted = setOf("android.permission.health.READ_HEART_RATE"),
                )

            assertEquals(HealthStatus.Ready, source(gateway).status())
        }

    @Test
    fun `a permission revoked between two calls is reflected on the next call, never cached`() =
        runTest {
            val gateway =
                FakeHealthConnectGateway(
                    sdkStatus = HealthConnectClient.SDK_AVAILABLE,
                    granted = setOf("android.permission.health.READ_HEART_RATE"),
                )
            val underTest = source(gateway)

            assertEquals(HealthStatus.Ready, underTest.status())

            gateway.granted = emptySet()

            assertEquals(HealthStatus.PermissionRequired, underTest.status())
        }

    @Test
    fun `metricsFor is a stub in this PR — always null`() =
        runTest {
            val gateway =
                FakeHealthConnectGateway(
                    sdkStatus = HealthConnectClient.SDK_AVAILABLE,
                    granted = setOf("android.permission.health.READ_HEART_RATE"),
                )
            val window = Instant.parse("2026-08-18T09:00:00Z")..Instant.parse("2026-08-18T10:00:00Z")

            assertNull(source(gateway).metricsFor(window))
        }

    private fun source(gateway: FakeHealthConnectGateway) = HealthConnectMetricsSource(gateway)
}

private class FakeHealthConnectGateway(
    private val sdkStatus: Int,
    var granted: Set<String> = emptySet(),
) : HealthConnectGateway {
    override fun sdkStatus(): Int = sdkStatus

    override suspend fun grantedPermissions(): Set<String> = granted
}
