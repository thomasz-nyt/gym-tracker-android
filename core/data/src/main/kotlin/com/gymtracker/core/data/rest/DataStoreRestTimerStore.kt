package com.gymtracker.core.data.rest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.gymtracker.core.domain.rest.RestTimerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** [RestTimerStore] over DataStore (ADR-0005, ADR-0010). */
class DataStoreRestTimerStore
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : RestTimerStore {
        override val restEndsAt: Flow<Instant?> =
            preferences.data.map { it[REST_ENDS_AT]?.let(Instant::ofEpochMilli) }

        override val defaultRest: Flow<Duration> =
            preferences.data.map { Duration.ofSeconds(it[DEFAULT_REST_SECONDS] ?: DEFAULT_REST_SECONDS_VALUE) }

        override val shouldAskForNotificationPermission: Flow<Boolean> =
            preferences.data.map { it[PERMISSION_ASKED] != true }

        override suspend fun setRestEndsAt(instant: Instant?) {
            preferences.edit { current ->
                if (instant == null) {
                    current.remove(REST_ENDS_AT)
                } else {
                    current[REST_ENDS_AT] = instant.toEpochMilli()
                }
            }
        }

        override suspend fun setDefaultRest(rest: Duration) {
            preferences.edit { it[DEFAULT_REST_SECONDS] = rest.seconds }
        }

        override suspend fun markNotificationPermissionAsked() {
            preferences.edit { it[PERMISSION_ASKED] = true }
        }

        private companion object {
            val REST_ENDS_AT = longPreferencesKey("rest_ends_at")
            val DEFAULT_REST_SECONDS = longPreferencesKey("default_rest_seconds")
            val PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")

            /** US-05: "90 seconds until changed in settings". */
            const val DEFAULT_REST_SECONDS_VALUE = 90L
        }
    }
