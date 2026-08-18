package com.gymtracker.core.domain.health

import kotlinx.coroutines.flow.Flow

/**
 * The member's own opt-in for Health Connect (US-21), device-local per ADR-0005 and ADR-0038 —
 * not part of the US-40/41 backup envelope, so restoring someone else's export never turns
 * reads on for a device that has never granted a permission.
 *
 * Modeled on [com.gymtracker.core.domain.member.UnitPreference]: an observed value, a
 * suspending read, and a setter.
 */
interface HealthIntegration {
    /** Emits again whenever the member changes it. */
    fun observe(): Flow<Boolean>

    /** The current setting. Defaults to `false` for every member (US-21). */
    suspend fun current(): Boolean

    suspend fun set(enabled: Boolean)
}
