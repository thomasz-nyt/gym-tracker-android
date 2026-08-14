package com.gymtracker.feature.logging.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.NumeralText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.progress.PersonalRecord
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.session.SessionProgress
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.SessionExerciseRow
import com.gymtracker.feature.logging.WarmUp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The warm-up: a stopwatch, and nothing else (US-28, ADR-0021).
 *
 * Idle, it is one quiet text button — the warm-up is the least of what this screen does and it
 * does not get to look like the most. Running, it counts up at [displayLarge]'s size — ADR-0029
 * gave that role to "the countdown you read from across the room," and a warm-up is read from
 * the same distance.
 *
 * What is deliberately absent: a weight field, a rep field, an exercise name, and any "save".
 * There is nothing to save. Stopping it discards it, which is why the control says "Done"
 * rather than anything that sounds like it writes a row.
 */
@Composable
internal fun WarmUpPanel(warmUp: WarmUp) {
    val elapsed = warmUp.elapsed

    if (elapsed == null) {
        TextButton(
            onClick = warmUp.onStart,
            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
        ) {
            Text("Start warm-up")
        }
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                EyebrowLabel(text = "Warm-up", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = elapsed.asCountdown(),
                    style = MaterialTheme.typography.displayLarge,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Warm-up ${elapsed.asCountdown()} elapsed, not recorded"
                        },
                )
            }
            TextButton(
                onClick = warmUp.onStop,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Done")
            }
        }
    }
}

/**
 * Resting, as ADR-0029 draws it: the countdown is a full-bleed accent surface, and "Up next" —
 * the movement, its target, and the comparison to last time — sits below it on the bare ground,
 * not inside the coloured block. The log button beneath both stays live and says
 * "DON'T WAIT" (ADR-0023's rule, restated for the new copy): resting never blocks logging.
 *
 * **The design's `+30s` and its audio-cue label ("CUE AT 0:10 & 0:00") are both left out.**
 * `RestTimer.extend()` does not exist yet, and a button that visibly does nothing is worse than
 * an absent one — as is a label promising a sound the phone never makes. Both arrive together
 * with the use case that backs them, rather than as chrome drawn ahead of its behaviour.
 *
 * The design's countdown progress bar needs the configured rest *duration*, not just what is
 * left — [SessionUiState][com.gymtracker.feature.logging.SessionUiState] does not carry that
 * today, and faking a fraction from [remaining] alone would draw a bar that resets to full every
 * time the countdown ticks. Left out rather than built wrong; the fix is threading
 * `RestTimerStore.defaultRest` through, not a layout change.
 *
 * **[justSetRecord] (US-18) adds a banner above the countdown, rather than replacing it** the
 * way `Redesign.dc.html`'s `2a PR moment` frame does — that frame drops the countdown to an
 * unfilled block so only one accent-filled surface is on screen, matching ADR-0029's "exactly
 * one filled element" rule; reproducing that swap needs the countdown block built two ways, and
 * a member's own history says this fires rarely. Two filled surfaces for one rest cycle, on the
 * rare set that earns it, is the simplification — not a rule this file otherwise breaks. The
 * frame's "Beats 95 lb from Sat 26 Jul" comparison line is left out entirely: [PersonalRecord]
 * carries the new best, not the one it beat, and manufacturing that number here would be
 * inventing a comparison nobody computed (constitution §2.4) rather than reading one back.
 */
@Composable
internal fun RestingBody(
    remaining: Duration,
    upNext: UpNextSet?,
    exerciseName: String?,
    progress: SessionProgress?,
    exercises: List<SessionExerciseRow>,
    unit: WeightUnit,
    justSetRecord: PersonalRecord?,
    onSkipRest: () -> Unit,
    onLogNext: () -> Unit,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (justSetRecord != null) {
            PersonalRecordBanner(
                record = justSetRecord,
                exerciseName =
                    exercises
                        .firstOrNull { it.sessionExercise.exerciseId == justSetRecord.exerciseId }
                        ?.exercise
                        ?.name,
                unit = unit,
            )
        }

        RestCountdownBanner(remaining = remaining, onSkipRest = onSkipRest)

        if (upNext != null) {
            UpNext(
                upNext = upNext,
                exerciseName = exerciseName,
                nextMovementName = nextMovementName(progress, exercises),
                unit = unit,
                modifier = Modifier.padding(GymDimens.ScreenPadding).weight(1f, fill = false),
            )

            // Deliberately *not* wrapped in Modifier.verticalScroll to give performScrollTo() a
            // scrollable ancestor — SessionMovements.kt's BottomLogBar tried exactly that and
            // it made Compose's test idling hang instead of the throw it was meant to avoid,
            // for as long as the caller's own timeout. No test currently targets this row, but
            // if one needs to, the proven fix is putting it inside a real LazyColumn (as
            // SessionPlan's log bar is now), not a bare verticalScroll on an already-fitting Row.
            Row(
                horizontalArrangement = Arrangement.spacedBy(GymDimens.HairGap),
                modifier = Modifier.padding(horizontal = GymDimens.ScreenPadding, vertical = GymDimens.Gap),
            ) {
                PrimaryActionButton(
                    eyebrow = "LOG SET ${upNext.setNumber} — DON'T WAIT",
                    detail = logButtonDetail(upNext, unit),
                    onClick = onLogNext,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onAdjust,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.sizeIn(minHeight = GymDimens.PrimaryAction, minWidth = GymDimens.PrimaryAction),
                ) {
                    // "Add set", not the design's "ADJUST" — see SessionScaffold.kt's
                    // BottomLogBar for why: this opens the exact sheet that label already names,
                    // and TwoTapSetLoggingTest matches that string literally.
                    Text("Add set")
                }
            }
        }
    }
}

/**
 * The inline PR moment (US-18, `2a PR moment`): the accent, the new best, and which movement it
 * was — the celebratory moment the redesign audit called "the only one in the app," which is
 * exactly why nothing here explains itself with a full sentence. The number does the talking.
 */
@Composable
private fun PersonalRecordBanner(
    record: PersonalRecord,
    exerciseName: String?,
    unit: WeightUnit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            EyebrowLabel(text = "Personal record", color = MaterialTheme.colorScheme.onPrimary)
            val weight = WeightFormatter.format(record.weightKg, unit)
            NumeralText(
                text = "${weight.primary} × ${record.reps}",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = exerciseName ?: record.exerciseId.value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** The accent-filled countdown block: eyebrow, the giant number, and `SKIP REST`. */
@Composable
private fun RestCountdownBanner(
    remaining: Duration,
    onSkipRest: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            EyebrowLabel(text = "Rest", color = MaterialTheme.colorScheme.onPrimary)
            Text(
                text = remaining.asCountdown(),
                style = MaterialTheme.typography.displayLarge,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Rest ${remaining.asCountdown()} remaining"
                    },
            )
            OutlinedButton(
                onClick = onSkipRest,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.StepperTarget),
            ) {
                Text("SKIP REST")
            }
        }
    }
}

/**
 * What the next set will be, and what the same movement was last time (ADR-0023, ADR-0029).
 *
 * [nextMovementName] is the "then Seated Cable Rows" clause — present only for a session
 * started from a routine ([SessionProgress.orderIsAPlan]), the same rule the segment bar in
 * [SessionScaffold] follows: a freestyle session's order is add-order, not a plan, and this
 * screen does not claim otherwise.
 */
@Composable
private fun UpNext(
    upNext: UpNextSet,
    exerciseName: String?,
    nextMovementName: String?,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val next = WeightFormatter.format(upNext.prefill.weight?.let { UnitConverter.toKilograms(it, unit) }, unit)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
        EyebrowLabel(text = "Up next", color = MaterialTheme.colorScheme.primary)
        Text(
            text = exerciseName ?: upNext.exerciseId.value,
            style = MaterialTheme.typography.headlineSmall,
        )
        NumeralText(
            text =
                buildString {
                    // No "of N": UpNextSet's own doc is explicit that the app does not know how
                    // many sets are intended, so there is no total here to render (ADR-0023).
                    append("Set ${upNext.setNumber}")
                    nextMovementName?.let { append("  ·  then $it") }
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GymDivider()
        // The reading unit alone at 44sp, and the conversion on its own quieter line beneath.
        // Both on one line is 20-odd characters at that size: it wrapped, and the wrap pushed
        // the comparison below it off the panel. ADR-0008 wants both units present, not both
        // equally loud — this is the same primary/secondary split the set rows already use.
        NumeralText(
            text = "${next.primary} × ${upNext.prefill.reps}",
            style = MaterialTheme.typography.headlineMedium,
        )
        next.secondary?.let { secondary ->
            NumeralText(
                text = secondary,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        upNext.comparison?.let { last ->
            val previous = WeightFormatter.format(last.weightKg, unit)
            NumeralText(
                text = "Last ${last.performedAt.asDay()}  ·  ${previous.primary} × ${last.reps}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The first movement in [SessionProgress.stillToCome], only when the session's order is a plan. */
private fun nextMovementName(
    progress: SessionProgress?,
    exercises: List<SessionExerciseRow>,
): String? {
    val next = progress?.takeIf { it.orderIsAPlan }?.stillToCome?.firstOrNull() ?: return null
    return exercises.firstOrNull { it.sessionExercise.id == next.id }?.exercise?.name ?: next.exerciseId.value
}

/**
 * "LOG SET n" button's detail line — the reading unit only, matching [UpNext]'s big line.
 *
 * The conversion is deliberately absent here rather than appended: the button is half the
 * screen's width beside `Add set`, and both units at `titleMedium` wrapped to a second line
 * that the button's own height then clipped. The kilo figure is on screen directly above,
 * in [UpNext], for anyone who reads in that unit (ADR-0008).
 */
private fun logButtonDetail(
    upNext: UpNextSet,
    unit: WeightUnit,
): String {
    val weight = WeightFormatter.format(upNext.prefill.weight?.let { UnitConverter.toKilograms(it, unit) }, unit)
    return "${weight.primary} × ${upNext.prefill.reps}"
}

/** The day a set happened, for the rest panel's comparison line. */
private fun Instant.asDay(): String =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(this)

/**
 * mm:ss, so 90 seconds reads "1:30" rather than "PT1M30S".
 *
 * Arithmetic on [Duration.getSeconds] rather than `toMinutesPart`/`toSecondsPart`, which are
 * API 31 and would crash on the API 26 devices `tech-stack.md` supports.
 */
private fun Duration.asCountdown(): String =
    "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60
