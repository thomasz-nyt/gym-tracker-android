package com.gymtracker.feature.logging

import com.gymtracker.core.domain.guided.GuidedPlan
import com.gymtracker.core.domain.guided.GuidedPlanStore
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.SetInput
import com.gymtracker.core.domain.set.SetPrefill
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

/**
 * The dialog that starts a guided exercise (US-05a).
 *
 * Its own form rather than a mode on [SetEntry]. Keeping them separate is what guarantees the
 * two-tap path of US-03 is untouched — "Add set" and "Save set" cannot acquire a branch they
 * did not have (constitution §2.1, ADR-0013).
 */
data class GuidedSetup(
    val row: SessionExerciseRow,
    val exerciseName: String,
    val weight: String,
    val reps: String,
    val sets: String,
)

/**
 * An exercise being walked through, as the screen renders it.
 *
 * @property setsDone how many of [targetSets] are logged. Derived from the rows in the
 *   database, never counted in memory, so a kill mid-exercise resumes where it left off.
 * @property reps the count for the set about to be finished, prefilled with [targetReps] and
 *   editable. Writing the target when fewer were managed would fabricate a value
 *   (constitution §2.4).
 * @property isComplete true once [targetSets] are done, at which point the screen shows the
 *   summary instead of the next set.
 * @property nextUp the next exercise in the session with nothing logged against it, offered
 *   after the summary. Derived from `position` order — nothing records a queue.
 */
data class GuidedRunning(
    val row: SessionExerciseRow,
    val exerciseName: String,
    val weightKg: Double?,
    val targetSets: Int,
    val targetReps: Int,
    val setsDone: Int,
    val reps: String,
    val isComplete: Boolean,
    val volumeKg: Double?,
    val elapsed: Duration,
    val nextUp: SessionExerciseRow?,
)

/** Guided mode's slice of [SessionUiState]. Both null means the session screen is showing. */
data class GuidedState(
    val setup: GuidedSetup? = null,
    val running: GuidedRunning? = null,
)

/**
 * Walking through one exercise, set by set (US-05a, ADR-0013).
 *
 * Opt-in and additive: this is a lens over the same rows the session screen shows, never a
 * separate place the data lives. Backing out leaves every set logged so far exactly where it
 * was, and each "Finish set" writes through the same [LogSets] the manual path uses.
 *
 * The one thing it does better than the manual path: ADR-0009 writes N sets sharing a single
 * `performed_at` — "the time they were recorded, not a guess at when each was performed".
 * Here each set is logged as it finishes, so each carries a real one.
 */
class GuidedController(
    private val logSets: LogSets,
    private val unitPreference: UnitPreference,
    private val planStore: GuidedPlanStore,
    private val exercises: Flow<List<SessionExerciseRow>>,
    /** Runs once the set is on disk — US-05's rest starts from here, exactly as it does manually. */
    private val onSetLogged: suspend () -> Unit,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val setup = MutableStateFlow<GuidedSetup?>(null)
    private val typedReps = MutableStateFlow<String?>(null)

    val state: Flow<GuidedState> =
        combine(setup, planStore.plan, exercises, typedReps) { pending, plan, rows, typed ->
            GuidedState(setup = pending, running = plan?.let { running(it, rows, typed) })
        }

    /**
     * Opens the start dialog with the same numbers US-03's set entry would show.
     *
     * The prefill is looked up by the caller rather than here, so this class does not need the
     * member and the catalog as well — and so the prefilling rule lives in one place.
     */
    fun start(
        row: SessionExerciseRow,
        prefill: SetPrefill?,
    ) {
        setup.value =
            GuidedSetup(
                row = row,
                exerciseName = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
                weight = prefill?.weight?.let(::trimNumber).orEmpty(),
                reps = prefill?.reps?.toString().orEmpty(),
                sets = DEFAULT_TARGET_SETS,
            )
    }

    /** One handler for the start dialog; pass only the field that changed. */
    fun changeSetup(
        weight: String? = null,
        reps: String? = null,
        sets: String? = null,
    ) {
        setup.value =
            setup.value?.let { current ->
                current.copy(
                    weight = weight ?: current.weight,
                    reps = reps ?: current.reps,
                    sets = sets ?: current.sets,
                )
            }
    }

    fun dismissSetup() {
        setup.value = null
    }

    /** Begins the exercise. Nothing is written yet — the first set is written when it is done. */
    fun begin() {
        val pending = setup.value ?: return
        val targetReps = pending.reps.toIntOrNull()?.takeIf { it >= 1 } ?: return
        val targetSets = pending.sets.toIntOrNull()?.takeIf { it >= 1 } ?: return

        scope.launch {
            val unit = unitPreference.current()
            val typedWeight = pending.weight.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

            planStore.setPlan(
                GuidedPlan(
                    sessionExerciseId = pending.row.sessionExercise.id,
                    targetSets = targetSets,
                    targetReps = targetReps,
                    weightKg = typedWeight?.let { UnitConverter.toKilograms(it, unit) },
                    // Counted from the database, so an exercise part-logged by hand does not
                    // read as already finished.
                    setsAtStart = pending.row.sets.size,
                    startedAt = clock.instant(),
                ),
            )
            setup.value = null
            typedReps.value = null
        }
    }

    /** Corrects the rep count for the set about to be finished. */
    fun changeReps(reps: String) {
        typedReps.value = reps
    }

    /**
     * Writes the set that was just performed and starts the rest (US-05).
     *
     * One set, one `performed_at`. The rest starts only after the write returns, so a save
     * that failed cannot leave a timer counting down for a set that does not exist.
     */
    fun finishSet() {
        scope.launch {
            val plan = planStore.plan.first() ?: return@launch
            val typed = typedReps.value ?: plan.targetReps.toString()
            val reps = typed.toIntOrNull()?.takeIf { it >= 1 } ?: return@launch

            logSets(
                sessionExerciseId = plan.sessionExerciseId,
                input =
                    SetInput(
                        // Already canonical kilograms; the plan stored it converted, so it is
                        // handed back in KG rather than round-tripped through the member's unit.
                        weight = plan.weightKg,
                        unit = WeightUnit.KG,
                        reps = reps,
                        rpe = null,
                    ),
                sets = 1,
            )
            typedReps.value = null
            onSetLogged()
        }
    }

    /** Leaves guided mode. Every set logged so far stays exactly where it is. */
    fun stop() {
        scope.launch {
            planStore.setPlan(null)
            typedReps.value = null
        }
    }

    /**
     * Ends this exercise and opens the start dialog for the next one.
     *
     * The plan is cleared first, so a kill between the two lands on the session screen with
     * everything logged rather than on a flow for an exercise that was never begun.
     */
    fun startNext(
        row: SessionExerciseRow,
        prefill: SetPrefill?,
    ) {
        scope.launch {
            planStore.setPlan(null)
            typedReps.value = null
            start(row, prefill)
        }
    }

    private fun running(
        plan: GuidedPlan,
        rows: List<SessionExerciseRow>,
        typed: String?,
    ): GuidedRunning? {
        val row = rows.firstOrNull { it.sessionExercise.id == plan.sessionExerciseId } ?: return null
        val done = (row.sets.size - plan.setsAtStart).coerceAtLeast(0)
        val logged = row.sets.takeLast(done)
        val weighted = logged.mapNotNull { set -> set.weightKg?.let { it * set.reps } }

        return GuidedRunning(
            row = row,
            exerciseName = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
            weightKg = plan.weightKg,
            targetSets = plan.targetSets,
            targetReps = plan.targetReps,
            setsDone = done,
            reps = typed ?: plan.targetReps.toString(),
            isComplete = done >= plan.targetSets,
            volumeKg = if (weighted.isEmpty()) null else weighted.sum(),
            elapsed = Duration.between(plan.startedAt, clock.instant()),
            nextUp = rows.firstOrNull { it.sessionExercise.id != row.sessionExercise.id && it.sets.isEmpty() },
        )
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private companion object {
        /**
         * One, matching set entry's default (ADR-0009). Guided mode is worth using for a
         * single set too — the rest still starts and the summary still lands.
         */
        const val DEFAULT_TARGET_SETS = "1"
    }
}
