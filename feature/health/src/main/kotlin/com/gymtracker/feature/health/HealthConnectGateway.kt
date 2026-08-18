package com.gymtracker.feature.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * The one seam between [HealthConnectMetricsSource] and the real `androidx.health.connect` SDK.
 * Everything the SDK needs a real `Context` or a real install to answer lives behind this
 * interface, so [HealthConnectMetricsSource]'s branching logic is testable with a fake and
 * needs neither Robolectric nor a device.
 */
internal interface HealthConnectGateway {
    /** One of `HealthConnectClient.SDK_*`. */
    fun sdkStatus(): Int

    /** The permission strings currently granted, re-read every call — never cached. */
    suspend fun grantedPermissions(): Set<String>

    /** Every heart-rate sample (beats per minute) recorded in [window]. */
    suspend fun heartRateBpm(window: ClosedRange<Instant>): List<Long>

    /** Every active-calorie record's energy (kcal) recorded in [window]. */
    suspend fun activeCaloriesKcal(window: ClosedRange<Instant>): List<Double>

    /**
     * The longest exercise session recorded within [window], or `null` if there is none —
     * used only to narrow the window the other two reads run over (`health-connect.md`), never
     * stored itself.
     */
    suspend fun exerciseSessionWindow(window: ClosedRange<Instant>): ClosedRange<Instant>?
}

internal class AndroidHealthConnectGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : HealthConnectGateway {
        override fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

        override suspend fun grantedPermissions(): Set<String> = client().permissionController.getGrantedPermissions()

        override suspend fun heartRateBpm(window: ClosedRange<Instant>): List<Long> =
            client()
                .readRecords(ReadRecordsRequest(HeartRateRecord::class, window.toTimeRangeFilter()))
                .records
                .flatMap { it.samples }
                .map { it.beatsPerMinute }

        override suspend fun activeCaloriesKcal(window: ClosedRange<Instant>): List<Double> =
            client()
                .readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, window.toTimeRangeFilter()))
                .records
                // Energy's JVM getter is `getKilocalories()`, renamed via @JvmName from
                // Kotlin's own property, `inKilocalories` — confirmed against the compiled
                // `connect-client-1.1.0.aar`'s embedded Kotlin metadata.
                .map { it.energy.inKilocalories }

        override suspend fun exerciseSessionWindow(window: ClosedRange<Instant>): ClosedRange<Instant>? =
            client()
                .readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, window.toTimeRangeFilter()))
                .records
                .maxByOrNull { Duration.between(it.startTime, it.endTime) }
                ?.let { it.startTime..it.endTime }

        private fun client() = HealthConnectClient.getOrCreate(context)

        private fun ClosedRange<Instant>.toTimeRangeFilter() = TimeRangeFilter.between(start, endInclusive)
    }
