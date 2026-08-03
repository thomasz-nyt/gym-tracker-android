package com.gymtracker.feature.logging

import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.set.SetInput
import com.gymtracker.core.domain.units.weightIncrement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/** The set-entry sheet for one exercise in the session (US-03). */
data class SetEntry(
    val sessionExerciseId: SessionExerciseId,
    val exerciseName: String,
    val weight: String,
    val reps: String,
    /** How many identical sets to record — "3 sets of 12" (ADR-0009). Defaults to "1". */
    val sets: String,
    /** Optional, 5.0–10.0 in half steps. Blank means not recorded, which is not the same as easy. */
    val rpe: String,
    /** True when the fields came from a previous set rather than being empty (US-03). */
    val prefilled: Boolean,
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
    /** Runs once the set is safely on disk — US-05's rest starts from here. */
    private val onSetLogged: suspend () -> Unit,
    private val prefillFromLastSet: PrefillFromLastSet,
    private val unitPreference: UnitPreference,
    private val currentMember: CurrentMember,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<SetEntry?>(null)

    val entry: StateFlow<SetEntry?> = state

    /**
     * Opens entry prefilled from the member's most recent set of this exercise (US-03).
     *
     * The whole point of the prefill is that the numbers are usually already right, so
     * confirming costs one more tap.
     */
    fun open(row: SessionExerciseRow) {
        scope.launch {
            val exerciseId = row.sessionExercise.exerciseId
            val unit = unitPreference.current()
            val prefill = prefillFromLastSet(exerciseId, currentMember.id(), unit)

            state.value =
                SetEntry(
                    sessionExerciseId = row.sessionExercise.id,
                    exerciseName = row.exercise?.name ?: exerciseId.value,
                    // Already in the member's unit; PrefillFromLastSet converted it.
                    weight = prefill?.weight?.let(::trimNumber).orEmpty(),
                    reps = prefill?.reps?.toString().orEmpty(),
                    // Not prefilled from history: how many sets you did last time is not a
                    // claim about today, and defaulting to 1 keeps the two-tap path intact.
                    sets = "1",
                    // Never carried forward: RPE is how hard *that* set felt (US-03 prefills
                    // weight and reps only), so repeating it would invent a measurement.
                    rpe = "",
                    prefilled = prefill != null,
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
            onSetLogged()
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

// The stepper arithmetic below is deliberately outside the controller: none of it touches the
// controller's state, and it is easier to check when it reads as plain functions.

/** US-03 for reps, ADR-0009 for sets: neither is meaningful below one. */
private const val WHOLE_NUMBER_FLOOR = 1

private fun String.stepWholeNumber(direction: Int): String {
    val from = trim().toIntOrNull() ?: 0
    return (from + direction).coerceAtLeast(WHOLE_NUMBER_FLOOR).toString()
}

/**
 * The next multiple of [increment] in [direction], starting from [from].
 *
 * Rounding towards the direction of travel is what makes an off-grid value tidy itself up on
 * the first press instead of carrying its remainder forever.
 */
private fun snap(
    from: Double,
    increment: Double,
    direction: Int,
): Double {
    val steps = from / increment
    val next = if (direction >= 0) floor(steps) + 1 else ceil(steps) - 1
    return next * increment
}

private fun trimNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
