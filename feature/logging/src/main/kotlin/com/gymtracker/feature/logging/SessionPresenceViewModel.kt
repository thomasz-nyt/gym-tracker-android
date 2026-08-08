package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Whether a workout is running — the bottom bar's one hide condition (ADR-0024).
 *
 * The NavHost needs this bit, and only this bit, to decide whether to show the bar: "Train,
 * Exercises and History show the bar; an active session does not." It reads Room directly,
 * the same source `ActiveSessionViewModel` does for its own `activeSession`, rather than
 * depending on that ViewModel — the NavHost has no business knowing about set entry, the rest
 * timer, or anything else that screen drives. This is the smallest slice of the signal, lifted
 * to where it is needed.
 */
@HiltViewModel
class SessionPresenceViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
        currentMember: CurrentMember,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val hasActiveSession: StateFlow<Boolean> =
            flow { emit(currentMember.id()) }
                .flatMapLatest { sessions.observeActiveSession(it) }
                .map { it != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
