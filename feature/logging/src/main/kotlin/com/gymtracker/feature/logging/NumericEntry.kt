package com.gymtracker.feature.logging

/** Blank load means bodyweight; other loads must be finite and nonnegative (US-62). */
internal fun String.isValidLoad(): Boolean =
    isBlank() || trim().toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true

/** RPE is optional, finite and restricted to the recorded half-step scale. */
internal fun String.isValidEffort(): Boolean =
    isBlank() ||
        trim().toDoubleOrNull()?.let { it.isFinite() && it in MIN_EFFORT..MAX_EFFORT && (it * 2) % 1.0 == 0.0 } == true

private const val MIN_EFFORT = 5.0
private const val MAX_EFFORT = 10.0
