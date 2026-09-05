package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.rest.LogUpNextSet
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.set.DeleteSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * The one-tap log (US-35) and the five-second window to take it back.
 *
 * A one-tap button is also a one-tap misfire: a thumb landing on `LOG SET` writes a set *and*
 * starts a rest, with nothing between the tap and the row. Every other destructive or
 * irreversible-by-accident action on the session screen already has ADR-0012's window (a deleted
 * set, a removed exercise, a deleted workout); this gives the one that creates rows the same.
 *
 * Split out of [ActiveSessionViewModel] the way [SetEditController] and [ExerciseRemovalController]
 * were — a window with its own state and expiry is the natural piece to lift, and that class's
 * own doc already asks the next thing added to split rather than pile on.
 */
class OneTapLogController(
    private val logUpNextSet: LogUpNextSet,
    private val deleteSet: DeleteSet,
    private val restTimer: RestTimer,
    private val scope: CoroutineScope,
    /**
     * Runs once an undo has deleted the row and ended its rest — for whatever the screen said
     * about that set on its own account (the record banner). Bound here rather than passed per
     * call so the screen's wiring is one method reference, the same shape as [SetEditController.undo].
     */
    private val onUndone: () -> Unit = {},
) {
    private val undoable = MutableStateFlow<ExerciseSet?>(null)
    private var expiry: Job? = null

    /** Whether the set just logged in one tap can still be taken back. */
    val canUndo: Flow<Boolean> = undoable.map { it != null }

    /**
     * Logs [next] exactly as [LogUpNextSet] does — the write and the rest are that use case's,
     * shared with the notification's own `LOG SET` (US-56) — and opens the undo window on the
     * row it wrote. A second log before the window closes replaces the undoable row: only the
     * most recent one-tap log can be taken back, as with a deleted set.
     */
    suspend fun log(next: UpNextSet): ExerciseSet {
        val logged = logUpNextSet(next)
        undoable.value = logged
        expiry?.cancel()
        expiry =
            scope.launch {
                delay(UNDO_WINDOW.toMillis())
                undoable.value = null
            }
        return logged
    }

    /**
     * Takes back the set the last one-tap log wrote, if the window is still open: the row is
     * deleted and the rest it started is ended — a rest earned by a set that no longer exists is
     * a countdown to nothing, and ending it is also what removes the notification that was
     * counting it down (US-56). [onUndone] runs once both have happened.
     */
    fun undo() {
        val logged = undoable.value ?: return
        expiry?.cancel()
        undoable.value = null
        scope.launch {
            deleteSet(logged.id)
            restTimer.skip()
            onUndone()
        }
    }

    private companion object {
        /** ADR-0012's window, so every undo on this screen behaves alike. */
        val UNDO_WINDOW: Duration = Duration.ofSeconds(5)
    }
}
