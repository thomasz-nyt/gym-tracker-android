package com.gymtracker.core.data.guided

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gymtracker.core.domain.guided.GuidedPlan
import com.gymtracker.core.domain.guided.GuidedPlanStore
import com.gymtracker.core.domain.model.SessionExerciseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * [GuidedPlanStore] over DataStore (ADR-0005, ADR-0016).
 *
 * Stored field by field rather than as serialised JSON: there are five of them, they are all
 * primitives, and a schema this small does not need a format that can go stale.
 *
 * The weight is absent rather than zero when the movement is bodyweight, so the key is removed
 * rather than written as `0.0` (constitution §2.4).
 */
class DataStoreGuidedPlanStore
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : GuidedPlanStore {
        override val plan: Flow<GuidedPlan?> =
            preferences.data.map { current ->
                val id = current[SESSION_EXERCISE_ID] ?: return@map null
                val startedAt = current[STARTED_AT] ?: return@map null

                GuidedPlan(
                    sessionExerciseId = SessionExerciseId(id),
                    targetSets = current[TARGET_SETS] ?: 1,
                    targetReps = current[TARGET_REPS] ?: 1,
                    weightKg = current[WEIGHT_KG],
                    setsAtStart = current[SETS_AT_START] ?: 0,
                    startedAt = Instant.ofEpochMilli(startedAt),
                )
            }

        override suspend fun setPlan(plan: GuidedPlan?) {
            preferences.edit { current ->
                if (plan == null) {
                    // One call per key rather than a list: the keys have different value types,
                    // and `remove` is generic in that type, so a heterogeneous list has no
                    // single `Preferences.Key<T>` to satisfy it.
                    current.remove(SESSION_EXERCISE_ID)
                    current.remove(TARGET_SETS)
                    current.remove(TARGET_REPS)
                    current.remove(WEIGHT_KG)
                    current.remove(SETS_AT_START)
                    current.remove(STARTED_AT)
                    return@edit
                }

                current[SESSION_EXERCISE_ID] = plan.sessionExerciseId.value
                current[TARGET_SETS] = plan.targetSets
                current[TARGET_REPS] = plan.targetReps
                current[SETS_AT_START] = plan.setsAtStart
                current[STARTED_AT] = plan.startedAt.toEpochMilli()
                plan.weightKg?.let { current[WEIGHT_KG] = it } ?: current.remove(WEIGHT_KG)
            }
        }

        private companion object {
            val SESSION_EXERCISE_ID = stringPreferencesKey("guided_session_exercise_id")
            val TARGET_SETS = intPreferencesKey("guided_target_sets")
            val TARGET_REPS = intPreferencesKey("guided_target_reps")
            val WEIGHT_KG = doublePreferencesKey("guided_weight_kg")
            val SETS_AT_START = intPreferencesKey("guided_sets_at_start")
            val STARTED_AT = longPreferencesKey("guided_started_at")
        }
    }
