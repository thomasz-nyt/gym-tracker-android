package com.gymtracker.core.domain.rest

import kotlinx.coroutines.flow.Flow

/**
 * Whether the rest cue sounds a tone as well as the haptic pulse (ADR-0049).
 *
 * The pulse is not a preference — it is the cue. The tone is the opt-in, off until the member
 * turns it on: a beep nobody asked for, every sixty seconds, in a shared gym is the kind of
 * default that gets an app uninstalled. Device-local (ADR-0005), neither synced nor backed up.
 */
interface RestCueTonePreference {
    /** Emits again whenever the member changes it. */
    fun observe(): Flow<Boolean>

    /** The current setting; false until the member turns it on. */
    suspend fun current(): Boolean

    /** Turns the tone on or off for every rest from now on. */
    suspend fun set(enabled: Boolean)
}
