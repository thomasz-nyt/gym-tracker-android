package com.gymtracker.feature.health

import androidx.health.connect.client.HealthConnectClient
import com.gymtracker.core.domain.health.HealthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `specs/health-connect.md` test matrix that does not need a device or Robolectric: every
 * branch of [HealthConnectMetricsSource.status] is a pure function of [FakeHealthConnectGateway]
 * and [FakeHealthIntegration], neither of which touches the real SDK.
 */
class HealthConnectMetricsSourceTest {
    @Test
    fun `not installed is Unavailable, regardless of the toggle`() =
        runTest {
            val gateway = FakeHealthConnectGateway(sdkStatus = HealthConnectClient.SDK_UNAVAILABLE)
            val toggle = FakeHealthIntegration(enabled = true)

            assertEquals(HealthStatus.Unavailable, source(gateway, toggle).status())
        }

    @Test
    fun `needs a provider update is Unavailable, the same as not installed`() =
        runTest {
            val gateway =
                FakeHealthConnectGateway(
                    sdkStatus = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
                )
            val toggle = FakeHealthIntegration(enabled = true)

            assertEquals(HealthStatus.Unavailable, source(gateway, toggle).status())
        }

    @Test
    fun `installed but the toggle is off is Unavailable, indistinguishable from not installed`() =
        runTest {
            val gateway = FakeHealthConnectGateway(sdkStatus = HealthConnectClient.SDK_AVAILABLE)
            val toggle = FakeHealthIntegration(enabled = false)

            assertEquals(HealthStatus.Unavailable, source(gateway, toggle).status())
        }

    @Test
    fun `toggle on, no permissions granted is PermissionRequired`() =
        runTest {
            val gateway =
                FakeHealthConnectGateway(sdkStatus = HealthConnectClient.SDK_AVAILABLE, granted = emptySet())
            val toggle = FakeHealthIntegration(enabled = true)

            assertEquals(HealthStatus.PermissionRequired, source(gateway, toggle).status())
        }

    @Test
    fun `toggle on, at least one permission granted is Ready`() =
        runTest {
            val gateway =
                FakeHealthConnectGateway(
                    sdkStatus = HealthConnectClient.SDK_AVAILABLE,
                    granted = setOf("android.permission.health.READ_HEART_RATE"),
                )
            val toggle = FakeHealthIntegration(enabled = true)

            assertEquals(HealthStatus.Ready, source(gateway, toggle).status())
        }

    @Test
    fun `a permission revoked between two calls is reflected on the next call, never cached`() =
        runTest {
            val gateway =
                FakeHealthConnectGateway(
                    sdkStatus = HealthConnectClient.SDK_AVAILABLE,
                    granted = setOf("android.permission.health.READ_HEART_RATE"),
                )
            val toggle = FakeHealthIntegration(enabled = true)
            val underTest = source(gateway, toggle)

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
            val toggle = FakeHealthIntegration(enabled = true)
            val window = Instant.parse("2026-08-18T09:00:00Z")..Instant.parse("2026-08-18T10:00:00Z")

            assertNull(source(gateway, toggle).metricsFor(window))
        }

    private fun source(
        gateway: FakeHealthConnectGateway,
        toggle: FakeHealthIntegration,
    ) = HealthConnectMetricsSource(gateway, toggle)
}

private class FakeHealthConnectGateway(
    private val sdkStatus: Int,
    var granted: Set<String> = emptySet(),
) : HealthConnectGateway {
    override fun sdkStatus(): Int = sdkStatus

    override suspend fun grantedPermissions(): Set<String> = granted
}

/** A minimal [com.gymtracker.core.domain.health.HealthIntegration] fake — no DataStore, no Android. */
private class FakeHealthIntegration(
    enabled: Boolean,
) : com.gymtracker.core.domain.health.HealthIntegration {
    private val state = MutableStateFlow(enabled)

    override fun observe() = state

    override suspend fun current(): Boolean = state.value

    override suspend fun set(enabled: Boolean) {
        state.value = enabled
    }
}
