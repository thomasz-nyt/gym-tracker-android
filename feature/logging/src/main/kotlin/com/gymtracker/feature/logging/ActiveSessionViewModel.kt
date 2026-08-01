package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.EndSession
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionHistory
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.StaleSessionPolicy
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** An exercise in the session, paired with its catalog entry for display. */
data class SessionExerciseRow(
    val sessionExercise: SessionExercise,
    val exercise: Exercise?,
    val sets: List<ExerciseSet> = emptyList(),
)

/**
 * Everything the active-session screen renders. One immutable state object per screen,
 * per `specs/tech-stack.md` § Architecture.
 */
data class SessionUiState(
    val isLoading: Boolean = true,
    val activeSession: WorkoutSession? = null,
    val stalePrompt: StaleSessionPrompt? = null,
    val exercises: List<SessionExerciseRow> = emptyList(),
    val isSearching: Boolean = false,
    val query: String = "",
    val results: List<Exercise> = emptyList(),
    val unit: WeightUnit = WeightUnit.LB,
    val setEntry: SetEntry? = null,
    /** Time left in the current rest, or null when none is running (US-05). */
    val restRemaining: Duration? = null,
    /** History, and the workout deleted from it that can still be put back (US-06, US-06a). */
    val history: HistoryState = HistoryState(),
)

/**
 * The tail of [SessionUiState], grouped only because `combine` takes a fixed number of flows.
 * Not a concept — do not let it become one.
 */
private data class ScreenExtras(
    val unit: WeightUnit,
    val entry: SetEntry?,
    val rest: Duration?,
    val history: HistoryState,
)

/** US-01 and US-02: run a session, and add exercises to it from the catalog. */
@HiltViewModel
class ActiveSessionViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val sessionExercises: SessionExerciseRepository,
        private val sets: SetRepository,
        private val logSets: LogSets,
        private val restTimer: RestTimer,
        private val restTimerStore: com.gymtracker.core.domain.rest.RestTimerStore,
        private val prefillFromLastSet: PrefillFromLastSet,
        private val unitPreference: UnitPreference,
        private val catalog: ExerciseCatalog,
        private val currentMember: CurrentMember,
        private val startSession: StartSession,
        private val addExerciseToSession: AddExerciseToSession,
        private val endSession: EndSession,
        sessionHistory: SessionHistory,
        deleteSession: DeleteSession,
        restoreSession: RestoreSession,
        private val clock: Clock,
    ) : ViewModel() {
        /** The rest between sets lives in its own state holder; see [RestController]. */
        val rest = RestController(restTimer, restTimerStore, viewModelScope)

        /** History and deleting from it live in their own state holder; see [HistoryController]. */
        val history =
            HistoryController(
                history = sessionHistory,
                deleteSession = deleteSession,
                restoreSession = restoreSession,
                currentMember = currentMember,
                scope = viewModelScope,
            )

        /** Set entry lives in its own state holder; see [SetEntryController]. */
        val setEntry =
            SetEntryController(
                logSets = logSets,
                onSetLogged = rest::startAfterSet,
                prefillFromLastSet = prefillFromLastSet,
                unitPreference = unitPreference,
                currentMember = currentMember,
                scope = viewModelScope,
            )

        private val stalePrompt = MutableStateFlow<StaleSessionPrompt?>(null)
        private val searching = MutableStateFlow(false)
        private val query = MutableStateFlow("")

        private val member: Flow<UserId> = flow { emit(currentMember.id()) }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val activeSession: Flow<WorkoutSession?> =
            member.flatMapLatest { sessions.observeActiveSession(it) }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val exercises: Flow<List<SessionExerciseRow>> =
            combine(activeSession, member) { session, memberId -> session to memberId }
                .flatMapLatest { (session, memberId) ->
                    if (session == null) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            sessionExercises.observeForSession(session.id),
                            catalog.search("", memberId),
                        ) { inSession, allExercises ->
                            val byId = allExercises.associateBy(Exercise::id)
                            inSession.map { SessionExerciseRow(it, byId[it.exerciseId]) }
                        }.flatMapLatest { rows ->
                            if (rows.isEmpty()) {
                                flowOf(emptyList())
                            } else {
                                combine(
                                    rows.map { row ->
                                        sets.observeForSessionExercise(row.sessionExercise.id)
                                    },
                                ) { perRow ->
                                    rows.mapIndexed { index, row -> row.copy(sets = perRow[index]) }
                                }
                            }
                        }
                    }
                }

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        private val results: Flow<List<Exercise>> =
            combine(
                // No delay on the empty query, so opening search shows the catalog at once;
                // the debounce is only there to stop re-querying on every keystroke.
                query.debounce { text -> if (text.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS },
                member,
            ) { text, memberId -> text to memberId }
                .flatMapLatest { (text, memberId) -> catalog.search(text, memberId) }

        val uiState: StateFlow<SessionUiState> =
            combine(
                activeSession,
                stalePrompt,
                exercises,
                combine(searching, query, results) { isSearching, text, found ->
                    Triple(isSearching, text, found)
                },
                combine(
                    unitPreference.observe(),
                    setEntry.entry,
                    rest.remaining(),
                    history.state,
                ) { unit, entry, rest, past -> ScreenExtras(unit, entry, rest, past) },
            ) { session, prompt, inSession, (isSearching, text, found), extras ->
                SessionUiState(
                    isLoading = false,
                    activeSession = session,
                    stalePrompt = prompt,
                    exercises = inSession,
                    isSearching = isSearching,
                    query = text,
                    results = found,
                    unit = extras.unit,
                    setEntry = extras.entry,
                    restRemaining = extras.rest,
                    history = extras.history,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState())

        init {
            checkForAbandonedSession()
        }

        /** Starts a session, or does nothing visible if one is already running (US-01). */

        fun onStartWorkout() {
            viewModelScope.launch { startSession(currentMember.id()) }
        }

        /**
         * Ends the session and returns the member to home (US-06).
         *
         * The session is read from the repository rather than from `uiState`, for the same
         * reason [onExerciseChosen] does: the database is the source of truth (§2).
         */
        fun onFinishWorkout() {
            viewModelScope.launch {
                val session = sessions.findActiveSession(currentMember.id()) ?: return@launch
                endSession(session.id)
            }
        }

        /** Opens the catalog search (US-02). */
        fun onAddExerciseClicked() {
            query.value = ""
            searching.value = true
        }

        fun onSearchDismissed() {
            searching.value = false
        }

        fun onQueryChanged(text: String) {
            query.value = text
        }

        /**
         * Appends the chosen exercise to the active session and closes the search. Adding the
         * same exercise twice is allowed — US-02 says so, and each appearance is its own row.
         */
        fun onExerciseChosen(exerciseId: ExerciseId) {
            viewModelScope.launch {
                // Read the session from the repository rather than uiState: uiState is shared
                // WhileSubscribed, so its value is the initial placeholder whenever the screen
                // is not currently collecting. The database is the source of truth (§2).
                val session = sessions.findActiveSession(currentMember.id()) ?: return@launch
                addExerciseToSession(session.id, exerciseId)
                searching.value = false
            }
        }

        /**
         * Applies the member's answer to the abandoned-session prompt. [StaleSessionPrompt]
         * already carries the only honest outcome for that session, so there is no choice of
         * end time to make here.
         */
        fun onResolveStale(prompt: StaleSessionPrompt) {
            viewModelScope.launch {
                when (prompt) {
                    is StaleSessionPrompt.Finish -> sessions.endSession(prompt.session.id, prompt.endedAt)
                    is StaleSessionPrompt.Discard -> sessions.deleteSession(prompt.session.id)
                }
                stalePrompt.value = null
            }
        }

        /**
         * Evaluated once, on open, exactly as US-01 words it — not continuously, or the prompt
         * would reappear while the member is looking at it.
         */
        private fun checkForAbandonedSession() {
            viewModelScope.launch {
                val active = sessions.findActiveSession(currentMember.id()) ?: return@launch
                stalePrompt.value =
                    StaleSessionPolicy.evaluate(
                        session = active,
                        // Real last activity now that sets exist. Passing null here — as this
                        // did while US-03 was unbuilt — measured staleness from the session's
                        // start, so a long workout with a recent set looked abandoned.
                        lastSetAt = sets.lastSetAtInSession(active.id),
                        now = clock.instant(),
                    )
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val TICK_MILLIS = 1_000L

            /** Enough to stop re-querying 873 rows on every keystroke, short enough to feel instant. */
            const val SEARCH_DEBOUNCE_MILLIS = 150L
        }
    }
