package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.guided.GuidedPlanStore
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.DetermineUpNextSet
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.EndSession
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionHistory
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.StaleSessionPolicy
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.session.WorkoutDetail
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import com.gymtracker.core.domain.sessionexercise.RemoveExerciseFromSession
import com.gymtracker.core.domain.sessionexercise.RestoreExerciseToSession
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.DeleteSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.set.RestoreSet
import com.gymtracker.core.domain.set.SetInput
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.set.UpdateSet
import com.gymtracker.core.domain.units.WeightUnit
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
    val unit: WeightUnit = WeightUnit.LB,
    val setEntry: SetEntry? = null,
    /** The logged set being corrected, if any (US-04). */
    val setEdit: SetEdit? = null,
    /** Whether the set just deleted can still be put back (US-04). */
    val canUndoSetDelete: Boolean = false,
    /** Time left in the current rest, or null when none is running (US-05). */
    val restRemaining: Duration? = null,
    /** What the rest panel says is coming, or null before anything is logged (ADR-0023). */
    val upNext: UpNextSet? = null,
    /** History, and the workout deleted from it that can still be put back (US-06, US-06a). */
    val history: HistoryState = HistoryState(),
    /** Whether the exercise just removed from the session can still be put back (US-02c). */
    val canUndoRemoval: Boolean = false,
    /** The exercise being walked through, if any (US-05a). */
    val guided: GuidedState = GuidedState(),
)

/**
 * The rest, as ADR-0023 made it: a countdown and the set it is counting down to. They travel
 * together because they are one panel, and because it keeps [ScreenExtras] at five fields.
 */
private data class RestPanel(
    val remaining: Duration?,
    val upNext: UpNextSet?,
)

/**
 * What the session screen itself renders, grouped only because `combine` takes a fixed number
 * of flows. Not a concept — do not let it become one.
 *
 * US-04's editor joined this one rather than becoming a third record, because correcting a set
 * happens *on* the session screen — it is the same kind of thing as [entry], not a side trip.
 * See [SideTrips] for why a third record was the line not to cross.
 */
private data class ScreenExtras(
    val unit: WeightUnit,
    val entry: SetEntry?,
    val rest: RestPanel,
    val edit: SetEdit?,
    val canUndoSetDelete: Boolean,
)

/**
 * The screens reached *from* the session — history, the undo of a removal, the guided flow.
 * Grouped for the same reason, and just as much not a concept.
 *
 * Two grouping records on one screen is the signal ADR-0017 named: this ViewModel now drives
 * the session, history, the workout detail, set entry, removal and the guided flow. The next
 * thing added here should split it rather than add a third.
 */
private data class SideTrips(
    val history: HistoryState,
    val canUndoRemoval: Boolean,
    val guided: GuidedState,
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
        workoutDetail: WorkoutDetail,
        deleteSession: DeleteSession,
        restoreSession: RestoreSession,
        removeExerciseFromSession: RemoveExerciseFromSession,
        restoreExerciseToSession: RestoreExerciseToSession,
        private val determineUpNextSet: DetermineUpNextSet,
        updateSet: UpdateSet,
        deleteSet: DeleteSet,
        restoreSet: RestoreSet,
        private val guidedPlanStore: GuidedPlanStore,
        private val clock: Clock,
    ) : ViewModel() {
        /** The rest between sets lives in its own state holder; see [RestController]. */
        val rest = RestController(restTimer, restTimerStore, viewModelScope)

        /** History and deleting from it live in their own state holder; see [HistoryController]. */
        val history =
            HistoryController(
                history = sessionHistory,
                workoutDetail = workoutDetail,
                deleteSession = deleteSession,
                restoreSession = restoreSession,
                currentMember = currentMember,
                scope = viewModelScope,
            )

        /** Removing an exercise lives in its own state holder; see [ExerciseRemovalController]. */
        val removal =
            ExerciseRemovalController(
                removeExercise = removeExerciseFromSession,
                restoreExercise = restoreExerciseToSession,
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

        /** Correcting a logged set lives in its own state holder; see [SetEditController]. */
        val setEdit =
            SetEditController(
                updateSet = updateSet,
                deleteSet = deleteSet,
                restoreSet = restoreSet,
                unitPreference = unitPreference,
                scope = viewModelScope,
            )

        private val stalePrompt = MutableStateFlow<StaleSessionPrompt?>(null)

        private val member: Flow<UserId> = flow { emit(currentMember.id()) }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val activeSession: Flow<WorkoutSession?> =
            member.flatMapLatest { sessions.observeActiveSession(it) }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val exercisesInOrder: Flow<List<SessionExerciseRow>> =
            combine(activeSession, member) { session, memberId -> session to memberId }
                .flatMapLatest { (session, memberId) ->
                    if (session == null) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            sessionExercises.observeForSession(session.id),
                            catalog.observeRanked(memberId),
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

        /**
         * US-02b: newest first, so the exercise just added is under the thumb rather than at
         * the bottom of a growing list.
         *
         * Reversed here and not in SQL on purpose. [exercisesInOrder] stays in `position`
         * order — the order the workout was performed in, which is what history (US-06b)
         * reads, what US-06a's restore promises back unchanged, and what guided mode walks
         * through to find the next exercise (US-05a).
         */
        private val exercises: Flow<List<SessionExerciseRow>> = exercisesInOrder.map { it.asReversed() }

        /**
         * Walking through one exercise lives in its own state holder; see [GuidedController].
         *
         * Declared after [exercisesInOrder] because it reads it, and given the in-order flow
         * rather than the display one so "the next exercise" means the next one performed.
         */
        val guided =
            GuidedController(
                performSet = { sessionExerciseId, input ->
                    // Write first, rest second, and only if the write returned — the same
                    // ordering SetEntryController holds to for the manual path (US-05).
                    logSets(sessionExerciseId = sessionExerciseId, input = input, sets = 1)
                    rest.startAfterSet()
                },
                unitPreference = unitPreference,
                planStore = guidedPlanStore,
                exercises = exercisesInOrder,
                clock = clock,
                scope = viewModelScope,
            )

        /**
         * What follows the running rest (ADR-0023), recomputed from the database whenever the
         * session's sets change. `exercises` already re-emits on every write, so it is the
         * trigger; nothing about "up next" is remembered between emissions.
         */
        private val restPanel: Flow<RestPanel> =
            combine(
                rest.remaining(),
                activeSession,
                member,
                unitPreference.observe(),
                exercises,
            ) { remaining, session, memberId, unit, _ ->
                RestPanel(
                    remaining = remaining,
                    upNext = session?.let { determineUpNextSet(it.id, memberId, unit) },
                )
            }

        val uiState: StateFlow<SessionUiState> =
            combine(
                activeSession,
                stalePrompt,
                exercises,
                combine(
                    unitPreference.observe(),
                    setEntry.entry,
                    restPanel,
                    setEdit.edit,
                    setEdit.canUndo,
                ) { unit, entry, panel, edit, canUndoSetDelete ->
                    ScreenExtras(unit, entry, panel, edit, canUndoSetDelete)
                },
                combine(history.state, removal.canUndo, guided.state) { past, canUndoRemoval, guidedState ->
                    SideTrips(past, canUndoRemoval, guidedState)
                },
            ) { session, prompt, inSession, extras, sideTrips ->
                SessionUiState(
                    isLoading = false,
                    activeSession = session,
                    stalePrompt = prompt,
                    exercises = inSession,
                    unit = extras.unit,
                    setEntry = extras.entry,
                    setEdit = extras.edit,
                    canUndoSetDelete = extras.canUndoSetDelete,
                    restRemaining = extras.rest.remaining,
                    upNext = extras.rest.upNext,
                    history = sideTrips.history,
                    canUndoRemoval = sideTrips.canUndoRemoval,
                    guided = sideTrips.guided,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState())

        init {
            checkForAbandonedSession()
        }

        /**
         * Logs the set the rest panel is offering, without opening the sheet (ADR-0023).
         *
         * One tap, which is under US-03's two-tap ceiling rather than at it. The rest then
         * restarts exactly as it does after any other set, so successive sets can be logged
         * from the panel alone. [next] is passed in rather than read back out of the state so
         * that what is written is unambiguously what was on screen when the thumb landed.
         */
        fun onLogNextSet(next: UpNextSet) {
            viewModelScope.launch {
                logSets(
                    sessionExerciseId = next.sessionExerciseId,
                    input =
                        SetInput(
                            weight = next.prefill.weight,
                            unit = unitPreference.current(),
                            reps = next.prefill.reps,
                            rpe = null,
                        ),
                    sets = 1,
                )
                rest.startAfterSet()
            }
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

        /**
         * Appends an exercise the member picked on the browse screen (US-02, US-12).
         *
         * Browse is a navigation destination of its own now, so it hands an id back rather
         * than this screen owning a search overlay. Adding the same exercise twice is allowed
         * — US-02 says so, and each appearance is its own row, which is also what lets one
         * visit to browse add the same movement more than once (US-02a).
         */
        fun onExerciseChosen(exerciseId: ExerciseId) = onExercisesChosen(listOf(exerciseId))

        /**
         * Appends everything one visit to the browse screen picked, in pick order (US-02a).
         *
         * **One coroutine, appended in sequence**, which matters more than it looks:
         * `AddExerciseToSession` takes its position from `MAX(position) + 1`, so appending
         * concurrently would let two exercises read the same maximum and land on the same
         * position. Looping over [onExerciseChosen] instead of this would do exactly that.
         */
        fun onExercisesChosen(exerciseIds: List<ExerciseId>) {
            if (exerciseIds.isEmpty()) return

            viewModelScope.launch {
                // Read the session from the repository rather than uiState: uiState is shared
                // WhileSubscribed, so its value is the initial placeholder whenever the screen
                // is not currently collecting. The database is the source of truth (§2).
                val session = sessions.findActiveSession(currentMember.id()) ?: return@launch
                exerciseIds.forEach { addExerciseToSession(session.id, it) }
            }
        }

        /**
         * Opens the guided start dialog for an exercise (US-05a).
         *
         * The prefill is looked up here rather than inside [GuidedController] so US-03's
         * prefilling rule stays in one place and the controller stays small enough to test.
         */
        fun onStartExercise(row: SessionExerciseRow) {
            viewModelScope.launch { guided.start(row, prefillFor(row)) }
        }

        /** Moves from the summary of one exercise to the start of the next (US-05a). */
        fun onStartNextExercise(row: SessionExerciseRow) {
            viewModelScope.launch { guided.startNext(row, prefillFor(row)) }
        }

        private suspend fun prefillFor(row: SessionExerciseRow) =
            prefillFromLastSet(
                row.sessionExercise.exerciseId,
                currentMember.id(),
                unitPreference.current(),
            )

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
        }
    }
