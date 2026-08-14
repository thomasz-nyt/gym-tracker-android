package com.gymtracker.feature.logging

import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.progress.EightWeekChange
import com.gymtracker.core.domain.progress.MostRecentlyTrainedExercise
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.DeletedSession
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionDetail
import com.gymtracker.core.domain.session.SessionHistory
import com.gymtracker.core.domain.session.SessionSummary
import com.gymtracker.core.domain.session.WorkoutDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * US-33's top section: one lift, chosen without asking — see [MostRecentlyTrainedExercise].
 *
 * [None] covers every honest reason there is nothing to feature (US-19): nothing ever logged,
 * the newest session had nothing actually performed in it, or that exercise's one recorded day
 * was entirely bodyweight and left no estimate to show.
 */
sealed interface TopLift {
    data object None : TopLift

    data class Lift(
        val exerciseId: ExerciseId,
        val exerciseName: String,
        val estimatedOneRepMaxKg: Double,
        /** Null when history does not reach back 8 weeks — see [EightWeekChange.deltaKg]. */
        val deltaKg: Double?,
    ) : TopLift
}

/**
 * Deleting a past workout and putting it back (US-06a), bundled so [HistoryController]'s
 * constructor names one collaborator for the pair instead of two.
 */
class SessionDeletion(
    private val deleteSession: DeleteSession,
    private val restoreSession: RestoreSession,
) {
    suspend fun delete(id: SessionId): DeletedSession? = deleteSession(id)

    suspend fun restore(deleted: DeletedSession) = restoreSession(deleted)
}

/** The history screen's slice of [SessionUiState] (US-06, US-06a, US-06b, US-33). */
data class HistoryState(
    val isOpen: Boolean = false,
    /** Finished workouts, newest first. The session in progress is never among them. */
    val sessions: List<SessionSummary> = emptyList(),
    /** Whether the last delete can still be taken back — true for five seconds after it. */
    val canUndo: Boolean = false,
    /** The workout opened from the list, or null while the list itself is on screen (US-06b). */
    val detail: SessionDetail? = null,
    val topLift: TopLift = TopLift.None,
    /** Which of [sessions] earns the `PR` badge (US-38). */
    val sessionsWithRecords: Set<SessionId> = emptySet(),
)

/**
 * History, and deleting from it (US-06, US-06a).
 *
 * Its own state holder rather than more surface on [ActiveSessionViewModel], following
 * [RestController] and [SetEntryController]. Nothing here is loaded until the screen is
 * opened: history is a side trip, and the core loop should not pay for it.
 *
 * Deleting is immediate and undo restores from the snapshot [DeleteSession] hands back
 * (ADR-0012). Only the most recent delete can be undone; a second delete inside the window
 * replaces the first, which is by then already committed.
 */
class HistoryController(
    private val history: SessionHistory,
    private val workoutDetail: WorkoutDetail,
    private val sessionDeletion: SessionDeletion,
    private val currentMember: CurrentMember,
    private val topLiftLoader: TopLiftLoader,
    private val scope: CoroutineScope,
) {
    private val open = MutableStateFlow(false)
    private val opened = MutableStateFlow<SessionId?>(null)
    private val undoable = MutableStateFlow<DeletedSession?>(null)
    private var expiry: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val entries: Flow<List<SessionSummary>> =
        open.flatMapLatest { isOpen ->
            if (!isOpen) {
                flowOf(emptyList())
            } else {
                flow { emit(currentMember.id()) }.flatMapLatest { member -> history(member) }
            }
        }

    /**
     * US-33: read only when the screen opens, the same "side trip should not pay for itself"
     * rule [entries] already follows — not re-read as the list changes underneath it, since
     * nothing on this screen can change what was most recently trained while it is up.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val topLift: Flow<TopLift> =
        open.flatMapLatest { isOpen ->
            if (!isOpen) {
                flowOf<TopLift>(TopLift.None)
            } else {
                flow { emit(topLiftLoader(currentMember.id())) }
            }
        }

    /** Nothing is read until a workout is actually opened; the list should not pay for it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val detail: Flow<SessionDetail?> =
        opened.flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                flow { emit(currentMember.id()) }.flatMapLatest { member -> workoutDetail(id, member) }
            }
        }

    val state: Flow<HistoryState> =
        combine(open, entries, undoable, detail, topLift) { isOpen, sessions, undo, opened, lift ->
            HistoryState(
                isOpen = isOpen,
                sessions = sessions,
                canUndo = undo != null,
                detail = opened,
                topLift = lift,
            )
        }

    fun open() {
        open.value = true
    }

    /** Leaves history. Any undo still on offer expires with the screen it belonged to. */
    fun close() {
        open.value = false
        opened.value = null
        forget()
    }

    /** Opens one past workout in full (US-06b). */
    fun openWorkout(id: SessionId) {
        opened.value = id
    }

    /** Back to the list. */
    fun closeWorkout() {
        opened.value = null
    }

    /**
     * Deletes a past workout and starts the undo window (US-06a).
     *
     * The row is gone from the database before the list re-renders without it, so what is on
     * screen is never ahead of what is stored.
     */
    fun delete(id: SessionId) {
        scope.launch {
            val deleted = sessionDeletion.delete(id) ?: return@launch
            undoable.value = deleted
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(UNDO_WINDOW.toMillis())
                    undoable.value = null
                }
        }
    }

    /** Puts the last deleted workout back, if the window has not closed. */
    fun undo() {
        val deleted = undoable.value ?: return
        forget()
        scope.launch { sessionDeletion.restore(deleted) }
    }

    private fun forget() {
        expiry?.cancel()
        undoable.value = null
    }

    private companion object {
        /** US-04's window, reused so the app's two destructive actions behave alike. */
        val UNDO_WINDOW: Duration = Duration.ofSeconds(5)
    }
}
