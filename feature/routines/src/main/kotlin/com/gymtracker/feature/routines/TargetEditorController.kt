package com.gymtracker.feature.routines

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.routine.RoutineItemRepository
import com.gymtracker.core.domain.routine.SetRoutineItemTarget
import com.gymtracker.core.domain.units.UnitConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The target-entry form for one movement (US-30), split out of [RoutineEditorViewModel] for the
 * same reason `SetEntryController` was split out of `ActiveSessionViewModel`: a form with its
 * own state and validation is the natural piece to lift out once a class covers more than one
 * concern.
 */
class TargetEditorController(
    private val items: RoutineItemRepository,
    private val catalog: ExerciseCatalog,
    private val setRoutineItemTarget: SetRoutineItemTarget,
    private val unitPreference: UnitPreference,
    /** Which routine is open, read fresh on every call rather than captured once at construction. */
    private val routineId: () -> RoutineId?,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<TargetEditorState?>(null)

    /** The target-entry form, open for one movement or absent. */
    val editor: StateFlow<TargetEditorState?> = state

    /**
     * Opens the target editor for one movement, prefilled from its current target if any.
     *
     * The load is converted to the member's own unit before it is shown — the same
     * [UnitConverter] round trip `SetEntryController` uses — rather than displaying the
     * stored kilograms verbatim: a target's `weightKg` is canonical storage (ADR-0006), not
     * what anyone typed.
     */
    fun onEdit(itemId: RoutineItemId) {
        val id = routineId() ?: return
        scope.launch {
            val item = items.itemsOf(id).firstOrNull { it.id == itemId } ?: return@launch
            val unit = unitPreference.current()
            val exerciseName =
                catalog
                    .observeRanked(ANY_MEMBER)
                    .first()
                    .firstOrNull { it.id == item.exerciseId }
                    ?.name
                    ?: item.exerciseId.value
            state.value =
                TargetEditorState(
                    itemId = itemId,
                    exerciseName = exerciseName,
                    unit = unit,
                    sets =
                        item.target
                            ?.sets
                            ?.toString()
                            .orEmpty(),
                    reps =
                        item.target
                            ?.reps
                            ?.toString()
                            .orEmpty(),
                    weight =
                        item.target
                            ?.weightKg
                            ?.let { UnitConverter.fromKilograms(it, unit) }
                            ?.let(::trimTargetWeight)
                            .orEmpty(),
                )
        }
    }

    /**
     * One handler for the whole form; pass only the field that changed.
     *
     * Any keystroke clears [TargetEditorState.errors]: the reason a save was refused describes
     * the form as it was, and a form being retyped is a different form.
     */
    fun onFieldChanged(
        sets: String? = null,
        reps: String? = null,
        weight: String? = null,
    ) {
        state.value =
            state.value?.let { current ->
                current.copy(
                    sets = sets ?: current.sets,
                    reps = reps ?: current.reps,
                    weight = weight ?: current.weight,
                    errors = emptyList(),
                )
            }
    }

    /**
     * Saves the open editor's fields as the movement's target, and closes it.
     *
     * Each field is independently optional (US-30: "3 x 8, load unrecorded is a plan"), so a
     * blank field is a valid, present-but-unset part of the target rather than a reason to
     * reject the whole form — only a field that was *typed* and does not parse, or parses
     * outside the valid range, blocks the save. A refused save leaves the editor open and says
     * which field it could not read ([TargetEditorState.errors]); it used to do nothing at all,
     * which the 2026-09-04 review found is the same defect PR #74 fixed on the set sheet, in a
     * dialog that reaches its write through a button that cannot be disabled ahead of time the
     * way `Save set` now is (an `AlertDialog`'s confirm button has no `enabled` of its own here).
     */
    fun onSave() {
        val editor = state.value ?: return
        val problems = editor.problems()
        if (problems.isNotEmpty()) {
            state.value = editor.copy(errors = problems)
        } else {
            save(editor)
        }
    }

    private fun save(editor: TargetEditorState) {
        val id = routineId() ?: return
        val parsed = editor.parsed() ?: return
        scope.launch {
            val item = items.itemsOf(id).firstOrNull { it.id == editor.itemId } ?: return@launch
            setRoutineItemTarget(item, parsed)
            state.value = null
        }
    }

    /** Clears the open editor's movement's target entirely, and closes the editor. */
    fun onClear() {
        val editor = state.value ?: return
        val id = routineId() ?: return
        scope.launch {
            val item = items.itemsOf(id).firstOrNull { it.id == editor.itemId } ?: return@launch
            setRoutineItemTarget(item, null)
            state.value = null
        }
    }

    fun onDismiss() {
        state.value = null
    }

    /**
     * @return the target [this] describes, in canonical kilograms (ADR-0006), or null if any
     *   typed field does not parse or clears the floor — the same fields [problems] names.
     */
    private fun TargetEditorState.parsed(): MovementTarget? {
        if (problems().isNotEmpty()) return null
        val parsedSets = sets.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val parsedReps = reps.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val parsedWeight = weight.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val weightKg = parsedWeight?.let { UnitConverter.toKilograms(it, unit) }
        return MovementTarget(parsedSets, parsedReps, weightKg)
    }

    /**
     * One line per typed field that cannot be read as the number it should be, in field order,
     * or empty when the form is saveable. A blank field is never a problem (US-30).
     */
    private fun TargetEditorState.problems(): List<String> =
        buildList {
            val parsedSets = sets.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            val parsedReps = reps.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            val typedWeight = weight.trim()
            val parsedWeight = typedWeight.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

            if (sets.isNotBlank() && (parsedSets == null || parsedSets < MIN_TARGET_SETS)) add(SETS_PROBLEM)
            if (reps.isNotBlank() && (parsedReps == null || parsedReps < MIN_TARGET_REPS)) add(REPS_PROBLEM)
            if (typedWeight.isNotEmpty() && (parsedWeight == null || parsedWeight < 0)) add(LOAD_PROBLEM)
        }

    private companion object {
        const val MIN_TARGET_SETS = 1
        const val MIN_TARGET_REPS = 1

        /** What the dialog shows for each unreadable field; asserted literally by the unit test. */
        const val SETS_PROBLEM = "Sets needs a whole number, 1 or more."
        const val REPS_PROBLEM = "Reps needs a whole number, 1 or more."
        const val LOAD_PROBLEM = "Load needs a number, 0 or more."

        /** The catalog's ranking is per member; this form does not rank, it only needs a name. */
        val ANY_MEMBER = UserId("")
    }
}

/** "105", not "105.0" — a whole-number target weight reads as a typed number, not a float. */
private fun trimTargetWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
