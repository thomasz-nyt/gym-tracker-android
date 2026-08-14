package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.routine.NextRoutineToTrain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The one thing Train home needs beyond [ActiveSessionViewModel]'s own state (US-36, ADR-0030):
 * which routine, if any, to offer starting.
 *
 * A dedicated ViewModel rather than one more field on [ActiveSessionViewModel], for the same
 * reason [SessionPresenceViewModel] is its own class and not a read off that one: this is the
 * smallest slice of the signal, lifted to where it is needed, and it does not belong in the
 * `combine` chain that class's own doc comment explains is deliberately one group already.
 *
 * Read once per composition rather than observed continuously — [NextRoutineToTrain]'s ranking
 * only matters while `NoSession` is on screen, and it recomputes (via [refresh]) whenever that
 * screen reappears, which is a cheap read against Room either way.
 */
@HiltViewModel
class TrainHomeViewModel
    @Inject
    constructor(
        private val nextRoutineToTrain: NextRoutineToTrain,
        private val currentMember: CurrentMember,
    ) : ViewModel() {
        private val state = MutableStateFlow<Routine?>(null)

        /** The routine Train home should offer starting, or null (no routines yet). */
        val nextRoutine: StateFlow<Routine?> = state

        init {
            refresh()
        }

        /** Re-reads the ranking — called when `NoSession` (re)appears, after a start or a delete. */
        fun refresh() {
            viewModelScope.launch { state.value = nextRoutineToTrain(currentMember.id()) }
        }
    }
