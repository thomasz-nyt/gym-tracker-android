package com.gymtracker.core.data.member

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.gymtracker.core.domain.member.KeepScreenOnPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [KeepScreenOnPreference] over DataStore (ADR-0005, US-59). */
class DataStoreKeepScreenOnPreference
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : KeepScreenOnPreference {
        override fun observe(): Flow<Boolean> = preferences.data.map { it.enabled() }

        override suspend fun current(): Boolean = preferences.data.first().enabled()

        override suspend fun set(enabled: Boolean) {
            preferences.edit { it[ENABLED] = enabled }
        }

        /**
         * Unset or unreadable falls back to **on** — the opposite default from
         * [com.gymtracker.core.data.health.DataStoreHealthIntegration], deliberately: an
         * integration is opt-in, a screen that stays readable mid-rest is the default a gym
         * floor wants (US-59).
         */
        private fun Preferences.enabled(): Boolean = this[ENABLED] ?: DEFAULT

        private companion object {
            val ENABLED = booleanPreferencesKey("keep_screen_on_during_workout")
            const val DEFAULT = true
        }
    }
