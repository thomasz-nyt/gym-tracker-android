package com.gymtracker.core.domain.health

/**
 * The two runtime permissions live heart rate needs (US-46, ADR-0039), requested one at a time
 * in this order, each with its own plain-language reason shown first — the same pattern
 * [HealthPermission] uses for Health Connect's three. Written as plain constants rather than
 * `android.Manifest.permission.*` so `:core:domain` stays pure Kotlin (constitution §7).
 */
enum class HeartRateBandPermission(
    val id: String,
    val reason: String,
) {
    SCAN(
        id = "android.permission.BLUETOOTH_SCAN",
        reason = "Looks for your heart rate band nearby, so it can be paired.",
    ),
    CONNECT(
        id = "android.permission.BLUETOOTH_CONNECT",
        reason = "Connects to your paired band to read its live heart rate.",
    ),
}
