package com.gymtracker.feature.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.gymtracker.core.designsystem.component.GymLoadRow
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.component.NumeralText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.RepMascot
import com.gymtracker.core.designsystem.component.StepperField
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymPreviews
import com.gymtracker.core.designsystem.theme.GymTextRoles
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightDisplay
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.session.EyebrowLabel
import com.gymtracker.feature.logging.session.SegmentBar
import java.time.Duration

/**
 * Walking through one exercise, set by set (US-05a, ADR-0017, ADR-0033).
 *
 * Rebuilt on the shipped design system for ADR-0033: `Redesign.dc.html` has no frame for this
 * screen, but option `1b` — the one-exercise-at-a-time direction ADR-0029 rejected for the
 * *main* session screen — is a materially closer match to what this screen already is than to
 * the multi-exercise screen it lost to there. Every size below reads through a `Typography`
 * role ADR-0029 already shipped; nothing here adds a new one.
 *
 * Everything on screen is still one of three things: what you are lifting, which set you are
 * on, and the one button that ends it. The rep count is a field rather than a label because the
 * target is a prefill and not a promise — logging 12 when 9 were managed would fabricate a
 * value (constitution §2.4). Since 2026-09-05 the weight is a field for the same reason (US-05a,
 * amended): 135 in the row when 145 was on the bar is the same fabrication.
 *
 * The root is a `LazyColumn`, not a `Column`, on a small enough screen that this content does
 * not always fit — `SessionMovements.kt`'s `BottomLogBar` already documents why: a bare
 * `Modifier.verticalScroll` gave `performScrollTo()` an ancestor but made Compose's test idling
 * hang instead of the throw it exists to replace, and a real `LazyColumn` was the proven fix.
 */
@Composable
internal fun GuidedExerciseScreen(
    running: GuidedRunning,
    unit: WeightUnit,
    restRemaining: Duration?,
    onWeightChanged: (String) -> Unit,
    onWeightStepped: (Int) -> Unit,
    onRepsChanged: (String) -> Unit,
    onRepsStepped: (Int) -> Unit,
    onFinishSet: () -> Unit,
    onStartNext: (SessionExerciseRow) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        // No root padding, and nothing centred (ADR-0033): every other rebuilt screen is
        // flush-left, and the resting hero needs to be full-bleed, which a root padding would
        // prevent. Each state below applies its own.
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (running.isComplete) {
                item { ExerciseSummary(running, unit, onStartNext, onStop) }
            } else {
                item {
                    if (restRemaining != null) {
                        RestHero(running, unit, restRemaining)
                    } else {
                        MidSetHeader(running, unit)
                    }
                }
                item {
                    GuidedControls(
                        running = running,
                        unit = unit,
                        onWeightChanged = onWeightChanged,
                        onWeightStepped = onWeightStepped,
                        onRepsChanged = onRepsChanged,
                        onRepsStepped = onRepsStepped,
                        onFinishSet = onFinishSet,
                        onStop = onStop,
                    )
                }
            }
        }
    }
}

/**
 * Mid-set, not resting (`1b Focus mid-set`). The hero reads [GuidedRunning.reps] — the number
 * about to be written — not the target: the literal fix for a member seeing a number on screen
 * that was not the one [onFinishSet] would log.
 */
@Composable
private fun MidSetHeader(
    running: GuidedRunning,
    unit: WeightUnit,
) {
    Column(
        modifier = Modifier.padding(GymDimens.CompactScreenPadding),
        verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap),
    ) {
        EyebrowLabel(
            text = "Set ${running.setsDone + 1} of ${running.targetSets}",
            color = MaterialTheme.colorScheme.primary,
        )
        GymText(text = running.exerciseName, role = GymTextRoles.TitleLg)
        HorizontalDivider(
            thickness = GymDimens.StructuralRuleThickness,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val weight = WeightFormatter.format(running.weightKg, unit)
        // ADR-0011's Turn 4 amendment: the split baseline row from GymLoadRow, not one
        // formatted string — the fix for the same "Bodyweight" wrap RestPanel.kt's UpNext had.
        // A mid-typing empty field would otherwise draw "135 lb ×" with no reps — onFinishSet is
        // already disabled in that state, so the "—" fallback is display-only, not validation.
        GymLoadRow(
            number = weight.number,
            unit = weight.unit,
            wordFallback = weight.primary,
            reps = running.reps.ifBlank { "—" },
            numeralRole = GymTextRoles.NumeralLg,
            wordRole = GymTextRoles.WordUnit,
        )
        // ADR-0008's Turn 4 amendment: the kg conversion is withdrawn from this surface —
        // weight.secondary is no longer read here.
    }
}

/**
 * Resting (`1b Focus resting`) — the red hero a member asked for by name: the movement, its
 * load, its rep count and which set it is, together on one line inside the accent block, rather
 * than as separate lines on the bare ground the way the main session screen's rest banner draws
 * them (`RestPanel.kt`'s `UpNext`). That is a deliberate difference, not a missed reuse: this
 * screen holds exactly one exercise, so there is nothing else on screen the combined line would
 * be competing with the way the session screen's "Up next" would be.
 *
 * No skip-rest control is drawn here — [GuidedControls]'s `Log set {n}` is already live
 * throughout the countdown (ADR-0023's rule, held structurally on this screen exactly as on the
 * session screen), so there is no action a skip button would unblock.
 *
 * US-43 / ADR-0035: `RepMascot` plays beside "Rest", drawn `monochrome` — gold-on-red measures
 * nowhere near a usable contrast, so the band is dropped here rather than recoloured again.
 */
@Composable
private fun RestHero(
    running: GuidedRunning,
    unit: WeightUnit,
    remaining: Duration,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.CompactScreenPadding),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                EyebrowLabel(text = "Rest", color = MaterialTheme.colorScheme.onPrimary)
                RepMascot(modifier = Modifier.height(GymDimens.MascotInline), monochrome = true)
            }
            GymText(
                text = remaining.asMinutesSeconds(),
                role = GymTextRoles.DisplayTimer,
                semantics = { contentDescription = "Rest ${remaining.asMinutesSeconds()} remaining" },
            )
            HorizontalDivider(
                thickness = GymDimens.StructuralRuleThickness,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            EyebrowLabel(text = "Then", color = MaterialTheme.colorScheme.onPrimary)
            GymText(text = running.exerciseName, role = GymTextRoles.TitleLg)
            // ADR-0011's Turn 4 amendment: the "55 lb × 12 · 25 kg · set 2 of 3" sentence — set
            // at the same weight as the exercise name above it — becomes two rows: the load as
            // a split baseline row, then the set position as its own label.caps line. The kg
            // conversion is dropped (ADR-0008's Turn 4 amendment).
            val weight = WeightFormatter.format(running.weightKg, unit)
            GymLoadRow(
                number = weight.number,
                unit = weight.unit,
                wordFallback = weight.primary,
                reps = running.reps,
                numeralRole = GymTextRoles.NumeralLg,
                wordRole = GymTextRoles.WordUnit,
            )
            // GuidedRunning.targetSets is a non-nullable Int (a fixed 3-set default backs a
            // movement with no target), so this never needs UpNext's "no total on record" guard.
            GymText(
                text = "SET ${running.setsDone + 1} OF ${running.targetSets}",
                role = GymTextRoles.LabelCaps,
            )
        }
    }
}

/** The set-progress dots, the weight and rep steppers, and the log/stop row — shared by both non-complete states. */
@Composable
private fun GuidedControls(
    running: GuidedRunning,
    unit: WeightUnit,
    onWeightChanged: (String) -> Unit,
    onWeightStepped: (Int) -> Unit,
    onRepsChanged: (String) -> Unit,
    onRepsStepped: (Int) -> Unit,
    onFinishSet: () -> Unit,
    onStop: () -> Unit,
) {
    val weight = WeightFormatter.format(running.weightKg, unit)
    Column(
        modifier = Modifier.padding(GymDimens.CompactScreenPadding),
        verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
    ) {
        SegmentBar(total = running.targetSets, done = running.setsDone)

        WeightField(running, unit, onWeightChanged, onWeightStepped)

        StepperField(
            label = "Reps",
            value = running.reps,
            onValueChange = onRepsChanged,
            onStep = onRepsStepped,
            // ADR-0011's Turn 4 amendment (frame 4b): "Target 12 — change it if you managed a
            // different number." used to cost a second line under every stepper. The steppers
            // already explain themselves; "Target 12" was the only part that was information,
            // so it moves onto the label's own row, right-aligned, one line, always.
            trailingLabel = "Target ${running.targetReps}",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
            // Eyebrow/detail, not the single-string overload: the frame's AFTER always states
            // what LOG SET will record, matching RestPanel.kt's log button (frame 4c) — the
            // "hero already shows the numbers" reasoning this comment used to give no longer
            // holds once the hero is a compact baseline row rather than a full sentence.
            PrimaryActionButton(
                eyebrow = "LOG SET ${running.setsDone + 1}",
                detail = logButtonDetail(weight, running.reps),
                onClick = onFinishSet,
                // The same predicate finishSet itself is gated by, so the button never promises
                // a write the controller would refuse (a blank rep count, a weight that won't read).
                enabled = running.canLogSet(),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onStop,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.sizeIn(minHeight = GymDimens.PrimaryAction, minWidth = GymDimens.PrimaryAction),
            ) {
                Text("Stop")
            }
        }
    }
}

/**
 * The load for the set about to be finished (US-05a, amended 2026-09-05): the same stepper, the
 * same increment and the same blank-means-bodyweight rule as set entry's, above the rep count it
 * used to sit fixed beside. The other unit reads underneath as it does on the sheet and in the
 * start dialog, so a number typed here means what it means there; absent for a bodyweight set
 * and while the field will not read, rather than a conversion of nothing.
 */
@Composable
private fun WeightField(
    running: GuidedRunning,
    unit: WeightUnit,
    onWeightChanged: (String) -> Unit,
    onWeightStepped: (Int) -> Unit,
) {
    StepperField(
        label = "Weight (${unit.name.lowercase()})",
        value = running.weight,
        onValueChange = onWeightChanged,
        onStep = onWeightStepped,
        placeholder = "Bodyweight",
        supporting = running.weightKg?.let { WeightFormatter.format(it, unit).secondary },
        keyboardType = KeyboardType.Decimal,
    )
}

/** "LOG SET n" button's detail line — the reading unit only, matching `RestPanel.kt`'s. */
private fun logButtonDetail(
    weight: WeightDisplay,
    reps: String,
): String = "${weight.primary} × $reps"

/**
 * What the exercise came to, and what is next if anything is (US-05a).
 *
 * US-43 / ADR-0035: `RepMascot` plays beside "Done" — the exercise is finished, so nothing here
 * is a tap this competes with.
 */
@Composable
private fun ExerciseSummary(
    running: GuidedRunning,
    unit: WeightUnit,
    onStartNext: (SessionExerciseRow) -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(GymDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            EyebrowLabel(text = "Done", color = MaterialTheme.colorScheme.primary)
            RepMascot(modifier = Modifier.height(GymDimens.MascotInline))
        }
        Text(text = running.exerciseName, style = MaterialTheme.typography.headlineSmall)
        HorizontalDivider(
            thickness = GymDimens.StructuralRuleThickness,
            color = MaterialTheme.colorScheme.onSurface,
        )
        NumeralText(
            text = "${running.setsDone} ${"set".orPlural(running.setsDone)} of ${running.targetReps}",
            style = MaterialTheme.typography.headlineMedium,
        )
        NumeralText(
            text =
                buildString {
                    WeightFormatter.formatVolume(running.volumeKg, unit)?.let { append("$it  ·  ") }
                    append(running.elapsed.asMinutesSeconds())
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentBar(total = running.targetSets, done = running.setsDone)

        val next = running.nextUp
        if (next != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
                PrimaryActionButton(
                    text = "Next: ${next.exercise?.name ?: next.sessionExercise.exerciseId.value}",
                    onClick = { onStartNext(next) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onStop,
                    shape = MaterialTheme.shapes.large,
                    modifier =
                        Modifier.sizeIn(minHeight = GymDimens.PrimaryAction, minWidth = GymDimens.PrimaryAction),
                ) {
                    Text("Stop here")
                }
            }
        } else {
            // With nothing left to start, leaving is the screen's most frequent action —
            // exactly what PrimaryActionButton's own doc reserves the role for.
            PrimaryActionButton(text = "Back to workout", onClick = onStop)
        }
    }
}

/**
 * The dialog that starts the flow (US-05a).
 *
 * Separate from [SetEntryDialog] on purpose: "Add set" and "Save set" keep their exact
 * behaviour, so the two-tap path of US-03 cannot be changed from here (ADR-0017). ADR-0033 left
 * this dialog on Material defaults deliberately, naming the fix in advance in its own "what this
 * ADR does not touch" section: "three `StepperField`s in the same dialog shape." This is that —
 * weight, sets and reps all read through the same component the screen this dialog opens into
 * already uses, so starting a walkthrough and correcting a set no longer disagree about how a
 * number is entered.
 */
@Composable
internal fun GuidedSetupDialog(
    setup: GuidedSetup,
    unit: WeightUnit,
    onWeightChanged: (String) -> Unit,
    onWeightStepped: (Int) -> Unit,
    onRepsChanged: (String) -> Unit,
    onRepsStepped: (Int) -> Unit,
    onSetsChanged: (String) -> Unit,
    onSetsStepped: (Int) -> Unit,
    onBegin: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start ${setup.exerciseName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
                StepperField(
                    label = "Weight (${unit.name.lowercase()})",
                    value = setup.weight,
                    onValueChange = onWeightChanged,
                    onStep = onWeightStepped,
                    placeholder = "Bodyweight",
                    // The other unit, live — the same convention set entry and the set editor
                    // already use, so a value typed here reads the same way there does.
                    supporting =
                        setup.weight
                            .trim()
                            .toDoubleOrNull()
                            ?.let { typed ->
                                WeightFormatter.format(UnitConverter.toKilograms(typed, unit), unit).secondary
                            },
                    keyboardType = KeyboardType.Decimal,
                )

                // Weight, Reps, Sets — the same field order the set-entry sheet uses
                // (SetSheets.kt), rather than this dialog's own prior Sets-then-Reps.
                StepperField(
                    label = "Reps",
                    value = setup.reps,
                    onValueChange = onRepsChanged,
                    onStep = onRepsStepped,
                )

                StepperField(
                    label = "Sets",
                    value = setup.sets,
                    onValueChange = onSetsChanged,
                    onStep = onSetsStepped,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onBegin,
                enabled =
                    setup.reps.toIntOrNull()?.let { it >= 1 } == true &&
                        setup.sets.toIntOrNull()?.let { it >= 1 } == true,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            // The touch-target floor its sibling above already carries — missed the first time
            // this dialog was built, caught auditing the same defect that motivated this pass.
            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Cancel")
            }
        },
    )
}

private fun String.orPlural(count: Int): String = if (count == 1) this else "${this}s"

@GymPreviews
@Composable
private fun GuidedMidSetPreview() {
    GymTrackerTheme {
        GuidedExerciseScreen(
            running = previewRunning(),
            unit = WeightUnit.LB,
            restRemaining = null,
            onWeightChanged = {},
            onWeightStepped = {},
            onRepsChanged = {},
            onRepsStepped = {},
            onFinishSet = {},
            onStartNext = {},
            onStop = {},
        )
    }
}

@GymPreviews
@Composable
private fun GuidedRestingPreview() {
    GymTrackerTheme {
        GuidedExerciseScreen(
            running = previewRunning(),
            unit = WeightUnit.LB,
            restRemaining = Duration.ofSeconds(45),
            onWeightChanged = {},
            onWeightStepped = {},
            onRepsChanged = {},
            onRepsStepped = {},
            onFinishSet = {},
            onStartNext = {},
            onStop = {},
        )
    }
}

/**
 * ADR-0011's Turn 4 amendment: 320dp, 130% font scale, the longest exercise name in the bundled
 * database, and a bodyweight movement — the two worst cases frame `4b` names, together, on both
 * the resting hero (`RestHero`) and `GuidedControls`' stepper/log row below it.
 */
@Preview(widthDp = 320, fontScale = 1.3f)
@Composable
private fun GuidedRestingNarrowWorstCasePreview() {
    GymTrackerTheme {
        GuidedExerciseScreen(
            running = previewRunning(exerciseName = WORST_CASE_NAME, weightKg = null),
            unit = WeightUnit.LB,
            restRemaining = Duration.ofSeconds(45),
            onWeightChanged = {},
            onWeightStepped = {},
            onRepsChanged = {},
            onRepsStepped = {},
            onFinishSet = {},
            onStartNext = {},
            onStop = {},
        )
    }
}

private const val WORST_CASE_NAME = "Barbell Incline Bench Press - Medium Grip"

@GymPreviews
@Composable
private fun GuidedCompletePreview() {
    GymTrackerTheme {
        GuidedExerciseScreen(
            running = previewRunning(setsDone = 3, isComplete = true),
            unit = WeightUnit.LB,
            restRemaining = null,
            onWeightChanged = {},
            onWeightStepped = {},
            onRepsChanged = {},
            onRepsStepped = {},
            onFinishSet = {},
            onStartNext = {},
            onStop = {},
        )
    }
}

private fun previewRunning(
    setsDone: Int = 1,
    isComplete: Boolean = false,
    exerciseName: String = "Bench Press",
    weightKg: Double? = 61.23,
): GuidedRunning {
    val row =
        SessionExerciseRow(
            sessionExercise = SessionExercise(SessionExerciseId("se-1"), SessionId("preview"), ExerciseId("bench"), 1),
            exercise = null,
        )
    return GuidedRunning(
        row = row,
        exerciseName = exerciseName,
        weight = if (weightKg == null) "" else "135",
        weightKg = weightKg,
        targetSets = 3,
        targetReps = 12,
        setsDone = setsDone,
        reps = "12",
        isComplete = isComplete,
        volumeKg = 734.8,
        elapsed = Duration.ofMinutes(PREVIEW_ELAPSED_MINUTES),
        nextUp = null,
    )
}

private const val PREVIEW_ELAPSED_MINUTES = 4L
