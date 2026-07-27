package com.gymtracker.feature.logging

import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.PrefillFromLastSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The set-entry sheet for one exercise in the session (US-03). */
data class SetEntry(
    val sessionExerciseId: SessionExerciseId,
    val exerciseName: String,
    val weight: String,
    val reps: String,
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
    private val logSet: LogSet,
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
                    prefilled = prefill != null,
                )
        }
    }

    /** One handler for the whole form; pass only the field that changed. */
    fun change(
        weight: String? = null,
        reps: String? = null,
    ) {
        state.value =
            state.value?.let { current ->
                current.copy(weight = weight ?: current.weight, reps = reps ?: current.reps)
            }
    }

    fun dismiss() {
        state.value = null
    }

    /**
     * Records the set. The sheet closes only after [LogSet] returns, so the UI never moves on
     * from a set that is not yet on disk (US-03).
     */
    fun confirm() {
        val confirmed = state.value?.validated() ?: return

        scope.launch {
            logSet(
                confirmed.entry.sessionExerciseId,
                confirmed.weight,
                unitPreference.current(),
                confirmed.reps,
                rpe = null,
            )
            state.value = null
        }
    }

    private data class ConfirmedSet(
        val entry: SetEntry,
        val weight: Double?,
        val reps: Int,
    )

    /**
     * @return null when the form cannot be saved: reps must parse to a whole number, and a
     *   weight that is present must parse. A blank weight is valid — that is a bodyweight
     *   set, which is absent rather than zero (constitution §2).
     */
    private fun SetEntry.validated(): ConfirmedSet? {
        val parsedReps = reps.toIntOrNull()
        val typed = weight.trim()
        val parsedWeight = typed.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val weightUnusable = typed.isNotEmpty() && parsedWeight == null

        return if (parsedReps == null || weightUnusable) {
            null
        } else {
            ConfirmedSet(this, parsedWeight, parsedReps)
        }
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 ==
            0.0
        ) {
            value.toLong().toString()
        } else {
            value.toString()
        }
}
