package com.gymtracker.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.health.LiveHeartRate
import com.gymtracker.core.domain.health.LiveHeartRateSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The app-wide live heart rate signal (US-47, ADR-0039) — the smallest slice
 * [GymTrackerNavHost] needs to render [LiveHeartRateChip], the same "lift only what is needed"
 * shape [com.gymtracker.feature.logging.SessionPresenceViewModel] uses for the bottom bar.
 *
 * Deliberately not gated on whether a session is running: the whole point (US-47 — "not just
 * the session screen") is that this reads the same whether the member is mid-workout, browsing
 * the catalog, or in Settings.
 */
@HiltViewModel
class LiveHeartRateViewModel
    @Inject
    constructor(
        source: LiveHeartRateSource,
    ) : ViewModel() {
        val state: StateFlow<LiveHeartRate> =
            source
                .observe()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LiveHeartRate.Unavailable)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
