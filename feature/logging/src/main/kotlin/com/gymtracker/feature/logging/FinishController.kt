package com.gymtracker.feature.logging

import com.gymtracker.core.domain.health.RecordSessionMetrics
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
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
 *
 * US-22's [recordSessionMetrics] is a seventh constructor parameter, one past detekt's default
 * `LongParameterList` threshold of 6. Suppressed rather than folded: each of the seven is a
 * genuinely distinct collaborator this class composes to do its one job (end the session, read
 * what it added up to, read what health said about it), none redundant with another.
 */
@Suppress("LongParameterList")
class FinishController(
    private val sessions: SessionRepository,
    private val currentMember: CurrentMember,
    private val endSession: EndSession,
    private val workoutDetail: WorkoutDetail,
    private val personalRecordsAchievedIn: PersonalRecordsAchievedIn,
    private val recordSessionMetrics: RecordSessionMetrics,
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
     *
     * US-22's health read happens after the summary is already showing, in the same coroutine
     * rather than a blocking step before it: `confirm()` itself returns the instant [launch]
     * schedules this, so a slow (or merely present) health read never delays the finish tap
     * (constitution §2.2). [showSummary] runs a second time once it completes, so a member who
     * opted in sees the numbers land rather than a summary that is permanently missing them —
     * but only if [state] still holds *this* session's summary, which is what stops a read that
     * outlives [dismiss] from reopening it.
     */
    fun confirm() {
        scope.launch {
            val member = currentMember.id()
            val session = sessions.findActiveSession(member) ?: return@launch

            state.value = FinishFlow.InProgress
            when (val result = endSession(session.id)) {
                is EndSessionResult.Ended -> {
                    showSummary(session.id, member)
                    recordSessionMetrics(session.id, session.startedAt..result.endedAt)
                    if (isStillShowing(session.id)) showSummary(session.id, member)
                }
                EndSessionResult.Discarded -> state.value = null
            }
        }
    }

    private suspend fun showSummary(
        id: SessionId,
        member: UserId,
    ) {
        val detail = workoutDetail(id, member).first()
        state.value = detail?.let { FinishFlow.Ready(it, personalRecordsAchievedIn(it, member)) }
    }

    private fun isStillShowing(id: SessionId): Boolean {
        val ready = state.value as? FinishFlow.Ready ?: return false
        return ready.detail.summary.session.id == id
    }

    /** Returns to the session list once the member has seen what the workout added up to. */
    fun dismiss() {
        state.value = null
    }
}
