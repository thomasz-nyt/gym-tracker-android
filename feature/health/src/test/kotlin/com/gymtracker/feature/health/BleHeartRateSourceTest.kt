package com.gymtracker.feature.health

import app.cash.turbine.test
import com.gymtracker.core.domain.health.HeartRateBandPreference
import com.gymtracker.core.domain.health.HeartRateBandSelection
import com.gymtracker.core.domain.health.LiveHeartRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * US-46 … US-48: every branch of [BleHeartRateSource] is a pure function of
 * [FakeHeartRateBandGateway] and [FakeHeartRateBandPreference], the same seam-plus-fake split
 * `HealthConnectMetricsSourceTest` uses — no Robolectric, no device.
 */
class BleHeartRateSourceTest {
    @Test
    fun `toggle off is Unavailable`() =
        runTest {
            val source = source(preference = FakeHeartRateBandPreference(enabled = false, deviceAddress = "AA"))

            source.observe().test {
                assertEquals(LiveHeartRate.Unavailable, awaitItem())
            }
        }

    @Test
    fun `toggle on but no device chosen yet is Unavailable`() =
        runTest {
            val source = source(preference = FakeHeartRateBandPreference(enabled = true, deviceAddress = null))

            source.observe().test {
                assertEquals(LiveHeartRate.Unavailable, awaitItem())
            }
        }

    @Test
    fun `not supported (below API 31, or no adapter) is Unavailable`() =
        runTest {
            val source = source(gateway = FakeHeartRateBandGateway(supported = false))

            source.observe().test {
                assertEquals(LiveHeartRate.Unavailable, awaitItem())
            }
        }

    @Test
    fun `no connect permission is Unavailable`() =
        runTest {
            val source = source(gateway = FakeHeartRateBandGateway(hasConnectPermission = false))

            source.observe().test {
                assertEquals(LiveHeartRate.Unavailable, awaitItem())
            }
        }

    @Test
    fun `enabled with a device connects and shows Searching before any reading`() =
        runTest {
            val gateway = FakeHeartRateBandGateway()
            val source = source(gateway = gateway)

            source.observe().test {
                assertEquals(LiveHeartRate.Searching, awaitItem())

                gateway.emit("AA", GattEvent.Connected)
                assertEquals(LiveHeartRate.Searching, awaitItem())
            }
        }

    @Test
    fun `a measurement notification becomes Beating`() =
        runTest {
            val gateway = FakeHeartRateBandGateway()
            val source = source(gateway = gateway)

            source.observe().test {
                assertEquals(LiveHeartRate.Searching, awaitItem())
                gateway.emit("AA", GattEvent.Connected)
                assertEquals(LiveHeartRate.Searching, awaitItem())

                // flags = 0x00 (uint8, no energy expended), BPM = 0x64 = 100
                gateway.emit("AA", GattEvent.MeasurementReceived(byteArrayOf(0x00, 0x64)))

                assertEquals(LiveHeartRate.Beating(bpm = 100, energyExpendedKilocalories = null), awaitItem())
            }
        }

    @Test
    fun `a malformed notification is not treated as a reading`() =
        runTest {
            val gateway = FakeHeartRateBandGateway()
            val source = source(gateway = gateway)

            source.observe().test {
                assertEquals(LiveHeartRate.Searching, awaitItem())
                gateway.emit("AA", GattEvent.Connected)
                assertEquals(LiveHeartRate.Searching, awaitItem())

                gateway.emit("AA", GattEvent.MeasurementReceived(byteArrayOf()))

                expectNoEvents()
            }
        }

    @Test
    fun `disconnecting after a reading moves to Lost, not a stale Beating`() =
        runTest {
            val gateway = FakeHeartRateBandGateway()
            val source = source(gateway = gateway)

            source.observe().test {
                skipItems(1) // Searching
                gateway.emit("AA", GattEvent.Connected)
                skipItems(1) // Searching
                gateway.emit("AA", GattEvent.MeasurementReceived(byteArrayOf(0x00, 0x64)))
                skipItems(1) // Beating

                gateway.emit("AA", GattEvent.Disconnected)

                assertEquals(LiveHeartRate.Lost, awaitItem())
            }
        }

    @Test
    fun `no new reading within the staleness window moves to Lost (US-48)`() =
        runTest {
            val gateway = FakeHeartRateBandGateway()
            val source = source(gateway = gateway)

            source.observe().test {
                skipItems(1) // Searching
                gateway.emit("AA", GattEvent.Connected)
                skipItems(1) // Searching
                gateway.emit("AA", GattEvent.MeasurementReceived(byteArrayOf(0x00, 0x64)))
                skipItems(1) // Beating

                advanceTimeBy(BleHeartRateSource.STALE_TIMEOUT_MILLIS + 1_000)

                assertEquals(LiveHeartRate.Lost, awaitItem())
            }
        }

    @Test
    fun `a fresh reading resets the staleness window rather than going stale early`() =
        runTest {
            val gateway = FakeHeartRateBandGateway()
            val source = source(gateway = gateway)

            source.observe().test {
                skipItems(1) // Searching
                gateway.emit("AA", GattEvent.Connected)
                skipItems(1) // Searching
                gateway.emit("AA", GattEvent.MeasurementReceived(byteArrayOf(0x00, 0x64)))
                skipItems(1) // Beating

                advanceTimeBy(BleHeartRateSource.STALE_TIMEOUT_MILLIS - 1_000)
                gateway.emit("AA", GattEvent.MeasurementReceived(byteArrayOf(0x00, 0x65)))
                assertEquals(LiveHeartRate.Beating(bpm = 0x65, energyExpendedKilocalories = null), awaitItem())

                // The window since the FIRST reading would have expired by now; since the
                // second reading reset it, no Lost should appear yet.
                advanceTimeBy(BleHeartRateSource.STALE_TIMEOUT_MILLIS - 1_000)
                expectNoEvents()
            }
        }

    @Test
    fun `turning the toggle off drops the connection immediately (US-49)`() =
        runTest {
            val gateway = FakeHeartRateBandGateway()
            val preference = FakeHeartRateBandPreference(enabled = true, deviceAddress = "AA")
            val source = source(gateway = gateway, preference = preference)

            source.observe().test {
                skipItems(1) // Searching
                gateway.emit("AA", GattEvent.Connected)
                skipItems(1) // Searching

                preference.setEnabled(false)

                assertEquals(LiveHeartRate.Unavailable, awaitItem())
                assertEquals(true, gateway.connectionClosedFor("AA"))
            }
        }

    private fun source(
        gateway: FakeHeartRateBandGateway = FakeHeartRateBandGateway(),
        preference: FakeHeartRateBandPreference = FakeHeartRateBandPreference(enabled = true, deviceAddress = "AA"),
    ) = BleHeartRateSource(gateway, preference)
}

private class FakeHeartRateBandPreference(
    enabled: Boolean,
    deviceAddress: String?,
) : HeartRateBandPreference {
    private val state = MutableStateFlow(HeartRateBandSelection(enabled, deviceAddress))

    override fun observe(): Flow<HeartRateBandSelection> = state

    override suspend fun current(): HeartRateBandSelection = state.value

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = state.value.copy(enabled = enabled)
    }

    override suspend fun setDevice(address: String?) {
        state.value = state.value.copy(deviceAddress = address)
    }
}

private class FakeHeartRateBandGateway(
    private val supported: Boolean = true,
    private val hasConnectPermission: Boolean = true,
) : HeartRateBandGateway {
    private val events = mutableMapOf<String, MutableSharedFlow<GattEvent>>()
    private val closed = mutableSetOf<String>()

    override fun isSupported(): Boolean = supported

    override fun hasScanPermission(): Boolean = true

    override fun hasConnectPermission(): Boolean = hasConnectPermission

    override fun scanForDevices(): Flow<DiscoveredDevice> = throw UnsupportedOperationException("not exercised here")

    override fun connect(address: String): Flow<GattEvent> =
        flow {
            try {
                emitAll(eventsFor(address))
            } finally {
                closed += address
            }
        }

    suspend fun emit(
        address: String,
        event: GattEvent,
    ) {
        eventsFor(address).emit(event)
    }

    fun connectionClosedFor(address: String): Boolean = address in closed

    private fun eventsFor(address: String) = events.getOrPut(address) { MutableSharedFlow(extraBufferCapacity = 8) }
}
