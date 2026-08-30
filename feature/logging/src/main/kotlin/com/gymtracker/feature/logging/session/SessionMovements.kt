package com.gymtracker.feature.logging.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.GymLoadRow
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.component.NumeralText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.session.SetIntervals
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.SessionExerciseRow
import com.gymtracker.feature.logging.asMinutesSeconds
import java.time.Duration

/**
 * The plan, live (ADR-0029): the open movement with its set rows, and everything still to come
 * as one ruled line each. Read against the design bundle's `1a Session mid-set` frame.
 *
 * Replaces the card stack this used to be. There is no `Card` and no `surfaceContainer*` fill
 * anywhere below — flush-left rows on the bare ground, separated by [GymDivider]'s row-weight
 * rule. The one filled control on the whole screen is the log button in [SessionScaffold]'s
 * bottom bar, not anything in this file.
 *
 * [openSessionExerciseId] — not [com.gymtracker.core.domain.session.SessionProgress.current] —
 * decides which row is open. `SessionProgress.current` means "zero sets logged," which is right
 * for the header's "n of m done" and wrong for this: the moment the open movement's first set
 * is logged, `current` would jump to the *next* movement (or null), and the movement someone is
 * mid-set on — two of three sets done — would have nowhere to render. See
 * `ActiveSessionViewModel`'s computation of [openSessionExerciseId] for the actual rule,
 * including US-45/ADR-0037's explicit, sticky override for when the machine was taken and a
 * member switches back to an earlier exercise.
 *
 * **The other-exercises section (US-45) is every exercise but the open one, in plan order —
 * earlier or later, touched or not.** It used to be `position > currentRow.position` only, which
 * is exactly the bug ADR-0037 fixes: log a set on exercise 3 and exercises 1–2 had no row, no
 * button, nothing to tap for the rest of the session. Reads plan order back out of [exercises]
 * itself (`sessionExercise.position`) rather than needing a second, separately-ordered list
 * passed in. Its own label — "Other exercises" is gone — reads [otherExercisesSectionLabel]
 * (US-54): `THEN` for a plan-backed session, `ALSO TODAY` otherwise. Which exercises appear here
 * is unchanged by that rename.
 *
 * "Start exercise" (US-05a) and "Remove" (US-02c) are not in the design's frames — the mockup
 * does not show every control the app already has to keep. They stay, as a quiet text row under
 * the open movement's meta line, because guided mode and removing a movement are both real
 * capabilities this screen cannot silently drop.
 *
 * **The log bar is the last item in this `LazyColumn`, not a fixed sibling outside it.** ADR-0029
 * describes it as pinned; the first attempt at that (a plain `Row` outside this list) is exactly
 * where `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` broke: `performScrollTo()` throws —
 * not no-ops — on a node with no scrollable ancestor at all, which a fixed sibling never has.
 * Wrapping that lone `Row` in its own `Modifier.verticalScroll` "fixed" the throw but made
 * `waitUntil`/`performScrollTo` hang instead, for as long as the test's own timeout, on every
 * one of those tests — worth naming plainly as a real, reproduced failure mode, not a hunch: a
 * `ScrollState` Compose's test idling never considers settled is a bug in the *app*, not just an
 * inconvenience for the suite. The old per-card "Add set" already proved a `LazyColumn` item
 * works; putting the log bar back inside one is the same proven shape, not a new one.
 */
@Composable
internal fun SessionPlan(
    exercises: List<SessionExerciseRow>,
    openSessionExerciseId: SessionExerciseId?,
    nextLoggableSet: UpNextSet?,
    unit: WeightUnit,
    orderIsAPlan: Boolean,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
    onLogNextSet: (UpNextSet) -> Unit,
    onSelectExercise: (SessionExerciseId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val planOrder = exercises.sortedBy { it.sessionExercise.position }
    val currentRow = planOrder.firstOrNull { it.sessionExercise.id == openSessionExerciseId }
    // US-44: every set in the session, not just the open movement's own — intervals
    // deliberately span movements, so the first set of a freshly-opened exercise still reads
    // the walk from whatever was logged last.
    val intervals = SetIntervals.of(exercises.flatMap { it.sets })

    LazyColumn(modifier = modifier) {
        if (currentRow != null) {
            item(key = "current-${currentRow.sessionExercise.id.value}") {
                CurrentMovement(
                    row = currentRow,
                    exerciseNumber = currentRow.sessionExercise.position,
                    movementsTotal = planOrder.size,
                    unit = unit,
                    intervals = intervals,
                    onRemoveExercise = onRemoveExercise,
                    onStartExercise = onStartExercise,
                    onEditSet = onEditSet,
                )
            }
        }

        val otherExercises =
            if (currentRow == null) {
                emptyList()
            } else {
                planOrder.filter { it.sessionExercise.id != currentRow.sessionExercise.id }
            }
        if (otherExercises.isNotEmpty()) {
            item(key = "other-exercises-label") {
                EyebrowLabel(
                    text = otherExercisesSectionLabel(orderIsAPlan),
                    // Deliberately muted, not accent — the redesign audit's finding 07 flagged
                    // exactly this shape (a label the same colour as a link) reading as tappable
                    // when it is not.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = GymDimens.Gap, bottom = GymDimens.TightGap),
                )
            }
            items(otherExercises, key = { "queue-${it.sessionExercise.id.value}" }) { row ->
                StillToComeRow(
                    index = row.sessionExercise.position,
                    name = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
                    target = row.sessionExercise.target,
                    setsLogged = row.sets.size,
                    unit = unit,
                    // US-45 (ADR-0037): tapping opens the row fully — its own set list, target,
                    // and one-tap log button — rather than firing "Add set" blind the way this
                    // used to for a future exercise. The sheet is still one tap away from the
                    // now-open row's own log bar, unchanged.
                    onClick = { onSelectExercise(row.sessionExercise.id) },
                )
            }
        }

        if (currentRow != null) {
            item(key = "log-bar") {
                BottomLogBar(
                    currentRow = currentRow,
                    nextLoggableSet = nextLoggableSet,
                    unit = unit,
                    onLogNextSet = onLogNextSet,
                    onAddSet = onAddSet,
                    modifier = Modifier.padding(top = GymDimens.Gap, bottom = GymDimens.ScreenPadding),
                )
            }
        }
    }
}

/**
 * The mid-set log bar: the one-tap log button plus the way to open the stepper sheet (US-35).
 * Absent — not disabled — when there is nothing to log one-tap ([nextLoggableSet] null): a
 * brand-new exercise with no history and no target has nothing sensible to write without
 * opening the sheet first, so only the sheet-opening button shows in that case.
 *
 * **The secondary button reads "Add set", not the design's "ADJUST".** It is the exact same
 * control `Add set` always was — same callback, same sheet, same "Save set" confirm — and
 * `TwoTapSetLoggingTest` matches `onNodeWithText("Add set")` literally. Renaming the label
 * without renaming what it does would be exactly the kind of change CLAUDE.md and the roadmap
 * both call out: "if this test needs editing, the redesign went wrong." It does not need
 * editing, because the control it depends on kept its name.
 */
@Composable
private fun BottomLogBar(
    currentRow: SessionExerciseRow,
    nextLoggableSet: UpNextSet?,
    unit: WeightUnit,
    onLogNextSet: (UpNextSet) -> Unit,
    onAddSet: (SessionExerciseRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
        if (nextLoggableSet != null) {
            PrimaryActionButton(
                eyebrow = "LOG SET ${nextLoggableSet.setNumber}",
                detail = logButtonDetail(nextLoggableSet, unit),
                onClick = { onLogNextSet(nextLoggableSet) },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { onAddSet(currentRow) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.sizeIn(minHeight = GymDimens.PrimaryAction, minWidth = GymDimens.PrimaryAction),
            ) {
                Text("Add set")
            }
        } else {
            PrimaryActionButton(
                text = "Add set",
                onClick = { onAddSet(currentRow) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "100 lb · 45.4 kg × 8", the same conversion [RestPanel]'s "Up next" reads. */
private fun logButtonDetail(
    next: UpNextSet,
    unit: WeightUnit,
): String {
    val weight = WeightFormatter.format(next.prefill.weight?.let { UnitConverter.toKilograms(it, unit) }, unit)
    // Reading unit only — same reason as RestPanel.kt's namesake: this button shares its row
    // with `Add set`, and both units at this size wrap to a line the button then clips. The set
    // rows directly above carry the conversion (ADR-0008).
    return "${weight.primary} × ${next.prefill.reps}"
}

/**
 * The movement currently open: eyebrow, name, target, its set rows, and (kept from before the
 * redesign, absent from the design's own frames) the way to start it guided or remove it.
 */
@Composable
private fun CurrentMovement(
    row: SessionExerciseRow,
    exerciseNumber: Int,
    movementsTotal: Int,
    unit: WeightUnit,
    intervals: Map<String, Duration>,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = GymDimens.Gap)) {
        Column(
            modifier = Modifier.padding(bottom = GymDimens.TightGap),
            // GymDimens has no token between 0 and HairGap (4dp); reusing it here rather than
            // naming a raw 2dp, per ADR-0011's "feature code never hard-codes a dp" rule.
            verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap),
        ) {
            EyebrowLabel(
                text = sessionKicker(exerciseNumber, movementsTotal, row.sessionExercise.target, row.sets.size),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
                style = MaterialTheme.typography.headlineSmall,
            )
            targetLine(row.sessionExercise.target, unit)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
            TextButton(
                onClick = { onStartExercise(row) },
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Start exercise")
            }
            // ADR-0019's structural rule (a destructive control never shares a surface with a
            // save) is about the screen's one filled control, which this row is not — but it
            // stays outlined-in-spirit (plain text, error-coloured) rather than filled regardless.
            TextButton(
                onClick = { onRemoveExercise(row.sessionExercise.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Remove")
            }
        }

        // US-44 (`Redesign.dc.html` 3g): absent, not zero, until there are at least two sets to
        // pace between — SetIntervals.average already returns null for exactly that case.
        SetIntervals.average(row.sets, intervals)?.let { average ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = GymDimens.HairGap),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${row.sets.size} ${if (row.sets.size == 1) "set" else "sets"} logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumeralText(
                    text = "avg ${average.asMinutesSeconds()} between",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        LoggedSets(row.sets, unit, intervals) { set -> onEditSet(row, set) }
    }
}

/**
 * The sets already logged against the current movement, each its own tap target (ADR-0022) —
 * ruled rows, not a collapsed line (ADR-0009 already explained why: one line for three sets was
 * unreadable to correct).
 *
 * **The load is a split baseline row ([GymLoadRow]), not one formatted string carrying both
 * units (ADR-0011's Turn 4 amendment).** This is the same wrap `RestPanel.kt`'s `UpNext` and
 * `GuidedExerciseScreen`'s hero lines had — a bodyweight set's `"Bodyweight × 12"` breaking a
 * numeral-sized role that had no width budget for an eleven-character word — flagged here at
 * the time as the identical bug and left for later rather than fixed in the same pass. The kg
 * conversion is dropped entirely (ADR-0008's Turn 4 amendment: kg stays on Progress and history
 * rows only, and this is neither), where it used to hang off the end via `weight.secondary`.
 *
 * **The trailing slot carries the set-to-set interval, not a checkmark (US-44, ADR-0036).** A
 * row already showing a real weight × reps number does not need a second symbol to confirm it
 * happened; the interval is new information the checkmark never was. "—" for the first set of
 * the session (nothing to measure from) and for a bulk-logged gap [SetIntervals] suppresses as
 * noise, matching the muted colour and dash the design's own first row uses.
 */
@Composable
private fun LoggedSets(
    sets: List<ExerciseSet>,
    unit: WeightUnit,
    intervals: Map<String, Duration>,
    onEditSet: (ExerciseSet) -> Unit,
) {
    Column {
        GymDivider()
        sets.forEach { set ->
            val weight = WeightFormatter.format(set.weightKg, unit)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = GymDimens.MinTouchTarget)
                        .clickable { onEditSet(set) }
                        .semantics { contentDescription = "Edit set ${set.setIndex}" }
                        .padding(vertical = GymDimens.HairGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GymText(
                    text = "SET ${set.setIndex}",
                    role = GymTextRoles.LabelCaps,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(GymDimens.RowLabelWidth),
                )
                GymLoadRow(
                    number = weight.number,
                    unit = weight.unit,
                    wordFallback = weight.primary,
                    reps = set.reps.toString(),
                    numeralRole = GymTextRoles.NumeralMd,
                    wordRole = GymTextRoles.WordUnit,
                    modifier = Modifier.weight(1f),
                )
                GymText(
                    text = intervals[set.id]?.let { "+${it.asMinutesSeconds()}" } ?: "—",
                    role = GymTextRoles.Meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GymDivider()
        }
        if (sets.isEmpty()) {
            Text(
                text = "No sets yet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = GymDimens.Gap),
            )
        }
    }
}

/**
 * One other movement in the session: index, name, and either its target (not yet started) or
 * how many sets it already carries (US-45 — switched away from, not lost). Tapping opens it
 * fully; there are no per-row actions beyond that.
 */
@Composable
private fun StillToComeRow(
    index: Int,
    name: String,
    target: MovementTarget?,
    setsLogged: Int,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .clickable(onClick = onClick)
                    .padding(vertical = GymDimens.HairGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(GymDimens.RowLabelWidth),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // A row already carrying sets (switched away from, not untouched) says so instead
            // of its target — distinguishing "not started" from "in progress elsewhere" at a
            // glance, without a new visual language: the exact phrase CurrentMovement's own
            // "N sets logged" line already established (US-44).
            val meta =
                if (setsLogged > 0) {
                    "$setsLogged ${if (setsLogged == 1) "set" else "sets"} logged"
                } else {
                    queueTargetLine(target, unit)
                }
            meta?.let { line ->
                NumeralText(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        GymDivider()
    }
}

/** A section eyebrow (ADR-0029): uppercased for display only, the string itself untouched. */
@Composable
internal fun EyebrowLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/**
 * US-54 / ADR-0046: the open exercise's kicker. A real sets count on the exercise's own
 * [target][MovementTarget.sets] is what "plan" means here — not
 * [com.gymtracker.core.domain.session.SessionProgress.orderIsAPlan], which gates the header tag
 * and section label instead (a plan-backed session's open exercise can still have no target of
 * its own, and a freestyle session's exercise could in principle carry one — the two signals are
 * independent, both already real fields on the domain model this pass didn't add).
 */
internal fun sessionKicker(
    exerciseNumber: Int,
    movementsTotal: Int,
    target: MovementTarget?,
    setsLogged: Int,
): String {
    val setsPlanned = target?.sets
    val setNumber = setsLogged + 1
    return when {
        setsPlanned == null -> "CURRENT"
        // Absorbed silently (00-gate.md 3.11), not flagged or blocked: the plan was a
        // suggestion, and once setNumber exceeds it, the exercise/total position stops being
        // the number worth naming — just which set this is, and that it's past the plan.
        setNumber > setsPlanned -> "SET $setNumber · EXTRA"
        else -> "EXERCISE $exerciseNumber OF $movementsTotal · SET $setNumber OF $setsPlanned"
    }
}

/** US-54: the other-exercises section label — a rename only, not a filter change (US-45 stands). */
internal fun otherExercisesSectionLabel(orderIsAPlan: Boolean): String = if (orderIsAPlan) "THEN" else "ALSO TODAY"

/** "Target 3 × 8 · 105 lb" for the current movement, or null when it has no target (US-13). */
private fun targetLine(
    target: MovementTarget?,
    unit: WeightUnit,
): String? {
    val setsReps = setsRepsPart(target) ?: return null
    val weight = target?.weightKg?.let { WeightFormatter.format(it, unit).primary }
    return "Target $setsReps" + (weight?.let { " · $it" } ?: "")
}

/** "3×10 · 90 lb" for a still-to-come row — the same numbers, without the "Target" label. */
private fun queueTargetLine(
    target: MovementTarget?,
    unit: WeightUnit,
): String? {
    val setsReps = setsRepsPart(target)?.replace(" × ", "×") ?: return null
    val weight = target?.weightKg?.let { WeightFormatter.format(it, unit).primary }
    return setsReps + (weight?.let { " · $it" } ?: "")
}

private fun setsRepsPart(target: MovementTarget?): String? {
    val sets = target?.sets
    val reps = target?.reps
    return when {
        sets != null && reps != null -> "$sets × $reps"
        reps != null -> "$reps reps"
        sets != null -> "$sets sets"
        else -> null
    }
}
