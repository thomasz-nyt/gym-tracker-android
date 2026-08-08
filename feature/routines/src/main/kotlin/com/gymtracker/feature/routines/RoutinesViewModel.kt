package com.gymtracker.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.routine.CreateRoutine
import com.gymtracker.core.domain.routine.DeleteRoutine
import com.gymtracker.core.domain.routine.RoutineItemRepository
import com.gymtracker.core.domain.routine.RoutineRepository
import com.gymtracker.core.domain.routine.StartSessionFromRoutine
import com.gymtracker.core.domain.session.StartSessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A routine in the list, with how many movements it holds. */
data class RoutineRow(
    val routine: Routine,
    val movements: Int,
)

/** Everything the routines list renders. */
data class RoutinesUiState(
    val isLoading: Boolean = true,
    val routines: List<RoutineRow> = emptyList(),
)

/**
 * What happened when the member asked to start a routine.
 *
 * Reported rather than acted on here, because the two cases need different things from the
 * screen: one navigates, the other explains.
 */
sealed interface RoutineStart {
    /** A workout began with the routine's movements in it. Go to it. */
    data object Started : RoutineStart

    /**
     * A workout was already running, so nothing was copied (US-01, ADR-0020).
     *
     * The screen says so rather than silently adding six movements to what is on screen.
     */
    data object AlreadyRunning : RoutineStart
}

/** US-29: the member's routines, and starting one. */
@HiltViewModel
class RoutinesViewModel
    @Inject
    constructor(
        routines: RoutineRepository,
        private val items: RoutineItemRepository,
        private val currentMember: CurrentMember,
        private val createRoutine: CreateRoutine,
        private val deleteRoutine: DeleteRoutine,
        private val startSessionFromRoutine: StartSessionFromRoutine,
    ) : ViewModel() {
        private val member: Flow<com.gymtracker.core.domain.model.UserId> = flow { emit(currentMember.id()) }

        private val started = MutableStateFlow<RoutineStart?>(null)

        /** Non-null exactly once per start, until [onStartHandled]. */
        val startOutcome: StateFlow<RoutineStart?> = started

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<RoutinesUiState> =
            member
                .flatMapLatest { routines.observeRoutines(it) }
                .flatMapLatest { list ->
                    if (list.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        // One flow per routine, so a movement added in the editor updates the
                        // count here without this screen knowing the editor exists.
                        combine(list.map { routine -> items.observeItems(routine.id) }) { perRoutine ->
                            list.mapIndexed { index, routine -> RoutineRow(routine, perRoutine[index].size) }
                        }
                    }
                }.map { RoutinesUiState(isLoading = false, routines = it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RoutinesUiState())

        /** Creates an empty routine. The editor is where it gains movements. */
        fun onCreateRoutine(name: String) {
            viewModelScope.launch { createRoutine(currentMember.id(), name) }
        }

        /** Deletes a routine and its movements. No session is affected (ADR-0020). */
        fun onDeleteRoutine(id: RoutineId) {
            viewModelScope.launch { deleteRoutine(id) }
        }

        /** Starts a workout from a routine, reporting the outcome through [startOutcome]. */
        fun onStartRoutine(id: RoutineId) {
            viewModelScope.launch {
                started.value =
                    when (startSessionFromRoutine(id, currentMember.id())) {
                        is StartSessionResult.Started -> RoutineStart.Started
                        is StartSessionResult.Resumed -> RoutineStart.AlreadyRunning
                        null -> null
                    }
            }
        }

        /** Acknowledges the outcome, so a recomposition cannot navigate a second time. */
        fun onStartHandled() {
            started.value = null
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
