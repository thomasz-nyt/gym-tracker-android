package com.gymtracker.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.routine.AddExerciseToRoutine
import com.gymtracker.core.domain.routine.DeleteRoutine
import com.gymtracker.core.domain.routine.MoveExerciseInRoutine
import com.gymtracker.core.domain.routine.RemoveExerciseFromRoutine
import com.gymtracker.core.domain.routine.RenameRoutine
import com.gymtracker.core.domain.routine.RoutineItemRepository
import com.gymtracker.core.domain.routine.RoutineRepository
import com.gymtracker.core.domain.routine.SetRoutineItemTarget
import com.gymtracker.core.domain.set.LastPerformance
import com.gymtracker.core.domain.set.LastPerformanceOf
import com.gymtracker.core.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One movement in the editor.
 *
 * [lastTime] is what the member actually lifted; it is null when they never have, and the
 * screen then shows the movement with no numbers rather than a zero (US-13's absence pattern,
 * constitution §2.4). [target] is the plan for next time (US-30, ADR-0027) — also null rather
 * than zero when nobody has set one, the same absence pattern, and rendered visibly distinct
 * from [lastTime] rather than merged into it. ADR-0020 named the earlier version of this type,
 * with no target field at all, as the whole bargain that bought the routine concept; ADR-0027
 * is the later maintainer decision that revisits exactly that point, and only that point.
 */
data class MovementRow(
    val itemId: RoutineItemId,
    val exerciseId: ExerciseId,
    val exerciseName: String,
    val lastTime: LastPerformance?,
    val target: MovementTarget? = null,
)

/**
 * The target-entry form for one movement (US-30). String fields, the same reason
 * `SetEntry`'s are: a field can be blank mid-edit without being a parse error, and "3 sets,
 * load unrecorded" needs to render two filled fields and one truly empty one, not a zero.
 */
data class TargetEditorState(
    val itemId: RoutineItemId,
    val exerciseName: String,
    /** The member's unit, read once when the editor opened — for [weight]'s field label. */
    val unit: WeightUnit,
    val sets: String,
    val reps: String,
    val weight: String,
    /**
     * Why the last save was refused, one line per field that could not be read — empty until a
     * save is attempted on an unusable form, and cleared by the next keystroke. Found by the
     * 2026-09-04 review: `onSave` already refused an unparseable form correctly and then said
     * nothing, so the dialog just stayed open with no signal why (US-30).
     */
    val errors: List<String> = emptyList(),
)

/** Everything the routine editor renders. */
data class RoutineEditorUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val movements: List<MovementRow> = emptyList(),
    val unit: WeightUnit = WeightUnit.LB,
)

/**
 * US-29's editor: a name, an order, add and remove. That is the whole of it.
 *
 * Which routine is being edited arrives through [open] rather than a constructor argument,
 * the same way `HistoryViewModel.openWorkout` takes the workout it shows — so the graph keeps
 * a plain `@HiltViewModel` and the route stays the screen's business.
 */
@Suppress("LongParameterList")
@HiltViewModel
class RoutineEditorViewModel
    @Inject
    constructor(
        private val routines: RoutineRepository,
        private val items: RoutineItemRepository,
        private val catalog: ExerciseCatalog,
        private val currentMember: CurrentMember,
        unitPreference: UnitPreference,
        private val addExerciseToRoutine: AddExerciseToRoutine,
        private val removeExerciseFromRoutine: RemoveExerciseFromRoutine,
        private val moveExerciseInRoutine: MoveExerciseInRoutine,
        private val renameRoutine: RenameRoutine,
        private val deleteRoutine: DeleteRoutine,
        private val lastPerformanceOf: LastPerformanceOf,
        private val setRoutineItemTarget: SetRoutineItemTarget,
    ) : ViewModel() {
        private val editing = MutableStateFlow<RoutineId?>(null)

        /**
         * The target-entry form lives in its own state holder; see [TargetEditorController].
         * `editing::value` rather than a captured id: the routine being edited can change (a
         * fresh [open]) while this controller instance does not.
         */
        val target =
            TargetEditorController(
                items = items,
                catalog = catalog,
                setRoutineItemTarget = setRoutineItemTarget,
                unitPreference = unitPreference,
                routineId = editing::value,
                scope = viewModelScope,
            )

        private val deleted = MutableStateFlow(false)

        /** True once [onDeleteRoutine] has completed, so the screen knows to leave. */
        val isDeleted: StateFlow<Boolean> = deleted

        /** Renamed locally the instant it is typed, so the field does not fight the database. */
        private val typedName = MutableStateFlow<String?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val storedName: Flow<String> =
            editing.filterNotNull().flatMapLatest { id -> flow { emit(routines.find(id)?.name.orEmpty()) } }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val movements: Flow<List<MovementRow>> =
            editing
                .filterNotNull()
                .flatMapLatest { id ->
                    combine(items.observeItems(id), catalog.observeRanked(ANY_MEMBER)) { rows, all ->
                        rows to all.associateBy(Exercise::id)
                    }
                }.flatMapLatest { (rows, byId) ->
                    // Read once per emission rather than observed: what you lifted last changes
                    // only when a set is logged, which cannot happen while this screen is up.
                    flow {
                        val member = currentMember.id()
                        emit(
                            rows.map { item ->
                                MovementRow(
                                    itemId = item.id,
                                    exerciseId = item.exerciseId,
                                    exerciseName = byId[item.exerciseId]?.name ?: item.exerciseId.value,
                                    lastTime = lastPerformanceOf(item.exerciseId, member),
                                    target = item.target,
                                )
                            },
                        )
                    }
                }

        val uiState: StateFlow<RoutineEditorUiState> =
            combine(
                storedName,
                typedName,
                movements,
                unitPreference.observe(),
            ) { stored, typed, rows, unit ->
                RoutineEditorUiState(
                    isLoading = false,
                    name = typed ?: stored,
                    movements = rows,
                    unit = unit,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RoutineEditorUiState())

        /** Points the editor at a routine. Idempotent, so recomposition does not reload it. */
        fun open(routineId: RoutineId) {
            if (editing.value != routineId) {
                editing.value = routineId
                typedName.value = null
            }
        }

        fun onNameChanged(name: String) {
            typedName.value = name
            val id = editing.value ?: return
            viewModelScope.launch { renameRoutine(id, name) }
        }

        fun onAddExercise(exerciseId: ExerciseId) = onExercisesChosen(listOf(exerciseId))

        /** Appends everything one visit to the picker chose, in pick order (US-02a's rule). */
        fun onExercisesChosen(exerciseIds: List<ExerciseId>) {
            val id = editing.value ?: return
            if (exerciseIds.isEmpty()) return

            // One coroutine, in sequence: positions come from MAX(position) + 1, so appending
            // concurrently would let two movements land on the same position.
            viewModelScope.launch { exerciseIds.forEach { addExerciseToRoutine(id, it) } }
        }

        fun onRemoveMovement(id: RoutineItemId) {
            viewModelScope.launch { removeExerciseFromRoutine(id) }
        }

        /**
         * Deletes the routine being edited and its movements (ADR-0019: destructive lives here,
         * not on the Routines list row). No session is affected — a routine relates to one only
         * by having been copied into it (ADR-0020), and a copy does not depend on the original.
         */
        fun onDeleteRoutine() {
            val id = editing.value ?: return
            viewModelScope.launch {
                deleteRoutine(id)
                deleted.value = true
            }
        }

        /**
         * Up and down rather than a drag (US-29 says "reorder"; the mock says "drag to reorder").
         *
         * A drag needs a gesture that is hard to perform one-handed with chalk on your fingers,
         * and it is invisible to TalkBack without work M7's accessibility pass has not done.
         * Two buttons reorder the same list, and `MoveExerciseInRoutine` takes from/to either
         * way, so a drag can replace this later without the domain changing at all.
         */
        fun onMoveUp(index: Int) = move(from = index, to = index - 1)

        fun onMoveDown(index: Int) = move(from = index, to = index + 1)

        private fun move(
            from: Int,
            to: Int,
        ) {
            val id = editing.value ?: return
            // Out-of-range is ignored by the use case, so the ends of the list need no guard here.
            viewModelScope.launch { moveExerciseInRoutine(id, from = from, to = to) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /**
             * The catalog's ranking is per member; this screen does not rank, it only needs names.
             * A fixed id keeps the flow off a suspend lookup it would otherwise re-run per item.
             */
            val ANY_MEMBER = UserId("")
        }
    }
