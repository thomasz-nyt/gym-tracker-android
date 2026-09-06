package com.gymtracker.core.domain.member

import kotlinx.coroutines.flow.Flow

/**
 * Whether the screen stays on while a workout is running (US-59).
 *
 * Device-local and stored in DataStore (ADR-0005): it describes this phone — how its screen
 * behaves on a gym floor — not the member, so it is neither synced nor backed up, the same class
 * of preference as [com.gymtracker.core.domain.health.HeartRateBandPreference]'s chosen device.
 * Defaults to on: the rest countdown is the most-glanced element in the app (ADR-0023), and a
 * phone that locks itself halfway through a rest costs an unlock per set.
 */
interface KeepScreenOnPreference {
    /** Emits again whenever the member changes it. */
    fun observe(): Flow<Boolean>

    /** The current setting; true until the member turns it off. */
    suspend fun current(): Boolean

    /** Turns the hold on or off for every workout from now on. */
    suspend fun set(enabled: Boolean)
}
