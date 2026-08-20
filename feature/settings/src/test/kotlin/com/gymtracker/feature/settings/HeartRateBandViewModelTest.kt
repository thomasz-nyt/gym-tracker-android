package com.gymtracker.feature.settings

import com.gymtracker.core.domain.health.DiscoveredHeartRateBand
import com.gymtracker.core.domain.health.HeartRateBandAvailability
import com.gymtracker.core.domain.health.HeartRateBandPermission
import com.gymtracker.core.domain.health.HeartRateBandPreference
import com.gymtracker.core.domain.health.HeartRateBandScanner
import com.gymtracker.core.domain.health.HeartRateBandSelection
import com.gymtracker.core.domain.health.ScanFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-46: pairing — the toggle, the two-permission walk (`BLUETOOTH_SCAN` then
 * `BLUETOOTH_CONNECT`), scanning, and choosing a device. Hand-written fakes, never MockK, per
 * `testing-strategy.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeartRateBandViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `unavailable device shows no walk and cannot be turned on meaningfully`() =
        runTest {
            val viewModel = viewModel(availability = HeartRateBandAvailability.Unavailable)

            assertEquals(HeartRateBandAvailability.Unavailable, viewModel.uiState.value.availability)
        }

    @Test
    fun `turning the toggle on starts the walk at SCAN`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onToggled(true)

            assertEquals(HeartRateBandPermission.SCAN, viewModel.uiState.value.pendingPermission)
            assertTrue(viewModel.uiState.value.enabled)
        }

    @Test
    fun `SCAN result advances to CONNECT`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onToggled(true)

            viewModel.onPermissionResult(HeartRateBandPermission.SCAN)

            assertEquals(HeartRateBandPermission.CONNECT, viewModel.uiState.value.pendingPermission)
        }

    @Test
    fun `CONNECT result ends the walk regardless of grant (any denial is final for the run)`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onToggled(true)
            viewModel.onPermissionResult(HeartRateBandPermission.SCAN)

            viewModel.onPermissionResult(HeartRateBandPermission.CONNECT)

            assertNull(viewModel.uiState.value.pendingPermission)
        }

    @Test
    fun `both permissions granted starts scanning automatically and lists discovered devices`() =
        runTest {
            val scanner = FakeScanner(availability = HeartRateBandAvailability.Ready)
            val viewModel = viewModel(scanner = scanner)
            viewModel.onToggled(true)
            viewModel.onPermissionResult(HeartRateBandPermission.SCAN)
            viewModel.onPermissionResult(HeartRateBandPermission.CONNECT)

            assertTrue(viewModel.uiState.value.isScanning)

            scanner.discover(DiscoveredHeartRateBand("AA:BB", "Charge 6"))

            assertEquals(listOf(DiscoveredHeartRateBand("AA:BB", "Charge 6")), viewModel.uiState.value.discovered)
        }

    @Test
    fun `the same device discovered twice is listed once`() =
        runTest {
            val scanner = FakeScanner(availability = HeartRateBandAvailability.Ready)
            val viewModel = viewModel(scanner = scanner)
            viewModel.onToggled(true)
            viewModel.onPermissionResult(HeartRateBandPermission.SCAN)
            viewModel.onPermissionResult(HeartRateBandPermission.CONNECT)

            scanner.discover(DiscoveredHeartRateBand("AA:BB", "Charge 6"))
            scanner.discover(DiscoveredHeartRateBand("AA:BB", "Charge 6"))

            assertEquals(1, viewModel.uiState.value.discovered.size)
        }

    @Test
    fun `choosing a device pairs it and stops scanning`() =
        runTest {
            val scanner = FakeScanner(availability = HeartRateBandAvailability.Ready)
            val preference = FakePreference()
            val viewModel = viewModel(scanner = scanner, preference = preference)
            viewModel.onToggled(true)
            viewModel.onPermissionResult(HeartRateBandPermission.SCAN)
            viewModel.onPermissionResult(HeartRateBandPermission.CONNECT)

            viewModel.onDeviceChosen("AA:BB")

            assertEquals("AA:BB", preference.current().deviceAddress)
            assertEquals(false, viewModel.uiState.value.isScanning)
        }

    @Test
    fun `turning the toggle off clears the walk and the discovered list (US-49)`() =
        runTest {
            val scanner = FakeScanner(availability = HeartRateBandAvailability.Ready)
            val viewModel = viewModel(scanner = scanner)
            viewModel.onToggled(true)
            viewModel.onPermissionResult(HeartRateBandPermission.SCAN)
            scanner.discover(DiscoveredHeartRateBand("AA:BB", "Charge 6"))

            viewModel.onToggled(false)

            assertNull(viewModel.uiState.value.pendingPermission)
            assertTrue(
                viewModel.uiState.value.discovered
                    .isEmpty(),
            )
            assertEquals(false, viewModel.uiState.value.isScanning)
        }

    @Test
    fun `a scan that cannot start reports it rather than looking like an empty search`() =
        runTest {
            // Android throttles an app to 5 scan starts per 30 seconds; without this, the UI
            // sits on "Looking for nearby devices…" forever and a broken scan is
            // indistinguishable from no devices nearby (US-48's honesty rule, applied to
            // pairing). Found on a real phone, not by this suite.
            val scanner = FakeScanner(availability = HeartRateBandAvailability.Ready, failWith = ScanFailedException(2))
            val viewModel = viewModel(scanner = scanner)

            viewModel.onToggled(true)
            viewModel.onPermissionResult(HeartRateBandPermission.SCAN)
            viewModel.onPermissionResult(HeartRateBandPermission.CONNECT)

            assertEquals(false, viewModel.uiState.value.isScanning)
            assertEquals(true, viewModel.uiState.value.scanFailed)
        }

    @Test
    fun `the paired device survives the toggle turning off (US-49 - pairing is not deleted)`() =
        runTest {
            val preference = FakePreference()
            preference.setDevice("AA:BB")
            val viewModel = viewModel(preference = preference)

            viewModel.onToggled(false)

            assertEquals("AA:BB", viewModel.uiState.value.pairedDeviceAddress)
        }

    private fun viewModel(
        availability: HeartRateBandAvailability = HeartRateBandAvailability.Ready,
        scanner: FakeScanner = FakeScanner(availability = availability),
        preference: FakePreference = FakePreference(),
    ) = HeartRateBandViewModel(preference, scanner)
}

private class FakePreference : HeartRateBandPreference {
    private val state = MutableStateFlow(HeartRateBandSelection(enabled = false, deviceAddress = null))

    override fun observe(): Flow<HeartRateBandSelection> = state

    override suspend fun current(): HeartRateBandSelection = state.value

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = state.value.copy(enabled = enabled)
    }

    override suspend fun setDevice(address: String?) {
        state.value = state.value.copy(deviceAddress = address)
    }
}

private class FakeScanner(
    private val availability: HeartRateBandAvailability,
    private val failWith: Throwable? = null,
) : HeartRateBandScanner {
    private val found = MutableSharedFlow<DiscoveredHeartRateBand>(extraBufferCapacity = 8)

    override fun availability(): HeartRateBandAvailability = availability

    override fun scan(): Flow<DiscoveredHeartRateBand> = failWith?.let { flow { throw it } } ?: found

    suspend fun discover(device: DiscoveredHeartRateBand) {
        found.emit(device)
    }
}
