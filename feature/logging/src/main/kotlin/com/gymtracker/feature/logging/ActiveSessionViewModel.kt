package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.guided.GuidedPlanStore
import com.gymtracker.core.domain.health.RecordSessionMetrics
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.progress.DetectPersonalRecord
import com.gymtracker.core.domain.progress.PersonalRecord
import com.gymtracker.core.domain.progress.PersonalRecordsAchievedIn
import com.gymtracker.core.domain.rest.DetermineUpNextSet
import com.gymtracker.core.domain.rest.LogUpNextSet
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.routine.StartSessionFromRoutine
import com.gymtracker.core.domain.session.EndSession
import com.gymtracker.core.domain.session.SessionDetail
import com.gymtracker.core.domain.session.SessionProgress
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
import com.gymtracker.core.domain.set.ResolveSetPrefill
import com.gymtracker.core.domain.set.RestoreSet
import com.gymtracker.core.domain.set.SetPrefill
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /**
     * What the current rest was configured for when it started — a progress bar's denominator.
     * Null exactly when [restRemaining] is null. Deliberately not the live
     * [com.gymtracker.core.domain.rest.RestTimerStore.defaultRest]: see that property's own doc
     * for why a rest already running must not visibly retime when the member changes the
     * default in Settings (US-42).
     */
    val restTotal: Duration? = null,
    /** What the rest panel says is coming, or null before anything is logged (ADR-0023). */
    val upNext: UpNextSet? = null,
    /**
     * What the movement list's one-tap log button will write (ADR-0029, US-35) — the current
     * movement's next set, independent of rest. Unlike [upNext], this is not null just because
     * nothing has been logged in the session yet; it is null only when there is no movement
     * left to log ([SessionProgress.current] is null) or no prefill exists for it yet (a brand
     * new exercise with no history and no target — in which case there is nothing sensible to
     * write with one tap, and the screen falls back to `Add set` alone).
     */
    val nextLoggableSet: UpNextSet? = null,
    /** Whether the exercise just removed from the session can still be put back (US-02c). */
    val canUndoRemoval: Boolean = false,
    /** Whether the set just logged in one tap can still be taken back (US-35). */
    val canUndoOneTapLog: Boolean = false,
    /** The exercise being walked through, if any (US-05a). */
    val guided: GuidedState = GuidedState(),
    /** What is showing over the just-finished session, if anything (US-31). */
    val finish: FinishFlow? = null,
    /**
     * How many movements are done, which is current, and which are still to come (ADR-0029);
     * null before the first emission the same way [activeSession] is.
     */
    val progress: SessionProgress? = null,
    /**
     * Which row [SessionPlan][com.gymtracker.feature.logging.session.SessionPlan] has open
     * (ADR-0029) — the last movement with any set logged, or the first movement if none do yet.
     * Deliberately not derived from [progress]`.current` on the UI side; see where this is
     * computed in the ViewModel for why the two answer different questions.
     */
    val openSessionExerciseId: SessionExerciseId? = null,
    /**
     * The record the set just logged set, if any (US-18) — shown inline in place of the rest
     * banner for the rest cycle it started, then cleared. Null the rest of the time, which is
     * almost always: US-18's own rule is that most sets are not records.
     */
    val justSetRecord: PersonalRecord? = null,
)

/**
 * What is on screen between confirming "Finish workout" and returning to the session list
 * (US-31). [InProgress] exists to close a race, not to be a loading spinner in its own right:
 * it is set *before* [EndSession] is called, synchronously, so the session's `active` flow going
 * null can never be observed with nothing yet queued to replace it — which would otherwise flash
 * home for a frame before the summary is ready.
 */
sealed interface FinishFlow {
    data object InProgress : FinishFlow

    /**
     * @property records every personal record the session set, already deduplicated to the best
     *   per (exercise, reps) by [PersonalRecordsAchievedIn] — never every intermediate one.
     */
    data class Ready(
        val detail: SessionDetail,
        val records: List<PersonalRecord>,
    ) : FinishFlow
}

/**
 * Everything that is a function of [WorkoutSession] plus member/unit: the session itself, the
 * exercises in display order, the rest countdown and what it counts down to (ADR-0023), how far
 * through the plan the session is, and what one tap would log next (ADR-0029, US-35).
 *
 * One `combine` group rather than several, and that is load-bearing, not tidiness. `activeSession`,
 * `member` and `exercisesInOrder` are cold flows (`flatMapLatest` / `flow {}`), so every
 * independent `combine(activeSession, ...)` elsewhere in this class re-subscribes them from
 * scratch — and `combine` fires its lambda on *every* input emission, not once per logical
 * upstream change. Two independent subscriptions to the same cold upstream can each deliver
 * their own emission for one write to the underlying repository, and the screen would observe a
 * genuinely transient state in between: the session already updated, [progress] not yet
 * recomputed for it. That is exactly what several `ActiveSessionViewModelTest` cases caught —
 * not a duplicate value (a `distinctUntilChanged` would have caught that), but two *different*
 * [SessionUiState] values a few microseconds apart, only the first of which the test's exact
 * `awaitItem()` sequence expected. Reading [session] and [exercises] here rather than as
 * separate outer `combine` arguments is what makes "the session changed" and "progress changed"
 * arrive as one atomic update instead of two.
 */
private data class SessionData(
    val session: WorkoutSession?,
    val exercises: List<SessionExerciseRow>,
    val upNext: UpNextSet?,
    val progress: SessionProgress?,
    /** Which row is open — see the computation site for why this is not [SessionProgress.current]. */
    val openSessionExerciseId: SessionExerciseId?,
    val nextLoggableSet: UpNextSet?,
)

/**
 * [SessionData] plus the rest countdown (ADR-0023). Kept as a second, outer `combine` — not
 * folded into [SessionData]'s own — because [RestController.remaining] ticks once a second for
 * as long as any collector holds it, and [SessionData]'s own computation is not free: it reads
 * [SessionProgress], and calls [PrefillFromLastSet] and [SetRepository.lastSetOfBefore], both
 * suspend Room queries. Combining those into the same lambda `remaining` feeds would re-run all
 * three every single second, forever, for no reason — the countdown ticking is not a reason for
 * the plan or the one-tap prefill to be recomputed. Splitting them costs nothing structurally
 * (this combine still fires from exactly one upstream subscription to each cold flow, so
 * [SessionData]'s own class doc about atomic updates still holds) and turns a per-second Room
 * query pair into what it should be: work done only when the session actually changes.
 */
private data class SessionComputed(
    val data: SessionData,
    val remaining: Duration?,
    /** What [remaining] started counting down from — a progress bar's denominator (ADR-0029). */
    val total: Duration?,
) {
    // Pass-through accessors so call sites read `computed.progress` etc. rather than
    // `computed.data.progress` — SessionData's split from this class is an internal
    // recomputation-cost detail, not something the rest of the ViewModel should have to know.
    val session get() = data.session
    val exercises get() = data.exercises
    val upNext get() = data.upNext
    val progress get() = data.progress
    val openSessionExerciseId get() = data.openSessionExerciseId
    val nextLoggableSet get() = data.nextLoggableSet
}

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
    val edit: SetEdit?,
    val canUndoSetDelete: Boolean,
)

/**
 * The undo of a removal, and the guided flow — grouped only because `combine` needs a fixed
 * shape, and just as much not a concept.
 *
 * This used to also carry history and the workout detail. ADR-0017 named this class "the second
 * grouping record on this screen" and said the next thing added should split the ViewModel
 * rather than pile on; ADR-0024 is that split — [HistoryViewModel] now owns both.
 */
private data class SideTrips(
    val canUndoRemoval: Boolean,
    val guided: GuidedState,
    val canUndoOneTapLog: Boolean,
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
        private val startSessionFromRoutine: StartSessionFromRoutine,
        private val addExerciseToSession: AddExerciseToSession,
        endSession: EndSession,
        workoutDetail: WorkoutDetail,
        personalRecordsAchievedIn: PersonalRecordsAchievedIn,
        recordSessionMetrics: RecordSessionMetrics,
        private val detectPersonalRecord: DetectPersonalRecord,
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

        /** Removing an exercise lives in its own state holder; see [ExerciseRemovalController]. */
        val removal =
            ExerciseRemovalController(
                removeExercise = removeExerciseFromSession,
                restoreExercise = restoreExerciseToSession,
                scope = viewModelScope,
            )

        /** Choosing which exercise is open lives in its own state holder; see [ExerciseSelectionController]. */
        val selection = ExerciseSelectionController()

        /** Set entry lives in its own state holder; see [SetEntryController]. */
        val setEntry =
            SetEntryController(
                logSets = logSets,
                onSetLogged = { sessionExerciseId, logged ->
                    rest.startAfterSet()
                    justSetRecord.value =
                        resolveJustSetRecord(
                            sessionExerciseId,
                            logged,
                            sessionExercises,
                            detectPersonalRecord,
                            currentMember,
                        )
                },
                sets = sets,
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

        /**
         * `LogUpNextSet` is built from dependencies this class already holds rather than injected
         * as one more constructor parameter. The point of sharing it with the notification's
         * `LOG SET` is that the *logic* lives in one class (US-54); which of two identical
         * instances this screen uses is not part of that, and a new parameter would have churned
         * ten test files that have nothing to do with this story. The controller around it owns
         * the undo window (US-35), the same split [setEdit] and [removal] already are — and like
         * them it is exposed for the route to wire `undo` directly. An undo also drops the record
         * banner announced for that set: a record set by a row that no longer exists is not one.
         */
        val oneTap =
            OneTapLogController(
                logUpNextSet = LogUpNextSet(logSets, restTimer, unitPreference),
                deleteSet = deleteSet,
                restTimer = restTimer,
                scope = viewModelScope,
                onUndone = { justSetRecord.value = null },
            )

        /** Ending the session lives in its own state holder; see [FinishController]. */
        val finish =
            FinishController(
                sessions = sessions,
                currentMember = currentMember,
                endSession = endSession,
                workoutDetail = workoutDetail,
                personalRecordsAchievedIn = personalRecordsAchievedIn,
                recordSessionMetrics = recordSessionMetrics,
                scope = viewModelScope,
            )

        private val stalePrompt = MutableStateFlow<StaleSessionPrompt?>(null)

        /**
         * US-18's inline PR moment. Overwritten (to the new result, record or null) on every set
         * logged, from both [setEntry]'s `onSetLogged` callback ([resolveJustSetRecord]) and
         * [onLogNextSet] — so it reads as "the record from the set just logged," not an
         * accumulating history, and persists until the next set is logged rather than being tied
         * to the rest cycle it started. `Redesign.dc.html`'s `2a PR moment` frame ties it to the
         * rest cycle instead; that needs a signal this class does not have (`RestController.skip`
         * is called directly from the route, bypassing this ViewModel — see
         * `ActiveSessionRoute.onSkipRest`), so the simpler lifecycle is a deliberate
         * simplification, not an oversight.
         */
        private val justSetRecord = MutableStateFlow<PersonalRecord?>(null)

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

        // US-02b's newest-first reversal lives in sessionComputed now (see its doc) — reversed
        // there rather than in SQL, same as before, but read out of the one atomic combine
        // instead of a second top-level subscription to exercisesInOrder. [exercisesInOrder]
        // itself stays in `position` order for the callers below that need it that way: history
        // (US-06b), US-06a's restore, and guided mode finding the next exercise (US-05a).

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
         * The session itself, its exercises, how far through the plan it is, and what one tap
         * would log next (ADR-0029, US-35) — everything that is a function of the session
         * changing, as opposed to a second ticking by. One `combine`, for the reason
         * [SessionData]'s doc explains: reading `session`/`exercises` back out of *this* value,
         * rather than from separate outer `combine` arguments that independently resubscribe
         * `activeSession` / `exercisesInOrder`, is what keeps them arriving as one atomic update.
         *
         * [SessionProgress] reads [rows] in plan order (the order [exercisesInOrder] is already
         * in) rather than the newest-first order the display list uses, since "current" and
         * "still to come" are both defined in terms of the plan; `exercises` below reverses it
         * for display the same way the old top-level `exercises` val did (US-02b).
         *
         * The one-tap prefill ([UpNextSet] built from [SessionProgress]'s open movement) is a
         * deliberately separate computation from [determineUpNextSet], which is keyed off "the
         * most recently logged set in this session" and is null until something has been logged
         * at all; the movement list needs a one-tap target from the very first set of the
         * session, before anything has been logged for anyone. It merges [SessionExercise.target]
         * with [PrefillFromLastSet] per field — exactly the merge `SetEntryController.open` uses
         * for `Add set` — rather than switching wholesale on whichever is present, since a
         * target's own fields are each independently optional (US-30).
         */
        private val sessionData: Flow<SessionData> =
            combine(
                activeSession,
                member,
                unitPreference.observe(),
                exercisesInOrder,
                selection.current,
            ) { session, memberId, unit, rows, selected ->
                val progress =
                    session?.let {
                        SessionProgress.of(
                            session = it,
                            exercises = rows.map { row -> row.sessionExercise },
                            sets = rows.flatMap { row -> row.sets },
                        )
                    }

                // The row the session screen has open (ADR-0029) — deliberately *not*
                // progress.current. SessionProgress.current means "zero sets logged", which is
                // correct for the header's "n of m done" but wrong for "which row is expanded
                // on screen": the moment the first set is logged against it, progress.current
                // would jump straight to null (or the next movement), and the movement someone
                // is mid-set on — one of three sets done, two to go — would have nowhere to
                // render at all. "The last movement with any set logged, or the first movement
                // if none do yet" is what the design's `1a Session mid-set` frame actually
                // shows: a movement with SET 1 and SET 2 already checked off and SET 3 dimmed
                // as NEXT, all on the *same* open row. [selected] (ADR-0037) wins over that
                // derived default when present — tapping any other exercise opens it explicitly,
                // and it stays open until a different explicit tap changes it. A stale selection
                // (its exercise was removed) falls through to the derived default automatically.
                val currentRow =
                    selected?.let { id -> rows.firstOrNull { it.sessionExercise.id == id } }
                        ?: rows.lastOrNull { it.sets.isNotEmpty() }
                        ?: rows.firstOrNull()
                val history =
                    currentRow?.let { row -> prefillFromLastSet(row.sessionExercise.exerciseId, memberId, unit) }
                val target = currentRow?.sessionExercise?.target
                // US-37 (ADR-0031): history wins over the target when both exist. The 3x12
                // floor ResolveSetPrefill adds is for the set-entry sheet's empty boxes, not
                // for this button — it stays absent for a movement with neither history nor a
                // target (US-35's own rule, unchanged), rather than starting to one-tap log a
                // number nobody chose for an exercise never done before.
                val prefill =
                    if (history != null || target != null) {
                        val resolved = ResolveSetPrefill(history = history, target = target, unit = unit)
                        SetPrefill(weight = resolved.weight, reps = resolved.reps)
                    } else {
                        null
                    }
                val nextLoggableSet =
                    if (currentRow == null || prefill == null) {
                        null
                    } else {
                        UpNextSet(
                            sessionExerciseId = currentRow.sessionExercise.id,
                            exerciseId = currentRow.sessionExercise.exerciseId,
                            setNumber = currentRow.sets.size + 1,
                            prefill = prefill,
                            comparison =
                                sets.lastSetOfBefore(
                                    currentRow.sessionExercise.exerciseId,
                                    memberId,
                                    currentRow.sessionExercise.sessionId,
                                ),
                        )
                    }

                SessionData(
                    session = session,
                    // US-02b: newest first, the same reversal the old top-level `exercises`
                    // val did — kept here instead so nothing outside this combine subscribes
                    // to exercisesInOrder a second time.
                    exercises = rows.asReversed(),
                    upNext = session?.let { determineUpNextSet(it.id, memberId, unit) },
                    progress = progress,
                    openSessionExerciseId = currentRow?.sessionExercise?.id,
                    nextLoggableSet = nextLoggableSet,
                )
            }

        /**
         * [sessionData] plus the rest countdown (ADR-0023). See [SessionComputed]'s doc for why
         * this is a second, outer `combine` rather than adding `rest.reading()` as a fifth
         * argument above: that ticks once a second for as long as the session is active, and
         * [sessionData]'s own computation is two suspend Room queries deep — this is what keeps
         * a countdown tick from re-running either of them. [RestController.reading] itself reads
         * its remaining-time and total in one tick for the same reason this whole class combines
         * atomically rather than per-field: two independently pushed flows can each emit for one
         * underlying change, and a member-facing `combine` over both would show a transient state
         * in between.
         */
        private val sessionComputed: Flow<SessionComputed> =
            combine(rest.reading(), sessionData) { reading, data ->
                SessionComputed(data = data, remaining = reading.remaining, total = reading.total)
            }

        /**
         * The screen state everything but [finish] drives. `sessionComputed` is a single
         * argument here rather than the several it replaces (`activeSession`, `exercises`, the
         * rest panel, [SessionProgress], the one-tap prefill) — see its doc for why folding
         * them into one atomic value, instead of separate `combine` arguments that would each
         * independently resubscribe the same cold upstream flows, is load-bearing here and not
         * just tidying up.
         */
        private val sessionState: Flow<SessionUiState> =
            combine(
                sessionComputed,
                stalePrompt,
                combine(
                    unitPreference.observe(),
                    setEntry.entry,
                    setEdit.edit,
                    setEdit.canUndo,
                ) { unit, entry, edit, canUndoSetDelete ->
                    ScreenExtras(unit, entry, edit, canUndoSetDelete)
                },
                combine(removal.canUndo, guided.state, oneTap.canUndo) { canUndoRemoval, guidedState, canUndoOneTap ->
                    SideTrips(canUndoRemoval, guidedState, canUndoOneTap)
                },
                justSetRecord,
            ) { computed, prompt, extras, sideTrips, record ->
                SessionUiState(
                    isLoading = false,
                    activeSession = computed.session,
                    stalePrompt = prompt,
                    exercises = computed.exercises,
                    unit = extras.unit,
                    setEntry = extras.entry,
                    setEdit = extras.edit,
                    canUndoSetDelete = extras.canUndoSetDelete,
                    restRemaining = computed.remaining,
                    restTotal = computed.total,
                    upNext = computed.upNext,
                    canUndoRemoval = sideTrips.canUndoRemoval,
                    canUndoOneTapLog = sideTrips.canUndoOneTapLog,
                    guided = sideTrips.guided,
                    progress = computed.progress,
                    openSessionExerciseId = computed.openSessionExerciseId,
                    nextLoggableSet = computed.nextLoggableSet,
                    justSetRecord = record,
                )
            }

        val uiState: StateFlow<SessionUiState> =
            combine(sessionState, finish.flow) { state, finishing -> state.copy(finish = finishing) }
                // Cheap insurance, not the fix for the transient-state race sessionComputed's
                // doc describes — that fix is structural (one atomic combine). This just
                // collapses the case where finish.flow re-emits its already-current value.
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState())

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
                // The write and the rest that follows it are `LogUpNextSet`'s, shared with the
                // notification's own LOG SET so the two cannot drift (US-54). What stays here
                // is the part that is genuinely this screen's: the record banner.
                val logged = oneTap.log(next)
                rest.markStarted()
                justSetRecord.value = detectPersonalRecord(logged, next.exerciseId, currentMember.id())
            }
        }

        /** Starts a session, or does nothing visible if one is already running (US-01). */

        fun onStartWorkout() {
            viewModelScope.launch { startSession(currentMember.id()) }
        }

        /**
         * Starts a session from the routine Train home is offering (US-36), the same one-tap
         * shortcut [onStartWorkout] is for the freestyle case — no navigation event, because the
         * screen already flips from `NoSession` to the running session reactively the moment
         * [activeSession] observes it.
         *
         * If a workout is already running, [StartSessionFromRoutine] resumes it and copies
         * nothing in (US-01) — the same outcome starting the same routine from the Routines
         * screen already has, silently here because there is no separate screen to explain it
         * on: the session that appears is the one already running, which answers the question
         * without a banner.
         */
        fun onStartFromRoutine(routineId: RoutineId) {
            viewModelScope.launch { startSessionFromRoutine(routineId, currentMember.id()) }
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

/**
 * Whether the set(s) just written set a record (US-18), checked against the first row only —
 * ADR-0025's "first time is not a record, beating must be strict" rule. A top-level function
 * rather than a member: [ActiveSessionViewModel] delegates most work to sub-controllers already
 * and this is the one piece [SetEntryController]'s callback needs that is not already one of
 * them, so it stays a function instead of growing the class or a new controller for one line.
 *
 * The two-tap and one-tap paths only ever log one set at a time, so "first" is the only set
 * there is. A manual "3 sets of 12" batch (ADR-0009) is the one case this undercounts:
 * [logged]'s later rows are already on disk by the time this runs, so they would see their own
 * identical-weight siblings as history and never register as beating it — checking the first
 * row is exactly right for that reason, not a shortcut around it.
 */
private suspend fun resolveJustSetRecord(
    sessionExerciseId: SessionExerciseId,
    logged: List<ExerciseSet>,
    sessionExercises: SessionExerciseRepository,
    detectPersonalRecord: DetectPersonalRecord,
    currentMember: CurrentMember,
): PersonalRecord? {
    val candidate = logged.firstOrNull()
    val exerciseId = candidate?.let { sessionExercises.find(sessionExerciseId)?.exerciseId }
    return exerciseId?.let { detectPersonalRecord(candidate, it, currentMember.id()) }
}
