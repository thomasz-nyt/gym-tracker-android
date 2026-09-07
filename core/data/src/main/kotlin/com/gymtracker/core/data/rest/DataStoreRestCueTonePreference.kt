package com.gymtracker.core.data.rest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.gymtracker.core.domain.rest.RestCueTonePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [RestCueTonePreference] over DataStore (ADR-0005, ADR-0049). */
class DataStoreRestCueTonePreference
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : RestCueTonePreference {
        override fun observe(): Flow<Boolean> = preferences.data.map { it.enabled() }

        override suspend fun current(): Boolean = preferences.data.first().enabled()

        override suspend fun set(enabled: Boolean) {
            preferences.edit { it[ENABLED] = enabled }
        }

        /** Unset or unreadable falls back to **off** — the tone is the opt-in (ADR-0049). */
        private fun Preferences.enabled(): Boolean = this[ENABLED] ?: DEFAULT

        private companion object {
            val ENABLED = booleanPreferencesKey("rest_cue_tone_enabled")
            const val DEFAULT = false
        }
    }
