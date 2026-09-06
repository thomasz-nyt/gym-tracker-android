package com.gymtracker.core.domain.guided

import com.gymtracker.core.domain.model.SessionExerciseId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The exercise being walked through right now (US-05a, ADR-0017).
 *
 * Deliberately *not* a domain entity. It is the sets-by-reps the member typed when they
 * started, kept for the length of the exercise and discarded when it ends — no prescription
 * table, no template, nothing that outlives the workout. ADR-0009 rejected the entity version
 * of this and ADR-0017 explains why the difference matters.
 *
 * @property targetSets and [targetReps] are a prefill for each set, never a promise about what
 *   was performed. The rep count is editable before every set, because writing 12 when 9 were
 *   managed would fabricate a logged value (constitution §2.4).
 * @property weightKg the *first* set's prefill only, and editable before every set for the same
 *   reason (US-05a, amended 2026-09-05). From set two on, the set just written is the prefill —
 *   read off the rows, not stored here — so this field never changes once the flow has begun.
 * @property setsAtStart how many sets this exercise already had when the flow began, so
 *   progress is a subtraction rather than a guess. An exercise half-logged by hand and then
 *   started guided does not read as already finished.
 */
data class GuidedPlan(
    val sessionExerciseId: SessionExerciseId,
    val targetSets: Int,
    val targetReps: Int,
    val weightKg: Double?,
    val setsAtStart: Int,
    val startedAt: Instant,
)

/**
 * Where the in-flight plan lives.
 *
 * DataStore, not Room, per ADR-0005's boundary: it describes this device and this install
 * only and will never sync. Storing it at all is what makes a locked phone during a three
 * minute rest survivable — the same reason the rest timer stores an end time (ADR-0010).
 */
interface GuidedPlanStore {
    val plan: Flow<GuidedPlan?>

    /** Pass null to end the flow. */
    suspend fun setPlan(plan: GuidedPlan?)
}
