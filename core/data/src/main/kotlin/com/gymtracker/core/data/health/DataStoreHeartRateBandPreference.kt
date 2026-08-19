package com.gymtracker.core.data.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gymtracker.core.domain.health.HeartRateBandPreference
import com.gymtracker.core.domain.health.HeartRateBandSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [HeartRateBandPreference] over DataStore (ADR-0005, ADR-0039). */
class DataStoreHeartRateBandPreference
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : HeartRateBandPreference {
        override fun observe(): Flow<HeartRateBandSelection> = preferences.data.map { it.toSelection() }

        override suspend fun current(): HeartRateBandSelection = preferences.data.first().toSelection()

        override suspend fun setEnabled(enabled: Boolean) {
            preferences.edit { it[ENABLED] = enabled }
        }

        override suspend fun setDevice(address: String?) {
            preferences.edit {
                if (address == null) it.remove(DEVICE_ADDRESS) else it[DEVICE_ADDRESS] = address
            }
        }

        /** Unset or unreadable falls back to off, no device (US-46), same guard [DataStoreHealthIntegration] uses. */
        private fun Preferences.toSelection() =
            HeartRateBandSelection(
                enabled = this[ENABLED] ?: DEFAULT_ENABLED,
                deviceAddress = this[DEVICE_ADDRESS],
            )

        private companion object {
            val ENABLED = booleanPreferencesKey("heart_rate_band_enabled")
            val DEVICE_ADDRESS = stringPreferencesKey("heart_rate_band_device_address")

            /** Off for every member, including the maintainer (US-46). */
            const val DEFAULT_ENABLED = false
        }
    }
