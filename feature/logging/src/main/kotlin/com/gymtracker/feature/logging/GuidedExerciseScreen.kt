package com.gymtracker.feature.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymPreviews
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Duration

/**
 * Walking through one exercise, set by set (US-05a, ADR-0017).
 *
 * Everything on screen is one of three things: what you are lifting, which set you are on, and
 * the one button that ends it. The rep count is a field rather than a label because the target
 * is a prefill and not a promise — logging 12 when 9 were managed would fabricate a value
 * (constitution §2.4).
 */
@Composable
internal fun GuidedExerciseScreen(
    running: GuidedRunning,
    unit: WeightUnit,
    restRemaining: Duration?,
    onRepsChanged: (String) -> Unit,
    onFinishSet: () -> Unit,
    onStartNext: (SessionExerciseRow) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(GymDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        ) {
            Text(
                text = running.exerciseName,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = running.weightKg.describe(unit),
                style = MaterialTheme.typography.titleMedium,
            )

            if (running.isComplete) {
                ExerciseSummary(running, unit, onStartNext, onStop)
            } else {
                SetInProgress(running, restRemaining, onRepsChanged, onFinishSet, onStop)
            }
        }
    }
}

/** The set you are about to do, or the rest before it. */
@Composable
private fun SetInProgress(
    running: GuidedRunning,
    restRemaining: Duration?,
    onRepsChanged: (String) -> Unit,
    onFinishSet: () -> Unit,
    onStop: () -> Unit,
) {
    Text(
        text = "Set ${running.setsDone + 1} of ${running.targetSets}",
        style = MaterialTheme.typography.titleLarge,
    )

    // Display-size, at the size you can read from where you are actually standing (ADR-0016) —
    // the same treatment the session screen's rest banner gets. The countdown gates nothing:
    // "Finish set" stays live throughout, which is US-05's "it never blocks logging the next
    // set" held to structurally.
    Text(
        text = restRemaining?.let { "Rest ${it.asMinutesSeconds()}" } ?: "Go",
        style = MaterialTheme.typography.displayMedium,
    )

    OutlinedTextField(
        value = running.reps,
        onValueChange = onRepsChanged,
        label = { Text("Reps") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "Target ${running.targetReps}. Change it if you managed a different number.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )

    // The one primary action this screen state exists for (ADR-0016).
    PrimaryActionButton(
        text = "Finish set",
        onClick = onFinishSet,
        enabled = running.reps.toIntOrNull()?.let { it >= 1 } == true,
    )

    TextButton(onClick = onStop, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
        Text("Stop")
    }
}

/** What the exercise came to, and what is next if anything is (US-05a). */
@Composable
private fun ExerciseSummary(
    running: GuidedRunning,
    unit: WeightUnit,
    onStartNext: (SessionExerciseRow) -> Unit,
    onStop: () -> Unit,
) {
    Text(text = "Done", style = MaterialTheme.typography.titleLarge)
    Text(
        text = "${running.setsDone} ${"set".orPlural(running.setsDone)} of ${running.targetReps}",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text =
            buildString {
                WeightFormatter.formatVolume(running.volumeKg, unit)?.let { append("$it  ·  ") }
                append(running.elapsed.asMinutesSeconds())
            },
        style = MaterialTheme.typography.bodyMedium,
    )

    val next = running.nextUp
    if (next != null) {
        PrimaryActionButton(
            text = "Next: ${next.exercise?.name ?: next.sessionExercise.exerciseId.value}",
            onClick = { onStartNext(next) },
        )
    }

    TextButton(onClick = onStop, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
        Text(if (next == null) "Back to workout" else "Stop here")
    }
}

/**
 * The dialog that starts the flow (US-05a).
 *
 * Separate from [SetEntryDialog] on purpose: "Add set" and "Save set" keep their exact
 * behaviour, so the two-tap path of US-03 cannot be changed from here (ADR-0017).
 */
@Composable
internal fun GuidedSetupDialog(
    setup: GuidedSetup,
    unit: WeightUnit,
    onWeightChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onSetsChanged: (String) -> Unit,
    onBegin: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start ${setup.exerciseName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
                OutlinedTextField(
                    value = setup.weight,
                    onValueChange = onWeightChanged,
                    label = { Text("Weight (${unit.name.lowercase()})") },
                    placeholder = { Text("Bodyweight") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                setup.weight
                    .trim()
                    .toDoubleOrNull()
                    ?.let { typed -> WeightFormatter.format(UnitConverter.toKilograms(typed, unit), unit).secondary }
                    ?.let { other -> Text(other, style = MaterialTheme.typography.bodySmall) }

                Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
                    OutlinedTextField(
                        value = setup.sets,
                        onValueChange = onSetsChanged,
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = setup.reps,
                        onValueChange = onRepsChanged,
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Both units, or "Bodyweight" when none was recorded — never "0 lb" (ADR-0008). */
private fun Double?.describe(unit: WeightUnit): String {
    val weight = WeightFormatter.format(this, unit)
    return buildString {
        append(weight.primary)
        weight.secondary?.let { append("  ·  $it") }
    }
}

/**
 * mm:ss. Arithmetic on [Duration.getSeconds] rather than `toMinutesPart`, which is API 31 and
 * would crash on the API 26 devices `tech-stack.md` supports.
 */
private fun Duration.asMinutesSeconds(): String =
    "%d:%02d".format(seconds / SECONDS_IN_MINUTE, seconds % SECONDS_IN_MINUTE)

private fun String.orPlural(count: Int): String = if (count == 1) this else "${this}s"

private const val SECONDS_IN_MINUTE = 60

@GymPreviews
@Composable
private fun GuidedRunningPreview() {
    val row =
        SessionExerciseRow(
            sessionExercise =
                SessionExercise(
                    SessionExerciseId("se-1"),
                    SessionId("preview"),
                    ExerciseId("bench"),
                    1,
                ),
            exercise = null,
        )

    GymTrackerTheme {
        GuidedExerciseScreen(
            running =
                GuidedRunning(
                    row = row,
                    exerciseName = "Bench Press",
                    weightKg = 61.23,
                    targetSets = 3,
                    targetReps = 12,
                    setsDone = 1,
                    reps = "12",
                    isComplete = false,
                    volumeKg = 734.8,
                    elapsed = Duration.ofMinutes(4),
                    nextUp = null,
                ),
            unit = WeightUnit.LB,
            restRemaining = Duration.ofSeconds(45),
            onRepsChanged = {},
            onFinishSet = {},
            onStartNext = {},
            onStop = {},
        )
    }
}
