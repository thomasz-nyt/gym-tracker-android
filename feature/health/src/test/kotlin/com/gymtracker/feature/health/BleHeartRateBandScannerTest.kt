package com.gymtracker.feature.health

import app.cash.turbine.test
import com.gymtracker.core.domain.health.HeartRateBandAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class BleHeartRateBandScannerTest {
    @Test
    fun `not supported is Unavailable`() {
        val gateway = FakeScanGateway(supported = false)

        assertEquals(HeartRateBandAvailability.Unavailable, BleHeartRateBandScanner(gateway).availability())
    }

    @Test
    fun `supported but missing either permission is PermissionRequired`() {
        assertEquals(
            HeartRateBandAvailability.PermissionRequired,
            BleHeartRateBandScanner(FakeScanGateway(hasScanPermission = false)).availability(),
        )
        assertEquals(
            HeartRateBandAvailability.PermissionRequired,
            BleHeartRateBandScanner(FakeScanGateway(hasConnectPermission = false)).availability(),
        )
    }

    @Test
    fun `supported with both permissions is Ready`() {
        assertEquals(HeartRateBandAvailability.Ready, BleHeartRateBandScanner(FakeScanGateway()).availability())
    }

    @Test
    fun `not supported finds nothing, never starts a scan`() =
        runTest {
            val gateway = FakeScanGateway(supported = false)

            BleHeartRateBandScanner(gateway).scan().test {
                awaitComplete()
            }
            assertEquals(false, gateway.scanStarted)
        }

    @Test
    fun `no scan permission finds nothing, never starts a scan`() =
        runTest {
            val gateway = FakeScanGateway(hasScanPermission = false)

            BleHeartRateBandScanner(gateway).scan().test {
                awaitComplete()
            }
            assertEquals(false, gateway.scanStarted)
        }

    @Test
    fun `supported and permitted maps each discovered device`() =
        runTest {
            val gateway =
                FakeScanGateway(
                    devices = listOf(DiscoveredDevice("AA:BB", "Charge 6"), DiscoveredDevice("CC:DD", null)),
                )

            BleHeartRateBandScanner(gateway).scan().test {
                assertEquals("AA:BB", awaitItem().address)
                assertEquals("CC:DD", awaitItem().address)
                awaitComplete()
            }
        }

    private class FakeScanGateway(
        private val supported: Boolean = true,
        private val hasScanPermission: Boolean = true,
        private val hasConnectPermission: Boolean = true,
        private val devices: List<DiscoveredDevice> = emptyList(),
    ) : HeartRateBandGateway {
        var scanStarted: Boolean = false
            private set

        override fun isSupported(): Boolean = supported

        override fun hasScanPermission(): Boolean = hasScanPermission

        override fun hasConnectPermission(): Boolean = hasConnectPermission

        override fun scanForDevices(): Flow<DiscoveredDevice> {
            scanStarted = true
            return flowOf(*devices.toTypedArray())
        }

        override fun connect(address: String): Flow<GattEvent> = throw UnsupportedOperationException()
    }
}
