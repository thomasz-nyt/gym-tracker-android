package com.gymtracker.feature.health

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import javax.inject.Inject

/**
 * The one seam between [BleHeartRateSource] and the platform `android.bluetooth` API
 * (ADR-0039). Everything that needs a real adapter, a real scan, or a real GATT connection
 * lives behind this interface, so [BleHeartRateSource]'s state machine — the part with actual
 * branching logic — is testable with a fake and needs neither Robolectric nor a device, the
 * same split `:feature:health`'s `HealthConnectGateway` already uses.
 */
internal interface HeartRateBandGateway {
    /** API 31+ with a Bluetooth adapter present and enabled (ADR-0039 restricts to API 31+). */
    fun isSupported(): Boolean

    fun hasScanPermission(): Boolean

    fun hasConnectPermission(): Boolean

    /** Nearby devices advertising the Bluetooth Heart Rate service, until the collector cancels. */
    fun scanForDevices(): Flow<DiscoveredDevice>

    /** Connects to [address] and streams its lifecycle until the collector cancels. */
    fun connect(address: String): Flow<GattEvent>
}

internal data class DiscoveredDevice(
    val address: String,
    val name: String?,
)

internal sealed interface GattEvent {
    data object Connected : GattEvent

    data object Disconnected : GattEvent

    data class MeasurementReceived(
        val payload: ByteArray,
    ) : GattEvent
}

internal class AndroidHeartRateBandGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : HeartRateBandGateway {
        override fun isSupported(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && adapter()?.isEnabled == true

        override fun hasScanPermission(): Boolean = hasPermission(Manifest.permission.BLUETOOTH_SCAN)

        override fun hasConnectPermission(): Boolean = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

        @SuppressLint("MissingPermission")
        // BLUETOOTH_SCAN — checked by the caller via hasScanPermission() before this is ever
        // invoked (BleHeartRateSource's state machine gates on it), which lint cannot see across
        // that boundary.
        override fun scanForDevices(): Flow<DiscoveredDevice> =
            callbackFlow {
                val scanner = adapter()?.bluetoothLeScanner
                if (scanner == null) {
                    close()
                    return@callbackFlow
                }

                val callback =
                    object : ScanCallback() {
                        override fun onScanResult(
                            callbackType: Int,
                            result: ScanResult,
                        ) {
                            trySend(DiscoveredDevice(result.device.address, result.device.name))
                        }
                    }

                val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID)).build()
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                scanner.startScan(listOf(filter), settings, callback)

                awaitClose { scanner.stopScan(callback) }
            }

        @SuppressLint("MissingPermission")
        // BLUETOOTH_CONNECT — checked by the caller via hasConnectPermission() before this is
        // ever invoked, for the same reason as scanForDevices() above.
        override fun connect(address: String): Flow<GattEvent> =
            callbackFlow {
                val device = adapter()?.getRemoteDevice(address)
                if (device == null) {
                    close()
                    return@callbackFlow
                }

                val callback =
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            gatt: BluetoothGatt,
                            status: Int,
                            newState: Int,
                        ) {
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                                BluetoothProfile.STATE_DISCONNECTED -> trySend(GattEvent.Disconnected)
                            }
                        }

                        override fun onServicesDiscovered(
                            gatt: BluetoothGatt,
                            status: Int,
                        ) {
                            val characteristic =
                                gatt
                                    .getService(HEART_RATE_SERVICE_UUID)
                                    ?.getCharacteristic(HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID)
                            if (characteristic == null) {
                                trySend(GattEvent.Disconnected)
                                return
                            }

                            trySend(GattEvent.Connected)
                            gatt.setCharacteristicNotification(characteristic, true)
                            characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let { descriptor ->
                                writeEnableNotification(gatt, descriptor)
                            }
                        }

                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            value: ByteArray,
                        ) {
                            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID) {
                                trySend(GattEvent.MeasurementReceived(value))
                            }
                        }
                    }

                // Explicit TRANSPORT_LE rather than the deprecated 3-arg overload, which lets
                // the platform guess the transport — auto-detection is a known source of
                // flaky connects to LE-only peripherals like a fitness band.
                val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                awaitClose { gatt.close() }
            }

        /**
         * `BluetoothGattDescriptor.setValue()` + `BluetoothGatt.writeDescriptor(descriptor)` was
         * deprecated in API 33 in favour of the value-taking overload; the old pair still works
         * on 31–32, which ADR-0039's floor still has to support.
         */
        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        // BLUETOOTH_CONNECT — checked by connect()'s only caller via hasConnectPermission()
        // before a connection is ever opened; Lint's suppression on connect() itself does not
        // reach this private method, called from a nested BluetoothGattCallback.
        private fun writeEnableNotification(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        private fun adapter(): BluetoothAdapter? =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

        private fun hasPermission(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        private companion object {
            // Bluetooth SIG-assigned 16-bit UUIDs, expanded to the standard 128-bit Base UUID —
            // Heart Rate service (0x180D), its Heart Rate Measurement characteristic (0x2A37),
            // and the Client Characteristic Configuration descriptor (0x2902) common to all
            // notify-capable characteristics.
            val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
            val HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID: UUID =
                UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
            val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        }
    }
