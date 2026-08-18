package com.gymtracker.core.data.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.gymtracker.core.domain.health.HealthIntegration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [HealthIntegration] over DataStore (ADR-0005, ADR-0038). */
class DataStoreHealthIntegration
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : HealthIntegration {
        override fun observe(): Flow<Boolean> = preferences.data.map { it.enabled() }

        override suspend fun current(): Boolean = preferences.data.first().enabled()

        override suspend fun set(enabled: Boolean) {
            preferences.edit { it[ENABLED] = enabled }
        }

        /**
         * Unset or unreadable falls back to off (US-21), same guard
         * [com.gymtracker.core.data.member.DataStoreUnitPreference] uses.
         */
        private fun Preferences.enabled(): Boolean = this[ENABLED] ?: DEFAULT

        private companion object {
            val ENABLED = booleanPreferencesKey("health_integration_enabled")

            /** Off for every member, including the maintainer (US-21). */
            const val DEFAULT = false
        }
    }
