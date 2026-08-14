package com.gymtracker.feature.logging

import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.ResolveSetPrefill
import com.gymtracker.core.domain.set.SetInput
import com.gymtracker.core.domain.set.SetPrefill
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.core.domain.units.weightIncrement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/** The set-entry sheet for one exercise in the session (US-03). */
data class SetEntry(
    val sessionExerciseId: SessionExerciseId,
    val exerciseName: String,
    val weight: String,
    val reps: String,
    /** How many identical sets to record — "3 sets of 12" (ADR-0009). Defaults to the target's count, then 3. */
    val sets: String,
    /** Optional, 5.0–10.0 in half steps. Blank means not recorded, which is not the same as easy. */
    val rpe: String,
    /** True when the fields came from a previous set or a target rather than being empty (US-03). */
    val prefilled: Boolean,
    /**
     * True when [weight] and [reps] came from a real past set (US-37, ADR-0031) — the sheet
     * shows "Prefilled from {date} — {weight} × {reps}" only in this case, since a target
     * already renders labelled as a target elsewhere on the same sheet.
     */
    val fromHistory: Boolean,
    /** When [fromHistory], the date that set happened — null otherwise. */
    val lastPerformedAt: Instant? = null,
)

/**
 * Owns the set-entry form: opening it prefilled, editing it, and saving.
 *
 * Split out of `ActiveSessionViewModel` because that class had grown to cover the session,
 * the catalog search and set entry at once. This is the third of those, and it is the one
 * with its own form state and validation, so it is the natural piece to lift out.
 */
class SetEntryController(
    private val logSets: LogSets,
    /**
     * Runs once the set is safely on disk — US-05's rest starts from here, and US-18's inline
     * PR check runs from the same signal, on the rows actually written.
     */
    private val onSetLogged: suspend (SessionExerciseId, List<ExerciseSet>) -> Unit,
    private val sets: SetRepository,
    private val unitPreference: UnitPreference,
    private val currentMember: CurrentMember,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<SetEntry?>(null)

    val entry: StateFlow<SetEntry?> = state

    /**
     * Opens entry prefilled per [ResolveSetPrefill] (US-37, ADR-0031): the member's most recent
     * set of this exact movement first, then the movement's target if the session copied one,
     * then a floor of 3 sets × 12 reps.
     *
     * The raw [ExerciseSet] is read directly here, rather than through
     * [com.gymtracker.core.domain.set.PrefillFromLastSet], because its `performedAt` is what
     * lets the sheet say *when* — "Prefilled from Tue 4 Aug" — not just what.
     */
    fun open(row: SessionExerciseRow) {
        scope.launch {
            val exerciseId = row.sessionExercise.exerciseId
            val unit = unitPreference.current()
            val target = row.sessionExercise.target
            val lastSet = sets.lastSetOf(exerciseId, currentMember.id())

            val resolved = ResolveSetPrefill(history = lastSet?.asPrefill(unit), target = target, unit = unit)

            state.value =
                SetEntry(
                    sessionExerciseId = row.sessionExercise.id,
                    exerciseName = row.exercise?.name ?: exerciseId.value,
                    weight = resolved.weight?.let(::trimNumber).orEmpty(),
                    reps = resolved.reps.toString(),
                    sets = resolved.sets.toString(),
                    // Never carried forward: RPE is how hard *that* set felt (US-03 prefills
                    // weight and reps only), so repeating it would invent a measurement.
                    rpe = "",
                    prefilled = lastSet != null || target != null,
                    fromHistory = resolved.fromHistory,
                    lastPerformedAt = lastSet?.performedAt,
                )
        }
    }

    /** One handler for the whole form; pass only the field that changed. */
    fun change(
        weight: String? = null,
        reps: String? = null,
        sets: String? = null,
        rpe: String? = null,
    ) {
        state.value =
            state.value?.let { current ->
                current.copy(
                    weight = weight ?: current.weight,
                    reps = reps ?: current.reps,
                    sets = sets ?: current.sets,
                    rpe = rpe ?: current.rpe,
                )
            }
    }

    /**
     * Nudges the weight by one increment of the member's own unit (ADR-0016): 2.5 kg or 5 lb,
     * the smallest change most gyms can actually load.
     *
     * The result is snapped onto the increment rather than offset from wherever the field
     * happened to be, so a prefill of 61.23 kg — which is 135 lb, entered by someone reading
     * pounds — steps to 62.5 rather than to 63.73.
     *
     * Stepping down past the bottom lands on blank, not on zero: a set with no recorded load
     * is a bodyweight set, and zero would claim the bar weighed nothing (constitution §2).
     */
    fun stepWeight(direction: Int) {
        scope.launch {
            val increment = unitPreference.current().weightIncrement()
            state.value =
                state.value?.let { current ->
                    val from = current.weight.trim().toDoubleOrNull() ?: 0.0
                    val stepped = snap(from, increment, direction)
                    current.copy(weight = if (stepped <= 0.0) "" else trimNumber(stepped))
                }
        }
    }

    /** Reps by one, never below the 1 that US-03 requires. */
    fun stepReps(direction: Int) {
        state.value =
            state.value?.let { current ->
                current.copy(reps = current.reps.stepWholeNumber(direction))
            }
    }

    /** Sets by one, never below the 1 that keeps the two-tap path a single confirm (ADR-0009). */
    fun stepSets(direction: Int) {
        state.value =
            state.value?.let { current ->
                current.copy(sets = current.sets.stepWholeNumber(direction))
            }
    }

    fun dismiss() {
        state.value = null
    }

    /**
     * Records the set, or several identical ones (ADR-0009). The sheet closes only after the
     * write returns, so the UI never moves on from a set that is not yet on disk (US-03).
     */
    fun confirm() {
        val confirmed = state.value?.validated() ?: return

        scope.launch {
            val logged =
                logSets(
                    sessionExerciseId = confirmed.entry.sessionExerciseId,
                    input =
                        SetInput(
                            weight = confirmed.weight,
                            unit = unitPreference.current(),
                            reps = confirmed.reps,
                            rpe = confirmed.rpe,
                        ),
                    sets = confirmed.sets,
                )
            state.value = null

            // Called after the write, so a failed save cannot start a rest for a set that
            // does not exist.
            onSetLogged(confirmed.entry.sessionExerciseId, logged)
        }
    }

    private data class ConfirmedSet(
        val entry: SetEntry,
        val weight: Double?,
        val reps: Int,
        val sets: Int,
        val rpe: Double?,
    )

    /**
     * @return null when the form cannot be saved: reps must parse to a whole number, and a
     *   weight that is present must parse. A blank weight is valid — that is a bodyweight
     *   set, which is absent rather than zero (constitution §2).
     */
    private fun SetEntry.validated(): ConfirmedSet? {
        val parsedReps = reps.toIntOrNull()
        val parsedSets = sets.toIntOrNull()
        val typed = weight.trim()
        val parsedWeight = typed.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val weightUnusable = typed.isNotEmpty() && parsedWeight == null

        val typedRpe = rpe.trim()
        val parsedRpe = typedRpe.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        // LogSet enforces the 5..10 range and half steps; here we only reject unparseable
        // text, so a typo does not become "not recorded".
        val rpeUnusable = typedRpe.isNotEmpty() && parsedRpe == null
        val unusable = parsedReps == null || parsedReps < 1 || parsedSets == null || parsedSets < 1

        return if (unusable || weightUnusable || rpeUnusable) {
            null
        } else {
            ConfirmedSet(this, parsedWeight, parsedReps, parsedSets, parsedRpe)
        }
    }
}

// The stepper arithmetic these use lives in SetSteppers.kt, shared with US-04's editor so a
// corrected set and a freshly logged one cannot disagree about what one press means.

/** The same conversion `DetermineUpNextSet.asPrefill` uses, for the same reason: RPE is never carried forward. */
private fun ExerciseSet.asPrefill(unit: WeightUnit) =
    SetPrefill(weight = weightKg?.let { UnitConverter.fromKilograms(it, unit) }, reps = reps)
