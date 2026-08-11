package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.progress.ExerciseTrendOf
import com.gymtracker.core.domain.progress.MostRecentlyTrainedExercise
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.PerformedExercise
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionDetail
import com.gymtracker.core.domain.session.SessionHistory
import com.gymtracker.core.domain.session.SessionSummary
import com.gymtracker.core.domain.session.WorkoutDetail
import com.gymtracker.core.domain.set.DeleteSet
import com.gymtracker.core.domain.set.RestoreSet
import com.gymtracker.core.domain.set.UpdateSet
import com.gymtracker.core.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Everything the history and workout-detail destinations render (ADR-0024). */
data class HistoryUiState(
    val isOpen: Boolean = false,
    /** Finished workouts, newest first. The session in progress is never among them. */
    val sessions: List<SessionSummary> = emptyList(),
    /** Whether the last delete can still be taken back — true for five seconds after it. */
    val canUndo: Boolean = false,
    /** The workout opened from the list, or null while the list itself is on screen (US-06b). */
    val detail: SessionDetail? = null,
    val unit: WeightUnit = WeightUnit.LB,
    /** The past set being corrected, if any (US-04's third criterion). */
    val setEdit: SetEdit? = null,
    /** Whether the past set just deleted can still be put back. */
    val canUndoSetDelete: Boolean = false,
    /** The Progress tab's top section (US-33). */
    val topLift: TopLift = TopLift.None,
)

/**
 * Past workouts (US-06, US-06a), and one opened in full (US-06b), as destinations of their own
 * (ADR-0024) instead of a flag inside `ActiveSessionViewModel`.
 *
 * `ActiveSessionViewModel` carried a comment, written when ADR-0017 added the guided flow,
 * saying the next thing added to that class should split it rather than pile on. This is that
 * split: [HistoryController] moves in unchanged — same class, same behaviour, same tests, only
 * a different owner. The set editor comes with it, because correcting a set logged in a past
 * workout (US-04's third criterion) needs one wherever a set is shown, and this screen no
 * longer rides on the active session's.
 *
 * History and its detail are reached as two separate navigation destinations now rather than
 * one screen toggling a flag, so two instances of this ViewModel typically exist at once — one
 * scoped to the list, one to the detail — each reading only the half of [HistoryController] it
 * needs. `open()` never needs a matching `close()` to "forget" anything: leaving the
 * destination clears its `ViewModelStore`, which is what actually forgets it.
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        sessionHistory: SessionHistory,
        workoutDetail: WorkoutDetail,
        deleteSession: DeleteSession,
        restoreSession: RestoreSession,
        currentMember: CurrentMember,
        updateSet: UpdateSet,
        deleteSet: DeleteSet,
        restoreSet: RestoreSet,
        mostRecentlyTrainedExercise: MostRecentlyTrainedExercise,
        exerciseTrendOf: ExerciseTrendOf,
        catalog: ExerciseCatalog,
        private val unitPreference: UnitPreference,
    ) : ViewModel() {
        /** History and deleting from it; unchanged from when `ActiveSessionViewModel` owned it. */
        private val history =
            HistoryController(
                history = sessionHistory,
                workoutDetail = workoutDetail,
                sessionDeletion = SessionDeletion(deleteSession, restoreSession),
                currentMember = currentMember,
                topLiftLoader = TopLiftLoader(mostRecentlyTrainedExercise, exerciseTrendOf, catalog),
                scope = viewModelScope,
            )

        /** Correcting a set from a past workout; unchanged from `ActiveSessionViewModel`'s. */
        private val setEdit =
            SetEditController(
                updateSet = updateSet,
                deleteSet = deleteSet,
                restoreSet = restoreSet,
                unitPreference = unitPreference,
                scope = viewModelScope,
            )

        val uiState: StateFlow<HistoryUiState> =
            combine(
                history.state,
                unitPreference.observe(),
                setEdit.edit,
                setEdit.canUndo,
            ) { historyState, unit, edit, canUndoSetDelete ->
                HistoryUiState(
                    isOpen = historyState.isOpen,
                    sessions = historyState.sessions,
                    canUndo = historyState.canUndo,
                    detail = historyState.detail,
                    unit = unit,
                    setEdit = edit,
                    canUndoSetDelete = canUndoSetDelete,
                    topLift = historyState.topLift,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState())

        /** Starts loading the list (US-06). Nothing is read before this is called. */
        fun open() = history.open()

        /** Leaves the list. Not needed for navigating away — see the class doc — kept for parity. */
        fun close() = history.close()

        /** Opens one past workout in full (US-06b). */
        fun openWorkout(id: SessionId) = history.openWorkout(id)

        /** Back to the list, without leaving history (US-06b). */
        fun closeWorkout() = history.closeWorkout()

        /** Deletes a past workout and starts the undo window (US-06a). */
        fun delete(id: SessionId) = history.delete(id)

        /** Puts the last deleted workout back, if the window has not closed. */
        fun undo() = history.undo()

        /** Opens the editor on a set from a past workout (US-04's third criterion, ADR-0022). */
        fun onEditPastSet(
            performed: PerformedExercise,
            set: ExerciseSet,
        ) = setEdit.open(set, performed.exercise?.name ?: performed.sessionExercise.exerciseId.value)

        /** Set editor actions, wired the same way `ActiveSessionViewModel`'s are. */
        internal fun setEditCallbacks() =
            SetEditCallbacks(
                onWeightChanged = { setEdit.change(weight = it) },
                onWeightStepped = setEdit::stepWeight,
                onRepsChanged = { setEdit.change(reps = it) },
                onRepsStepped = setEdit::stepReps,
                onRpeChanged = { setEdit.change(rpe = it) },
                onSave = setEdit::save,
                onDelete = setEdit::delete,
                onDismiss = setEdit::dismiss,
            )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
