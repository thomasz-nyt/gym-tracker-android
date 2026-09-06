package com.gymtracker.feature.logging

import com.gymtracker.core.domain.guided.GuidedPlan
import com.gymtracker.core.domain.guided.GuidedPlanStore
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.SetInput
import com.gymtracker.core.domain.set.SetPrefill
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.core.domain.units.weightIncrement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

/**
 * The dialog that starts a guided exercise (US-05a).
 *
 * Its own form rather than a mode on [SetEntry]. Keeping them separate is what guarantees the
 * two-tap path of US-03 is untouched — "Add set" and "Save set" cannot acquire a branch they
 * did not have (constitution §2.1, ADR-0017).
 */
data class GuidedSetup(
    val row: SessionExerciseRow,
    val exerciseName: String,
    val weight: String,
    val reps: String,
    val sets: String,
)

/**
 * An exercise being walked through, as the screen renders it.
 *
 * @property weight the load for the set about to be finished, as the field shows it — in the
 *   member's unit, blank for a bodyweight set (US-05a, amended 2026-09-05). Set 1 starts at the
 *   start dialog's weight; every later set starts at the weight of the set just written, because
 *   the last set is the best prefill for the next (US-37's rule inside one exercise). Editable,
 *   for the same reason [reps] is: 135 in the row when 145 was on the bar is a fabricated value.
 * @property weightKg what [weight] will write, in canonical kilograms — null for a bodyweight
 *   set, and also null while the field will not read; [canLogSet] tells the two apart. Untyped,
 *   it is the carried kilograms exactly, never round-tripped through the member's unit.
 * @property setsDone how many of [targetSets] are logged. Derived from the rows in the
 *   database, never counted in memory, so a kill mid-exercise resumes where it left off.
 * @property reps the count for the set about to be finished, prefilled with [targetReps] and
 *   editable. Writing the target when fewer were managed would fabricate a value
 *   (constitution §2.4).
 * @property isComplete true once [targetSets] are done, at which point the screen shows the
 *   summary instead of the next set.
 * @property nextUp the next exercise in the session with nothing logged against it, offered
 *   after the summary. Derived from `position` order — nothing records a queue.
 */
data class GuidedRunning(
    val row: SessionExerciseRow,
    val exerciseName: String,
    val weight: String,
    val weightKg: Double?,
    val targetSets: Int,
    val targetReps: Int,
    val setsDone: Int,
    val reps: String,
    val isComplete: Boolean,
    val volumeKg: Double?,
    val elapsed: Duration,
    val nextUp: SessionExerciseRow?,
)

/** Guided mode's slice of [SessionUiState]. Both null means the session screen is showing. */
data class GuidedState(
    val setup: GuidedSetup? = null,
    val running: GuidedRunning? = null,
)

/**
 * Whether `Log set n` would actually write anything right now: the rep count reads as a whole
 * number of at least one, and the weight is blank (bodyweight) or reads as a number. The one
 * predicate the button's enabled state and [GuidedController.finishSet] share — the same fix
 * `SetEntry.canSave()` made for the sheet, so the two cannot drift apart.
 */
internal fun GuidedRunning.canLogSet(): Boolean {
    val typedWeight = weight.trim()
    val weightReads = typedWeight.isEmpty() || typedWeight.toDoubleOrNull()?.let { it >= 0.0 } == true
    return weightReads && reps.toIntOrNull()?.let { it >= 1 } == true
}

/**
 * What the member has typed over the set about to be finished, if anything (US-05a). Null means
 * untouched: the rep count falls back to the target and the weight to the set just written (or
 * the plan's, for set 1) — see [runningExercise]. Cleared after every set, so those fallbacks are
 * what each new set starts from.
 */
private data class TypedSet(
    val reps: String? = null,
    val weight: String? = null,
)

/**
 * Walking through one exercise, set by set (US-05a, ADR-0017).
 *
 * Opt-in and additive: this is a lens over the same rows the session screen shows, never a
 * separate place the data lives. Backing out leaves every set logged so far exactly where it
 * was, and each "Finish set" writes through the same [LogSets] the manual path uses.
 *
 * The one thing it does better than the manual path: ADR-0009 writes N sets sharing a single
 * `performed_at` — "the time they were recorded, not a guess at when each was performed".
 * Here each set is logged as it finishes, so each carries a real one.
 *
 * `TooManyFunctions` is suppressed for the same reason `SetDao` and `BackupCodec` suppress it:
 * this class drives one exercise through guided mode start to finish, and the setup dialog's
 * three steppers (weight, reps, sets — ADR-0033's own named follow-up) are that same one
 * responsibility, not a second one. Splitting the setup-dialog methods into their own class
 * would separate a form from the flow it starts, for no reader's benefit — [start] and [begin]
 * already have to be read together.
 */
@Suppress("TooManyFunctions")
class GuidedController(
    /**
     * Writes one set and then starts the rest that follows it.
     *
     * One parameter rather than [LogSets] plus a separate callback, because the **order** is
     * the guarantee: a write that failed must not leave a timer counting down for a set that
     * does not exist. Two parameters would let a caller wire them the other way round.
     */
    private val performSet: suspend (SessionExerciseId, SetInput) -> Unit,
    private val unitPreference: UnitPreference,
    private val planStore: GuidedPlanStore,
    private val exercises: Flow<List<SessionExerciseRow>>,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val setup = MutableStateFlow<GuidedSetup?>(null)
    private val typed = MutableStateFlow(TypedSet())

    val state: Flow<GuidedState> =
        combine(
            setup,
            planStore.plan,
            exercises,
            typed,
            unitPreference.observe(),
        ) { pending, plan, rows, typedSet, unit ->
            GuidedState(setup = pending, running = plan?.let { runningExercise(it, rows, typedSet, unit, clock) })
        }

    /**
     * Opens the start dialog: weight from the last time this exercise was done, same as US-03's
     * set entry; reps and sets at a fixed walkthrough length instead.
     *
     * Reps and sets deliberately do **not** follow history here, unlike weight. Each set guided
     * mode writes gets its own real `performed_at` regardless of the target count (see the class
     * doc), so a fixed starting point costs nothing the way raising the two-tap sheet's Sets
     * floor above 1 would — that would let confirming without editing fabricate a shared
     * timestamp across several rows, exactly what ADR-0031 found and reverted on-device.
     *
     * The prefill is looked up by the caller rather than here, so this class does not need the
     * member and the catalog as well — and so the prefilling rule lives in one place.
     */
    fun start(
        row: SessionExerciseRow,
        prefill: SetPrefill?,
    ) {
        setup.value =
            GuidedSetup(
                row = row,
                exerciseName = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
                weight = prefill?.weight?.let(::trimNumber).orEmpty(),
                reps = DEFAULT_TARGET_REPS,
                sets = DEFAULT_TARGET_SETS,
            )
    }

    /** One handler for the start dialog; pass only the field that changed. */
    fun changeSetup(
        weight: String? = null,
        reps: String? = null,
        sets: String? = null,
    ) {
        setup.value =
            setup.value?.let { current ->
                current.copy(
                    weight = weight ?: current.weight,
                    reps = reps ?: current.reps,
                    sets = sets ?: current.sets,
                )
            }
    }

    fun dismissSetup() {
        setup.value = null
    }

    /**
     * Steps the setup dialog's rep target by -1 or +1 (US-43 UI follow-up: the dialog gains the
     * app's stepper, matching the rep count on the screen it opens).
     *
     * Separate from [stepReps]: that steps the count for the set about to be *finished*, once
     * the flow is running; this steps the *pending target*, before [begin] has been called.
     * They read and write different fields of [GuidedState] and must never be confused for one
     * another — a no-op on a null [setup] rather than falling back to the running exercise is
     * how that stays true structurally, not just by convention.
     */
    fun stepSetupReps(direction: Int) {
        setup.value = setup.value?.let { current -> current.copy(reps = current.reps.stepWholeNumber(direction)) }
    }

    /** [stepSetupReps]'s counterpart for the setup dialog's set target. */
    fun stepSetupSets(direction: Int) {
        setup.value = setup.value?.let { current -> current.copy(sets = current.sets.stepWholeNumber(direction)) }
    }

    /**
     * [stepSetupReps]'s counterpart for the setup dialog's weight — ADR-0033's own "what this
     * ADR does not touch" section named all three fields as the follow-up, not just reps and
     * sets. Same rule [SetEntryController.stepWeight] uses: one increment of the member's unit
     * (2.5 kg / 5 lb), snapped rather than offset so a value entered in the other unit steps
     * cleanly, floored at blank rather than zero (a bodyweight set, not a claim the bar weighs
     * nothing — constitution §2).
     */
    fun stepSetupWeight(direction: Int) {
        scope.launch {
            val increment = unitPreference.current().weightIncrement()
            setup.value =
                setup.value?.let { current ->
                    val from = current.weight.trim().toDoubleOrNull() ?: 0.0
                    val stepped = snap(from, increment, direction)
                    current.copy(weight = if (stepped <= 0.0) "" else trimNumber(stepped))
                }
        }
    }

    /** Begins the exercise. Nothing is written yet — the first set is written when it is done. */
    fun begin() {
        val pending = setup.value ?: return
        val targetReps = pending.reps.toIntOrNull()?.takeIf { it >= 1 }
        val targetSets = pending.sets.toIntOrNull()?.takeIf { it >= 1 }
        if (targetReps == null || targetSets == null) return

        scope.launch {
            val unit = unitPreference.current()
            val entered = pending.weight.trim()
            val typedWeight = entered.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

            planStore.setPlan(
                GuidedPlan(
                    sessionExerciseId = pending.row.sessionExercise.id,
                    targetSets = targetSets,
                    targetReps = targetReps,
                    weightKg = typedWeight?.let { UnitConverter.toKilograms(it, unit) },
                    // Counted from the database, so an exercise part-logged by hand does not
                    // read as already finished.
                    setsAtStart = pending.row.sets.size,
                    startedAt = clock.instant(),
                ),
            )
            setup.value = null
            typed.value = TypedSet()
        }
    }

    /** Corrects the rep count for the set about to be finished. */
    fun changeReps(reps: String) {
        typed.update { it.copy(reps = reps) }
    }

    /**
     * Steps the rep count for the set about to be finished (ADR-0033, ADR-0016).
     *
     * Steps from what the screen shows — [GuidedRunning.reps], which is already the typed value
     * if there is one, else the target — so a step before any typing moves from the number on
     * screen, not from zero. The floor is [String.stepWholeNumber]'s, the same one set entry and
     * set correction already share.
     */
    fun stepReps(direction: Int) {
        scope.launch {
            val running = state.first().running ?: return@launch
            typed.update { it.copy(reps = running.reps.stepWholeNumber(direction)) }
        }
    }

    /** Corrects the load for the set about to be finished (US-05a, amended 2026-09-05). */
    fun changeWeight(weight: String) {
        typed.update { it.copy(weight = weight) }
    }

    /**
     * Steps the load for the set about to be finished by one increment of the member's unit —
     * [SetEntryController.stepWeight]'s exact rule (2.5 kg / 5 lb, snapped onto the increment so
     * a weight carried over from a set typed in the other unit steps cleanly, floored at blank
     * rather than zero: a bodyweight set, not a claim the bar weighs nothing). From what the
     * screen shows, like [stepReps].
     */
    fun stepWeight(direction: Int) {
        scope.launch {
            val running = state.first().running ?: return@launch
            val increment = unitPreference.current().weightIncrement()
            val from = running.weight.trim().toDoubleOrNull() ?: 0.0
            val stepped = snap(from, increment, direction)
            typed.update { it.copy(weight = if (stepped <= 0.0) "" else trimNumber(stepped)) }
        }
    }

    /**
     * Writes the set that was just performed and starts the rest (US-05).
     *
     * One set, one `performed_at`. The rest starts only after the write returns, so a save
     * that failed cannot leave a timer counting down for a set that does not exist. What is
     * written is exactly what the screen shows — [GuidedRunning.weightKg] and [GuidedRunning.reps],
     * read off the same rendered state, gated by the same [canLogSet] as the button — so the
     * number on screen and the number in the row cannot disagree (constitution §2.4).
     */
    fun finishSet() {
        scope.launch {
            val running = state.first().running?.takeIf { it.canLogSet() } ?: return@launch

            performSet(
                running.row.sessionExercise.id,
                SetInput(
                    // Already canonical kilograms — carried or converted once in runningExercise,
                    // so it is handed back in KG rather than round-tripped through the member's unit.
                    weight = running.weightKg,
                    unit = WeightUnit.KG,
                    reps = running.reps.toInt(),
                    rpe = null,
                ),
            )
            typed.value = TypedSet()
        }
    }

    /** Leaves guided mode. Every set logged so far stays exactly where it is. */
    fun stop() {
        scope.launch {
            planStore.setPlan(null)
            typed.value = TypedSet()
        }
    }

    /**
     * Ends this exercise and opens the start dialog for the next one.
     *
     * The plan is cleared first, so a kill between the two lands on the session screen with
     * everything logged rather than on a flow for an exercise that was never begun.
     */
    fun startNext(
        row: SessionExerciseRow,
        prefill: SetPrefill?,
    ) {
        scope.launch {
            planStore.setPlan(null)
            typed.value = TypedSet()
            start(row, prefill)
        }
    }

    private companion object {
        /**
         * A fixed walkthrough length (3 sets of 12), not read from history or a routine target.
         * Safe to default above ADR-0009's single-set floor here specifically because each set
         * is logged as it finishes with its own real timestamp — unlike the two-tap sheet, whose
         * own Sets field stays at 1 for exactly the reason this one does not need to (see
         * [start]'s doc).
         */
        const val DEFAULT_TARGET_SETS = "3"
        const val DEFAULT_TARGET_REPS = "12"
    }
}

/**
 * What [GuidedController] renders for a running exercise, computed fresh each time [combine]
 * ticks. Top-level rather than a class member — it depends only on its own parameters, not on
 * any of [GuidedController]'s private state — and moving it out was the difference between 13
 * member functions (detekt's `TooManyFunctions` threshold) and 12 once the three new
 * setup-dialog steppers (weight, reps, sets — ADR-0033's own named follow-up) landed alongside
 * [GuidedController.changeReps] and [GuidedController.stepReps].
 */
private fun runningExercise(
    plan: GuidedPlan,
    rows: List<SessionExerciseRow>,
    typed: TypedSet,
    unit: WeightUnit,
    clock: Clock,
): GuidedRunning? {
    val row = rows.firstOrNull { it.sessionExercise.id == plan.sessionExerciseId } ?: return null
    val done = (row.sets.size - plan.setsAtStart).coerceAtLeast(0)
    val logged = row.sets.takeLast(done)
    val weighted = logged.mapNotNull { set -> set.weightKg?.let { it * set.reps } }

    // US-05a (amended 2026-09-05): the load carried into the set about to be finished is the set
    // just written — read off the rows, like `done`, so a kill mid-exercise resumes at the weight
    // actually being lifted — and the plan's only for set 1. Whatever the member has typed over
    // it wins, converted once here; untyped, the carried kilograms pass through exactly.
    val carriedKg = if (done > 0) logged.last().weightKg else plan.weightKg
    val weight = typed.weight ?: carriedKg?.let { trimNumber(UnitConverter.fromKilograms(it, unit)) }.orEmpty()

    return GuidedRunning(
        row = row,
        exerciseName = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
        weight = weight,
        weightKg = if (typed.weight == null) carriedKg else weight.asKilograms(unit),
        targetSets = plan.targetSets,
        targetReps = plan.targetReps,
        setsDone = done,
        reps = typed.reps ?: plan.targetReps.toString(),
        isComplete = done >= plan.targetSets,
        volumeKg = if (weighted.isEmpty()) null else weighted.sum(),
        elapsed = Duration.between(plan.startedAt, clock.instant()),
        nextUp = rows.firstOrNull { it.sessionExercise.id != row.sessionExercise.id && it.sets.isEmpty() },
    )
}

/**
 * The kilograms a typed weight will write: null for blank (a bodyweight set) and for text that
 * will not read as a non-negative number — [GuidedRunning.canLogSet] tells those two apart, and
 * keeps the second from ever reaching `performSet`.
 */
private fun String.asKilograms(unit: WeightUnit): Double? =
    trim()
        .takeIf { it.isNotEmpty() }
        ?.toDoubleOrNull()
        ?.takeIf { it >= 0.0 }
        ?.let { UnitConverter.toKilograms(it, unit) }
