package com.gymtracker.core.data.warmup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.gymtracker.core.domain.warmup.WarmUpTimerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** [WarmUpTimerStore] over DataStore (ADR-0005, ADR-0021). */
class DataStoreWarmUpTimerStore
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : WarmUpTimerStore {
        override val warmUpStartedAt: Flow<Instant?> =
            preferences.data.map { it[WARM_UP_STARTED_AT]?.let(Instant::ofEpochMilli) }

        override suspend fun setWarmUpStartedAt(instant: Instant?) {
            preferences.edit { current ->
                if (instant == null) {
                    current.remove(WARM_UP_STARTED_AT)
                } else {
                    current[WARM_UP_STARTED_AT] = instant.toEpochMilli()
                }
            }
        }

        private companion object {
            /** Its own key, beside the rest timer's rather than sharing it (ADR-0021). */
            val WARM_UP_STARTED_AT = longPreferencesKey("warm_up_started_at")
        }
    }
