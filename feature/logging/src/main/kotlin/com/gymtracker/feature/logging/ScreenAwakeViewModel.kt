package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.member.KeepScreenOnPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Whether the screen should stay on while a workout runs (US-59).
 *
 * A ViewModel of its own for the reason [WarmUpViewModel] and [TrainHomeViewModel] are: it is
 * the smallest slice of one signal, lifted to where the route needs it, rather than a seventh
 * branch on [ActiveSessionViewModel]'s `combine`. It reads a device preference and nothing about
 * the session; whether a workout is actually running is the route's half of the decision.
 */
@HiltViewModel
class ScreenAwakeViewModel
    @Inject
    constructor(
        preference: KeepScreenOnPreference,
    ) : ViewModel() {
        /** The member's setting, live. Starts as on — the default — until DataStore has answered. */
        val keepScreenOn: StateFlow<Boolean> =
            preference
                .observe()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
