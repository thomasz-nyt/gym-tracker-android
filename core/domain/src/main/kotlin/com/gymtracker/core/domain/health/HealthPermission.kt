package com.gymtracker.core.domain.health

/**
 * The three read-only permissions `specs/health-connect.md` §Permissions names, requested one
 * at a time in this order, each with its own plain-language reason shown first — never a
 * write permission, since the app has none and creates no records
 * (`health-connect.md` §"We never write").
 *
 * [id] is the literal Health Connect permission string. Written as a plain constant rather
 * than computed via `androidx.health.connect.client.permission.HealthPermission.
 * getReadPermission(...)` so `:core:domain` stays pure Kotlin (constitution §7) — a value the
 * UI only ever passes back to the OS permission-request contract, never interprets. Confirmed
 * against the compiled `connect-client-1.1.0.aar`'s `HealthPermission` class, the same kind of
 * check ADR-0028 used for `androidx.room.Query`'s retention when a reflection-based test turned
 * out to be unworkable. [com.gymtracker.feature.health]'s own permission use (granted-permission
 * checks) never needs to name these strings back, so there is nothing here to drift against.
 */
enum class HealthPermission(
    val id: String,
    val reason: String,
) {
    HEART_RATE(
        id = "android.permission.health.READ_HEART_RATE",
        reason = "Reads your heart rate during a workout, to show its average and peak.",
    ),
    ACTIVE_CALORIES(
        id = "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
        reason = "Reads active calories burned during a workout.",
    ),
    EXERCISE(
        id = "android.permission.health.READ_EXERCISE",
        reason =
            "Reads the exercise session itself, only to narrow the time window read above " +
                "— never stored.",
    ),
}
