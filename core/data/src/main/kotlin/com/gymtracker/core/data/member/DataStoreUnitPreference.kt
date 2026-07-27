package com.gymtracker.core.data.member

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [UnitPreference] over DataStore (ADR-0005, ADR-0008). */
class DataStoreUnitPreference
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : UnitPreference {
        override fun observe(): Flow<WeightUnit> = preferences.data.map { it.unit() }

        override suspend fun current(): WeightUnit = preferences.data.first().unit()

        override suspend fun set(unit: WeightUnit) {
            preferences.edit { it[UNIT] = unit.name }
        }

        /** An unreadable value falls back to the default rather than crashing the app. */
        private fun Preferences.unit(): WeightUnit =
            this[UNIT]?.let { stored -> WeightUnit.entries.firstOrNull { it.name == stored } } ?: DEFAULT

        private companion object {
            val UNIT = stringPreferencesKey("weight_unit")

            /** Pounds: the household is in the US (ADR-0008). */
            val DEFAULT = WeightUnit.LB
        }
    }
