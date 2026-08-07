package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.sessionexercise.RemoveExerciseFromSession
import com.gymtracker.core.domain.sessionexercise.RemovedExercise
import com.gymtracker.core.domain.sessionexercise.RestoreExerciseToSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Taking an exercise back out of the session (US-02c).
 *
 * Its own state holder, following [RestController], [SetEntryController] and
 * [HistoryController]. The removal is immediate and undo restores from the snapshot
 * [RemoveExerciseFromSession] hands back — the same shape [HistoryController] uses for a whole
 * workout, because ADR-0012 already settled how a destructive action behaves here.
 *
 * Only the most recent removal can be undone. A second one inside the window replaces the
 * first, which is by then already committed.
 */
class ExerciseRemovalController(
    private val removeExercise: RemoveExerciseFromSession,
    private val restoreExercise: RestoreExerciseToSession,
    private val scope: CoroutineScope,
) {
    private val undoable = MutableStateFlow<RemovedExercise?>(null)
    private var expiry: Job? = null

    /** True for five seconds after a removal, while it can still be taken back. */
    val canUndo: Flow<Boolean> = undoable.map { it != null }

    /**
     * Removes an exercise and starts the undo window.
     *
     * The row is gone from the database before the list re-renders without it, so what is on
     * screen is never ahead of what is stored (§2).
     */
    fun remove(id: SessionExerciseId) {
        scope.launch {
            val removed = removeExercise(id) ?: return@launch
            undoable.value = removed
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(UNDO_WINDOW.toMillis())
                    undoable.value = null
                }
        }
    }

    /** Puts the last removed exercise back, if the window has not closed. */
    fun undo() {
        val removed = undoable.value ?: return
        forget()
        scope.launch { restoreExercise(removed) }
    }

    private fun forget() {
        expiry?.cancel()
        undoable.value = null
    }

    private companion object {
        /** US-04's window, reused so the app's destructive actions behave alike. */
        val UNDO_WINDOW: Duration = Duration.ofSeconds(5)
    }
}
