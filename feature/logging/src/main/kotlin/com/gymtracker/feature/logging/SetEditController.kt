package com.gymtracker.feature.logging

import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.set.DeleteSet
import com.gymtracker.core.domain.set.RestoreSet
import com.gymtracker.core.domain.set.UpdateSet
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.weightIncrement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * The editor for one set already logged (US-04).
 *
 * Deliberately not [SetEntry]: that form has a **sets** field, because logging can write three
 * identical rows at once (ADR-0009). Correcting is always about exactly one row, so offering a
 * repeat count here would mean "turn this set into three", which is not what US-04 says and not
 * what a member tapping their own set expects.
 *
 * @property set the row being corrected, carried so the write can keep its identity.
 */
data class SetEdit(
    val set: ExerciseSet,
    val exerciseName: String,
    val weight: String,
    val reps: String,
    /** Optional, 5.0–10.0 in half steps. Blank means not recorded, which is not the same as easy. */
    val rpe: String,
)

/**
 * Correcting and deleting a logged set (US-04), in its own state holder alongside
 * [SetEntryController], [ExerciseRemovalController] and [HistoryController].
 *
 * Delete lives here rather than on the set row because of ADR-0019: a destructive control never
 * shares a surface with a save. The only way to reach it is to open the editor for the set it
 * would delete, which also means you have just read the set you are about to remove.
 *
 * The undo window is ADR-0012's, the same five seconds US-02c and US-06a use. Only the most
 * recent delete can be taken back; a second one inside the window replaces the first, which is
 * by then already committed.
 */
class SetEditController(
    private val updateSet: UpdateSet,
    private val deleteSet: DeleteSet,
    private val restoreSet: RestoreSet,
    private val unitPreference: UnitPreference,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<SetEdit?>(null)
    private val undoable = MutableStateFlow<ExerciseSet?>(null)
    private var expiry: Job? = null

    val edit: StateFlow<SetEdit?> = state

    /** True for five seconds after a delete, while the set can still be put back. */
    val canUndo: Flow<Boolean> = undoable.map { it != null }

    /** Opens the editor on [set], with its stored kilograms shown in the member's own unit. */
    fun open(
        set: ExerciseSet,
        exerciseName: String,
    ) {
        scope.launch {
            val unit = unitPreference.current()
            state.value =
                SetEdit(
                    set = set,
                    exerciseName = exerciseName,
                    weight = set.weightKg?.let { trimNumber(UnitConverter.fromKilograms(it, unit)) }.orEmpty(),
                    reps = set.reps.toString(),
                    rpe = set.rpe?.let(::trimNumber).orEmpty(),
                )
        }
    }

    /** One handler for the whole form; pass only the field that changed. */
    fun change(
        weight: String? = null,
        reps: String? = null,
        rpe: String? = null,
    ) {
        state.value =
            state.value?.let { current ->
                current.copy(
                    weight = weight ?: current.weight,
                    reps = reps ?: current.reps,
                    rpe = rpe ?: current.rpe,
                )
            }
    }

    /** As in set entry: one increment of the member's unit, and down past the bottom is blank. */
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

    /** Reps by one, never below the 1 that US-03 requires of any set. */
    fun stepReps(direction: Int) {
        state.value =
            state.value?.let { current ->
                current.copy(reps = current.reps.stepWholeNumber(direction))
            }
    }

    fun dismiss() {
        state.value = null
    }

    /**
     * Writes the correction. The editor closes only after the write returns, so what is on
     * screen is never ahead of what is stored (constitution §2).
     */
    fun save() {
        val corrected = state.value?.validated() ?: return

        scope.launch {
            updateSet(
                set = corrected.edit.set,
                weight = corrected.weight,
                unit = unitPreference.current(),
                reps = corrected.reps,
                rpe = corrected.rpe,
            )
            state.value = null
        }
    }

    private data class CorrectedSet(
        val edit: SetEdit,
        val weight: Double?,
        val reps: Int,
        val rpe: Double?,
    )

    /**
     * @return null when the form cannot be saved, which leaves the editor open on the bad value
     *   rather than writing it. A blank weight is fine — that is a bodyweight set, absent rather
     *   than zero (constitution §2). Unparseable text is not: saving it would quietly turn a
     *   load the member did lift into an absence.
     */
    private fun SetEdit.validated(): CorrectedSet? {
        val parsedReps = reps.trim().toIntOrNull()
        val typedWeight = weight.trim()
        val parsedWeight = typedWeight.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val typedRpe = rpe.trim()
        val parsedRpe = typedRpe.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

        val unusable =
            parsedReps == null ||
                parsedReps < 1 ||
                (typedWeight.isNotEmpty() && parsedWeight == null) ||
                (typedRpe.isNotEmpty() && parsedRpe == null)

        return if (unusable) null else CorrectedSet(this, parsedWeight, parsedReps, parsedRpe)
    }

    /** Deletes the set the editor is open on, closes it, and starts the undo window. */
    fun delete() {
        val current = state.value ?: return
        scope.launch {
            val deleted = deleteSet(current.set.id) ?: return@launch
            state.value = null
            undoable.value = deleted
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(UNDO_WINDOW.toMillis())
                    undoable.value = null
                }
        }
    }

    /** Puts the last deleted set back, if the window has not closed. */
    fun undo() {
        val deleted = undoable.value ?: return
        expiry?.cancel()
        undoable.value = null
        scope.launch { restoreSet(deleted) }
    }

    private companion object {
        /** ADR-0012's window, so the app's destructive actions all behave alike. */
        val UNDO_WINDOW: Duration = Duration.ofSeconds(5)
    }
}
