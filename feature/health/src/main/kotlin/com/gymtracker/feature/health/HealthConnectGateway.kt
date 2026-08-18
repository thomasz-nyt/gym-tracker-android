package com.gymtracker.feature.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
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
}

internal class AndroidHealthConnectGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : HealthConnectGateway {
        override fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

        override suspend fun grantedPermissions(): Set<String> =
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    }
