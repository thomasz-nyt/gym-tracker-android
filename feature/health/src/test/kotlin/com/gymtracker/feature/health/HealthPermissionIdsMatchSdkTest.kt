package com.gymtracker.feature.health

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import com.gymtracker.core.domain.health.HealthPermission
import org.junit.Test
import kotlin.test.assertEquals
import androidx.health.connect.client.permission.HealthPermission as SdkHealthPermission

/**
 * [com.gymtracker.core.domain.health.HealthPermission.id] is a plain string constant, not a
 * call into the real SDK (`:core:domain` stays pure Kotlin, constitution §7) — this is the one
 * place that checks the two have not drifted apart, the only module that may import both the
 * domain type and the real `androidx.health.connect.client.permission.HealthPermission`.
 */
class HealthPermissionIdsMatchSdkTest {
    @Test
    fun `heart rate id matches the SDK's own computed value`() {
        assertEquals(SdkHealthPermission.getReadPermission(HeartRateRecord::class), HealthPermission.HEART_RATE.id)
    }

    @Test
    fun `active calories id matches the SDK's own computed value`() {
        assertEquals(
            SdkHealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.ACTIVE_CALORIES.id,
        )
    }

    @Test
    fun `exercise session id matches the SDK's own computed value`() {
        assertEquals(
            SdkHealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.EXERCISE.id,
        )
    }
}
