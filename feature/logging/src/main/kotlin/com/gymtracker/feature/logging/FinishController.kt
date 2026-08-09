package com.gymtracker.feature.logging

import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.progress.PersonalRecordsAchievedIn
import com.gymtracker.core.domain.session.EndSession
import com.gymtracker.core.domain.session.EndSessionResult
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.WorkoutDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Ending a session and showing what it added up to (US-31), split out of
 * `ActiveSessionViewModel` for the same reason the rest, set entry, and exercise removal already
 * were — the screen's own job is running a session, not every state machine layered onto it.
 */
class FinishController(
    private val sessions: SessionRepository,
    private val currentMember: CurrentMember,
    private val endSession: EndSession,
    private val workoutDetail: WorkoutDetail,
    private val personalRecordsAchievedIn: PersonalRecordsAchievedIn,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<FinishFlow?>(null)

    /** What is showing over the just-finished session, if anything. See [FinishFlow]. */
    val flow: StateFlow<FinishFlow?> = state

    /**
     * Ends the session and shows what it added up to.
     *
     * The confirm dialog above this call is unchanged — this only replaces what happens once
     * the member has already said yes. [state] is set to [FinishFlow.InProgress] *before*
     * [endSession] runs, not after: that call is what makes the active session go null, and
     * [SessionBody] checks `finish` first, so ordering it this way is what stops home flashing
     * on screen for a frame before the summary is ready.
     *
     * A session with no sets is [EndSessionResult.Discarded] rather than ended (US-06) — there
     * is nothing to summarize, so [state] is left null and the screen falls through to home
     * exactly as it did before this story.
     */
    fun confirm() {
        scope.launch {
            val member = currentMember.id()
            val session = sessions.findActiveSession(member) ?: return@launch

            state.value = FinishFlow.InProgress
            when (endSession(session.id)) {
                is EndSessionResult.Ended -> {
                    val detail = workoutDetail(session.id, member).first()
                    state.value = detail?.let { FinishFlow.Ready(it, personalRecordsAchievedIn(it, member)) }
                }
                EndSessionResult.Discarded -> state.value = null
            }
        }
    }

    /** Returns to the session list once the member has seen what the workout added up to. */
    fun dismiss() {
        state.value = null
    }
}
