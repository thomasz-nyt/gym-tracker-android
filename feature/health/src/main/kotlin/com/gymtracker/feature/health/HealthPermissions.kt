package com.gymtracker.feature.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord

/**
 * The exact three permissions `specs/health-connect.md` §Permissions names, requested one at a
 * time, each with its own on-screen reason first. No write permission is requested, ever — the
 * app has none and creates no records (`health-connect.md` §"We never write").
 */
object HealthPermissions {
    /** Read heart rate, for `avg_hr`/`max_hr`. */
    val HEART_RATE: String = HealthPermission.getReadPermission(HeartRateRecord::class)

    /** Read active calories burned, for `active_kcal`. */
    val ACTIVE_CALORIES: String = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)

    /** Read exercise sessions, used only to refine the read window — never stored. */
    val EXERCISE: String = HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    /** In the order requested: heart rate, then calories, then the session window refinement. */
    val ALL: List<String> = listOf(HEART_RATE, ACTIVE_CALORIES, EXERCISE)
}
