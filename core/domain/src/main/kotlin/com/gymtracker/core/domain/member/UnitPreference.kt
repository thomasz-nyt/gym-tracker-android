package com.gymtracker.core.domain.member

import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.Flow

/**
 * The unit this member types weights in and sees first (ADR-0008).
 *
 * Device-local at M1 and stored in DataStore (ADR-0005); at M2 it moves to
 * `profiles.unit_preference` with the rest of their identity.
 */
interface UnitPreference {
    /** Emits again whenever the member changes it. */
    fun observe(): Flow<WeightUnit>

    /** The current unit. Defaults to [WeightUnit.LB] — the household is in the US. */
    suspend fun current(): WeightUnit

    suspend fun set(unit: WeightUnit)
}
