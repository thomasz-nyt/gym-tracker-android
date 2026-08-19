package com.gymtracker.feature.health

import com.gymtracker.core.domain.health.HeartRateBandPreference
import com.gymtracker.core.domain.health.HeartRateBandSelection
import com.gymtracker.core.domain.health.HeartRateMeasurement
import com.gymtracker.core.domain.health.LiveHeartRate
import com.gymtracker.core.domain.health.LiveHeartRateSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The real [LiveHeartRateSource] (US-46 … US-48, ADR-0039). Bound only when `:app`'s
 * optional-feature flag enables the health module — the default binding stays
 * [com.gymtracker.core.domain.health.NoOpLiveHeartRateSource].
 *
 * Re-derives the whole connection from [HeartRateBandPreference.observe] on every emission
 * ([kotlinx.coroutines.flow.flatMapLatest]), so turning the toggle off or changing the chosen
 * device tears down any open connection immediately (US-49) rather than leaving it running
 * against a stale preference.
 */
class BleHeartRateSource
    @Inject
    internal constructor(
        private val gateway: HeartRateBandGateway,
        private val preference: HeartRateBandPreference,
    ) : LiveHeartRateSource {
        override fun observe(): Flow<LiveHeartRate> =
            preference.observe().distinctUntilChanged().flatMapLatest { selection -> connectionFor(selection) }

        private fun connectionFor(selection: HeartRateBandSelection): Flow<LiveHeartRate> {
            val address = selection.deviceAddress
            if (address == null || !canConnect(selection)) return flowOf(LiveHeartRate.Unavailable)

            return callbackFlow {
                send(LiveHeartRate.Searching)
                var staleWatchdog: Job? = null

                val collection =
                    launch {
                        gateway.connect(address).collect { event ->
                            when (event) {
                                is GattEvent.Connected -> send(LiveHeartRate.Searching)

                                is GattEvent.Disconnected -> {
                                    staleWatchdog?.cancel()
                                    send(LiveHeartRate.Lost)
                                }

                                is GattEvent.MeasurementReceived -> {
                                    val measurement = HeartRateMeasurement.parse(event.payload) ?: return@collect
                                    send(
                                        LiveHeartRate.Beating(
                                            bpm = measurement.bpm,
                                            energyExpendedKilocalories = measurement.energyExpendedKilocalories,
                                        ),
                                    )
                                    staleWatchdog?.cancel()
                                    staleWatchdog =
                                        launch {
                                            delay(STALE_TIMEOUT_MILLIS)
                                            send(LiveHeartRate.Lost)
                                        }
                                }
                            }
                        }
                    }

                awaitClose {
                    staleWatchdog?.cancel()
                    collection.cancel()
                }
            }
        }

        private fun canConnect(selection: HeartRateBandSelection): Boolean =
            selection.enabled && gateway.isSupported() && gateway.hasConnectPermission()

        companion object {
            /**
             * A conformant band notifies roughly once per second; ten missed notifications in a
             * row is treated as the connection having gone silent, not merely a slow beat
             * (US-48 — a stale reading is never shown as current, constitution §2.4).
             */
            const val STALE_TIMEOUT_MILLIS = 10_000L
        }
    }
