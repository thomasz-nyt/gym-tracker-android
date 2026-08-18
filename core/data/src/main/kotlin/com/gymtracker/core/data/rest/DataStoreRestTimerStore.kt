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

        override val restTotal: Flow<Duration?> =
            preferences.data.map { it[REST_TOTAL_SECONDS]?.let(Duration::ofSeconds) }

        override val defaultRest: Flow<Duration> =
            preferences.data.map { Duration.ofSeconds(it[DEFAULT_REST_SECONDS] ?: DEFAULT_REST_SECONDS_VALUE) }

        override val shouldAskForNotificationPermission: Flow<Boolean> =
            preferences.data.map { it[PERMISSION_ASKED] != true }

        override suspend fun setRestEndsAt(instant: Instant?) {
            preferences.edit { current ->
                if (instant == null) {
                    // Clearing the end time always clears the total with it — the two describe
                    // the same rest and must never fall out of sync (e.g. a stale total left
                    // over from a rest that no longer exists).
                    current.remove(REST_ENDS_AT)
                    current.remove(REST_TOTAL_SECONDS)
                } else {
                    current[REST_ENDS_AT] = instant.toEpochMilli()
                }
            }
        }

        override suspend fun setRest(
            endsAt: Instant,
            total: Duration,
        ) {
            // One edit block, so a reader never observes an end time with last rest's total
            // (or vice versa) between the two writes.
            preferences.edit { current ->
                current[REST_ENDS_AT] = endsAt.toEpochMilli()
                current[REST_TOTAL_SECONDS] = total.seconds
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
            val REST_TOTAL_SECONDS = longPreferencesKey("rest_total_seconds")
            val DEFAULT_REST_SECONDS = longPreferencesKey("default_rest_seconds")
            val PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")

            /** US-05: "60 seconds until changed in settings". */
            const val DEFAULT_REST_SECONDS_VALUE = 60L
        }
    }
