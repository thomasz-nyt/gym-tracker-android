package com.gymtracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.health.DiscoveredHeartRateBand
import com.gymtracker.core.domain.health.HeartRateBandAvailability
import com.gymtracker.core.domain.health.HeartRateBandPermission
import com.gymtracker.core.domain.health.HeartRateBandPreference
import com.gymtracker.core.domain.health.HeartRateBandScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Settings pairing section renders (US-46, ADR-0039) — separate from [SettingsViewModel] and its state. */
data class HeartRateBandUiState(
    /**
     * The device/OS gate, independent of [enabled] (mirrors [com.gymtracker.core.domain.health.HealthStatus]'s
     * split from the toggle, for the same reason). The pairing section renders nothing at all
     * while this is [HeartRateBandAvailability.Unavailable].
     */
    val availability: HeartRateBandAvailability = HeartRateBandAvailability.Unavailable,
    /** The member's own opt-in (US-46). Defaults off. */
    val enabled: Boolean = false,
    val pairedDeviceAddress: String? = null,
    val isScanning: Boolean = false,
    val discovered: List<DiscoveredHeartRateBand> = emptyList(),
    /**
     * The scan could not be started — distinct from a scan that ran and found nothing, which is
     * [isScanning] with an empty [discovered]. Without this the two look identical on screen
     * (US-48's honesty rule applied to pairing).
     */
    val scanFailed: Boolean = false,
    /** The permission currently awaiting its on-screen reason, or `null` when no walk is in progress. */
    val pendingPermission: HeartRateBandPermission? = null,
)

/**
 * US-46: the toggle, the two-permission walk (`BLUETOOTH_SCAN` then `BLUETOOTH_CONNECT`,
 * mirroring [SettingsViewModel]'s Health Connect walk), scanning, and choosing a device.
 * Kept out of [SettingsViewModel] itself — that class is already at detekt's `TooManyFunctions`
 * suppression limit, and pairing is a large enough new concern to deserve its own state holder
 * rather than push it further past that line.
 */
@HiltViewModel
class HeartRateBandViewModel
    @Inject
    constructor(
        private val preference: HeartRateBandPreference,
        private val scanner: HeartRateBandScanner,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HeartRateBandUiState())
        val uiState: StateFlow<HeartRateBandUiState> = _uiState.asStateFlow()

        private var scanJob: Job? = null

        init {
            viewModelScope.launch {
                preference.observe().collect { selection ->
                    _uiState.update {
                        it.copy(
                            availability = scanner.availability(),
                            enabled = selection.enabled,
                            pairedDeviceAddress = selection.deviceAddress,
                        )
                    }
                }
            }
        }

        /**
         * Stores the choice, then either starts the permission walk at the first permission
         * (turning on) or tears down any walk/scan in progress (turning off, US-49) — the
         * paired device itself is untouched, the same convention [HeartRateBandPreference]'s
         * class doc explains.
         */
        fun onToggled(enabled: Boolean) {
            viewModelScope.launch {
                preference.setEnabled(enabled)
                if (enabled) {
                    _uiState.update { it.copy(pendingPermission = HeartRateBandPermission.entries.first()) }
                } else {
                    stopScanning()
                    _uiState.update { it.copy(pendingPermission = null, discovered = emptyList()) }
                }
            }
        }

        /**
         * Called once the system permission request for [permission] has returned, granted or
         * denied — the walk advances either way, the same "any denial is final for that run"
         * convention `health-connect.md` sets for Health Connect. Once the walk ends with both
         * permissions resolved, scanning starts automatically if the device turned out Ready.
         */
        fun onPermissionResult(permission: HeartRateBandPermission) {
            val next = HeartRateBandPermission.entries.getOrNull(permission.ordinal + 1)
            _uiState.update { it.copy(pendingPermission = next, availability = scanner.availability()) }
            if (next == null && _uiState.value.availability == HeartRateBandAvailability.Ready) {
                startScanning()
            }
        }

        /** The member picked a device from the scan results; pairing stops the scan (one device at a time). */
        fun onDeviceChosen(address: String) {
            viewModelScope.launch {
                preference.setDevice(address)
                stopScanning()
            }
        }

        private fun startScanning() {
            scanJob?.cancel()
            _uiState.update { it.copy(isScanning = true, scanFailed = false, discovered = emptyList()) }
            scanJob =
                viewModelScope.launch {
                    scanner
                        .scan()
                        .catch { _uiState.update { state -> state.copy(isScanning = false, scanFailed = true) } }
                        .collect { device ->
                            _uiState.update { state ->
                                if (state.discovered.any { it.address == device.address }) {
                                    state
                                } else {
                                    state.copy(discovered = state.discovered + device)
                                }
                            }
                        }
                }
        }

        private fun stopScanning() {
            scanJob?.cancel()
            scanJob = null
            _uiState.update { it.copy(isScanning = false) }
        }
    }
