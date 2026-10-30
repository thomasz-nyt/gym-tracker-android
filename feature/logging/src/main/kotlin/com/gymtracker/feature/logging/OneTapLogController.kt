package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.rest.LogUpNextSet
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.set.DeleteSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

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
    private val submission: Mutex = Mutex(),
) {
    private val failure = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = failure

    fun dismissError() {
        failure.value = null
    }

    private val undoable = MutableStateFlow<ExerciseSet?>(null)

    /** Whether the set just logged in one tap can still be taken back. */
    val canUndo: Flow<Boolean> = undoable.map { it != null }

    /**
     * Logs [next] exactly as [LogUpNextSet] does — the write and the rest are that use case's,
     * shared with the notification's own `LOG SET` (US-56) — and opens the undo window on the
     * row it wrote. A second log before the window closes replaces the undoable row: only the
     * most recent one-tap log can be taken back, as with a deleted set.
     * Keep unrelated storage failures recoverable at the one-tap UI boundary.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun log(next: UpNextSet): ExerciseSet? {
        if (!submission.tryLock()) return null
        return try {
            val logged = logUpNextSet(next)
            failure.value = null
            undoable.value = logged
            logged
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure.value = "Could not log the set. ${error.message.orEmpty()}"
            null
        } finally {
            submission.unlock()
        }
    }

    /**
     * Takes back the set the last one-tap log wrote, if the window is still open: the row is
     * deleted and the rest it started is ended — a rest earned by a set that no longer exists is
     * a countdown to nothing, and ending it is also what removes the notification that was
     * counting it down (US-56). [onUndone] runs once both have happened.
     */
    fun undo() {
        val logged = undoable.value ?: return
        undoable.value = null
        scope.launch {
            deleteSet(logged.id)
            restTimer.skip()
            onUndone()
        }
    }

    /** Leaving a workout must not allow its last set to be undone from a different workout. */
    fun clear() {
        undoable.value = null
    }
}
