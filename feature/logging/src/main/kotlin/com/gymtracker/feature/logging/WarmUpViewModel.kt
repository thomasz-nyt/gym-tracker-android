package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.warmup.WarmUpTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

/**
 * The warm-up before the lifting starts (US-28, ADR-0021).
 *
 * **A ViewModel of its own, deliberately.** The warm-up is drawn on the session screen but it
 * is not part of the session: it has no row, contributes nothing to duration, and never
 * reaches history. Folding it into [SessionUiState] would put it inside the object the
 * session is rendered from and invite exactly the coupling constitution §1 forbids. Keeping
 * it here means it *cannot* write a set — it has no repository to write one with — which is
 * the same trick ADR-0024 used when history left [ActiveSessionViewModel].
 *
 * It also keeps that ViewModel's `combine` from growing a sixth branch; ADR-0017 already
 * warned that the next thing added there should split rather than pile on.
 */
@HiltViewModel
class WarmUpViewModel
    @Inject
    constructor(
        private val warmUpTimer: WarmUpTimer,
    ) : ViewModel() {
        /**
         * How long the warm-up has run, or null when none is running.
         *
         * Null and [Duration.ZERO] mean different things here: null is "no warm-up", zero is
         * "a warm-up that just began". The screen renders the second and not the first.
         */
        val elapsed: StateFlow<Duration?> =
            ticking().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        /** Starts the warm-up, or leaves a running one alone (US-28: starting again never resets). */
        fun onStartWarmUp() {
            viewModelScope.launch { warmUpTimer.start() }
        }

        /** Ends the warm-up. Nothing is recorded, so there is nothing to save. */
        fun onStopWarmUp() {
            viewModelScope.launch { warmUpTimer.stop() }
        }

        /**
         * Elapsed time, re-read every second so the display moves.
         *
         * The tick is only a redraw signal — every value comes from the stored start instant
         * against the clock, so a missed tick cannot make the warm-up drift. It runs only
         * while a warm-up is actually running: an idle screen schedules no wakeups, which is
         * the difference from [RestController]'s unconditional loop.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun ticking(): Flow<Duration?> =
            warmUpTimer
                .elapsed()
                .flatMapLatest { running ->
                    if (running == null) {
                        flowOf(null)
                    } else {
                        flow<Duration?> {
                            while (true) {
                                emitAll(warmUpTimer.elapsed().take(1))
                                delay(TICK_MILLIS)
                            }
                        }
                    }
                }.distinctUntilChanged()

        private companion object {
            const val TICK_MILLIS = 1_000L
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
