package com.gymtracker.feature.logging.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.GymLoadRow
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.component.NumeralText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.RepMascot
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.progress.PersonalRecord
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.session.SessionProgress
import com.gymtracker.core.domain.set.SetPrefill
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.SessionExerciseRow
import com.gymtracker.feature.logging.WarmUp
import com.gymtracker.feature.logging.asMinutesSeconds
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
 *
 * US-43 / ADR-0035: running, `RepMascot` plays beside "Done" — there is nothing to tap here but
 * "Done" itself, so nothing is competing with it for attention the way a mid-set control would.
 *
 * **Stacked, not a `Row` (`Redesign.dc.html` Turn 3, finding 01 / frame `3a`).** 312dp of usable
 * width; a tabular `18:47` at `displayLarge`'s 104sp is ~230 of it, and the old layout put "Done"
 * and `RepMascot` beside it on the same line — asking "Done" to fit in −14dp. The countdown now
 * owns a full-width line of its own, so no control ever shares its axis and the arithmetic that
 * produced the overflow cannot recur regardless of how wide the number gets. `Done` and Rep sit
 * in a second row beneath a rule, both sized to [GymDimens.StepperTarget] (56dp) — "where 56dp is
 * plenty," per the frame.
 */
@Composable
internal fun WarmUpPanel(warmUp: WarmUp) {
    val elapsed = warmUp.elapsed

    if (elapsed == null) {
        // ADR-0011's Turn 4 amendment (frame 4c): a 44dp row between 2px rules, replacing the
        // floating 17sp red sentence this used to be — label.caps in the accent, matching the
        // "secondary buttons" row this role is named for. The string itself stays exactly
        // "Start warm-up", sentence case: WarmUpPanelScreenTest matches it literally, the same
        // tripwire the amendment's "frames vs. this repo's own tripwires" note names.
        GymDivider()
        TextButton(
            onClick = warmUp.onStart,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            modifier = Modifier.fillMaxWidth().height(GymDimens.WarmUpRowHeight),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                GymText(
                    text = "Start warm-up",
                    role = GymTextRoles.LabelCaps,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
        GymDivider()
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            // ADR-0021's "not recorded" rule used to live only in the contentDescription below;
            // Turn 3 puts it on screen where it can be read, not just announced.
            EyebrowLabel(text = "Warm-up · not recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
            GymText(
                text = elapsed.asMinutesSeconds(),
                role = GymTextRoles.DisplayTimer,
                semantics = { contentDescription = "Warm-up ${elapsed.asMinutesSeconds()} elapsed, not recorded" },
            )
            GymDivider()
            Row(
                horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = warmUp.onStop,
                    // Shape.kt's own class doc names this exact trap: OutlinedButton's default
                    // shape is CornerFull, not one of GymShapes's roles, so it stays a pill
                    // unless a shape is passed explicitly — confirmed on device, not caught by
                    // any test (nothing here asserts geometry).
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f).height(GymDimens.StepperTarget),
                ) {
                    Text("Done")
                }
                RepMascot(modifier = Modifier.height(GymDimens.StepperTarget))
            }
        }
    }
}

/**
 * Resting, as ADR-0036 redraws ADR-0029's frame: the countdown block is ink while resting is
 * calm and takes the accent fill only for the final ten seconds, and "Up next" — the movement,
 * its target, and the comparison to last time — sits below it on the bare ground, not inside the
 * coloured block. The log button beneath both stays live and says "DON'T WAIT" (ADR-0023's rule,
 * restated for the new copy): resting never blocks logging; ADR-0036 additionally steps its fill
 * back to outlined for the same ten seconds the countdown block itself is filled, so the two are
 * never both accent-filled together.
 *
 * **The design's `+30s` and its audio-cue label ("CUE AT 0:10 & 0:00") are both left out**, a
 * second time — confirmed again when ADR-0036 was written. `RestTimer.extend()` does not exist
 * yet, and a button that visibly does nothing is worse than an absent one — as is a label
 * promising a sound the phone never makes. Both arrive together with the use case that backs
 * them, rather than as chrome drawn ahead of its behaviour. The countdown block's own colour
 * flip at 0:10 is kept: it costs nothing undelivered and is itself the cue.
 *
 * [total] is nullable rather than required because it is a display refinement, not a
 * precondition (ADR-0029/US-29): a member on this exact screen the moment the app is upgraded
 * has a rest already running with no total on record (`RestTimerStore`'s pinned-total migration
 * is additive, so an in-flight rest predates the field entirely). The countdown number itself
 * never depended on it and still renders; only the bar and the `"of {total}"` readout are
 * skipped, rather than drawing them against a guessed total.
 *
 * **[justSetRecord] (US-18) adds a banner above the countdown, rather than replacing it** the
 * way `Redesign.dc.html`'s `2a PR moment` frame does — that frame drops the countdown to an
 * unfilled block so only one accent-filled surface is on screen, matching ADR-0029's "exactly
 * one filled element" rule. `PrimaryActionButton`'s `outlined` parameter (ADR-0036) is now the
 * exact "countdown block built two ways" mechanism that swap needs, but the swap itself stays
 * out of scope here: with the countdown no longer accent-filled outside the final ten seconds,
 * the two-filled-surfaces problem the PR banner posed has mostly dissolved on its own, and a
 * member's own history says this fires rarely regardless. The frame's "Beats 95 lb from Sat 26
 * Jul" comparison line is left out entirely: [PersonalRecord] carries the new best, not the one
 * it beat, and manufacturing that number here would be inventing a comparison nobody computed
 * (constitution §2.4) rather than reading one back.
 */
@Composable
internal fun RestingBody(
    remaining: Duration,
    total: Duration?,
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

        RestCountdownBanner(remaining = remaining, total = total, onSkipRest = onSkipRest)

        if (upNext != null) {
            // A display refinement, not a promise: read back from the SessionExercise's own
            // copied-at-start target (ADR-0027), never invented here — see UpNext's own KDoc.
            val targetSets =
                exercises
                    .firstOrNull { it.sessionExercise.id == upNext.sessionExerciseId }
                    ?.sessionExercise
                    ?.target
                    ?.sets

            UpNext(
                upNext = upNext,
                exerciseName = exerciseName,
                nextMovementName = nextMovementName(progress, exercises),
                targetSets = targetSets,
                unit = unit,
                modifier = Modifier.padding(GymDimens.CompactScreenPadding).weight(1f, fill = false),
            )

            // Deliberately *not* wrapped in Modifier.verticalScroll to give performScrollTo() a
            // scrollable ancestor — SessionMovements.kt's BottomLogBar tried exactly that and
            // it made Compose's test idling hang instead of the throw it was meant to avoid,
            // for as long as the caller's own timeout. No test currently targets this row, but
            // if one needs to, the proven fix is putting it inside a real LazyColumn (as
            // SessionPlan's log bar is now), not a bare verticalScroll on an already-fitting Row.
            Row(
                horizontalArrangement = Arrangement.spacedBy(GymDimens.HairGap),
                modifier = Modifier.padding(horizontal = GymDimens.CompactScreenPadding, vertical = GymDimens.Gap),
            ) {
                PrimaryActionButton(
                    // "— DON'T WAIT" is cut (ADR-0011's Turn 4 amendment, frame 4c): it was the
                    // longest string on the screen, and the value line beneath already says
                    // what tapping it will do — ADR-0023's "resting never blocks logging" rule
                    // is unchanged, it just no longer needs restating in the button's own label.
                    eyebrow = "LOG SET ${upNext.setNumber}",
                    detail = logButtonDetail(upNext, unit),
                    onClick = onLogNext,
                    // ADR-0036: steps back to outlined for exactly the seconds the countdown
                    // block above is itself accent-filled, so the two are never both filled.
                    outlined = remaining <= FINAL_STRETCH,
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

/**
 * The countdown block (ADR-0036): ink while resting is calm, and red only for the final ten
 * seconds — the one moment the countdown, not the log button, is the thing to notice. Never
 * both filled at once; [RestingBody] steps the log button back to outlined for exactly the
 * seconds this block is accent-filled, so "exactly one filled element" (ADR-0029) holds through
 * the swap, not just around it.
 *
 * [total] stays nullable through to here — see [RestingBody]'s doc for why — and the bar plus
 * the `"of {total}"` readout are both skipped together when it is absent, rather than one
 * rendering against a total the other does not have.
 */
@Composable
private fun RestCountdownBanner(
    remaining: Duration,
    total: Duration?,
    onSkipRest: () -> Unit,
) {
    val urgent = remaining <= FINAL_STRETCH
    val containerColor =
        if (urgent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inverseSurface
    val contentColor =
        if (urgent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inverseOnSurface

    Surface(
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.CompactScreenPadding),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            EyebrowLabel(text = "Rest", color = contentColor)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
                GymText(
                    text = remaining.asMinutesSeconds(),
                    role = GymTextRoles.DisplayTimer,
                    color = contentColor,
                    semantics = { contentDescription = "Rest ${remaining.asMinutesSeconds()} remaining" },
                )
                if (total != null) {
                    GymText(
                        text = "of ${total.asMinutesSeconds()}",
                        role = GymTextRoles.Meta,
                        color = contentColor.copy(alpha = MUTED_ALPHA),
                    )
                }
            }
            if (total != null) {
                RestProgressBar(
                    remaining = remaining,
                    total = total,
                    fillColor = contentColor,
                    trackColor = contentColor.copy(alpha = REST_BAR_TRACK_ALPHA),
                )
            }
            OutlinedButton(
                onClick = onSkipRest,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.StepperTarget),
            ) {
                Text("SKIP REST")
            }
        }
    }
}

/**
 * How much rest is left, as a shrinking bar — the same track-and-fill shape
 * `WeeklyVolumeScreen`'s `VolumeBar` already uses. [fillColor] and [trackColor] are the block's
 * own content colour at two alphas (ADR-0036), the same two-weight idiom [GymDivider]'s
 * ink/outlineVariant pairing already uses elsewhere on this screen, chosen so the bar reads
 * correctly in both the calm and urgent states without a colour of its own to keep in sync with
 * the swap above.
 *
 * Carries no semantics: the countdown number immediately above already announces "Rest N
 * remaining," and a bar repeating that as a percentage would be noise, not a second fact.
 */
@Composable
private fun RestProgressBar(
    remaining: Duration,
    total: Duration,
    fillColor: Color,
    trackColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GymDimens.VolumeBarHeight)
                .background(trackColor)
                .clearAndSetSemantics {},
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(remaining.fractionOf(total))
                    .fillMaxHeight()
                    .background(fillColor),
        )
    }
}

/** Never negative, never past full — a rest just started or one mid-tick beyond `total` alike. */
private fun Duration.fractionOf(total: Duration): Float =
    if (total.isZero || total.isNegative) {
        0f
    } else {
        (seconds.toFloat() / total.seconds.toFloat()).coerceIn(0f, 1f)
    }

/** ADR-0036: the countdown block flips to accent-filled for the last ten seconds of a rest. */
private const val FINAL_STRETCH_SECONDS = 10L
private val FINAL_STRETCH: Duration = Duration.ofSeconds(FINAL_STRETCH_SECONDS)
private const val MUTED_ALPHA = 0.6f

/** Faint enough that the giant countdown number stays the loudest thing on the surface. */
private const val REST_BAR_TRACK_ALPHA = 0.3f

/**
 * What the next set will be, and what the same movement was last time (ADR-0023, ADR-0029).
 *
 * [nextMovementName] is the "then Seated Cable Rows" clause — present only for a session
 * started from a routine ([SessionProgress.orderIsAPlan]), the same rule the segment bar in
 * [SessionScaffold] follows: a freestyle session's order is add-order, not a plan, and this
 * screen does not claim otherwise.
 *
 * [targetSets] is a *display* refinement (ADR-0011's Turn 4 amendment, frame `4c`): the routine
 * target copied onto this movement's [com.gymtracker.core.domain.model.SessionExercise] at
 * session start (ADR-0027), read by [RestingBody] from the matching [SessionExerciseRow] rather
 * than invented here. Still null for a freestyle movement with no target — `UpNextSet`'s own
 * KDoc is explicit that [UpNextSet.setNumber] alone carries no total to render ("the 3 does not
 * exist to render"), so "SET n OF m" only ever appears when a real target backs the m.
 */
@Composable
private fun UpNext(
    upNext: UpNextSet,
    exerciseName: String?,
    nextMovementName: String?,
    targetSets: Int?,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val next = WeightFormatter.format(upNext.prefill.weight?.let { UnitConverter.toKilograms(it, unit) }, unit)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
        EyebrowLabel(text = "Up next", color = MaterialTheme.colorScheme.primary)
        GymText(text = exerciseName ?: upNext.exerciseId.value, role = GymTextRoles.TitleLg)
        GymText(
            text = if (targetSets != null) "SET ${upNext.setNumber} OF $targetSets" else "SET ${upNext.setNumber}",
            role = GymTextRoles.LabelCaps,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Its own line rather than dot-joined onto "Set n" (ADR-0011's Turn 4 amendment, cause
        // 3): a `·` sentence breaks at any point in a 320dp column, which is what left an
        // orphan "then Seated Cable Rows" tail on its own line above.
        nextMovementName?.let {
            GymText(text = "Then $it", role = GymTextRoles.Meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GymDivider()
        // The split baseline row (GymLoadRow), not one formatted string — the fix for the
        // "Bodyweight × 12" wrap this exact line produced (ADR-0011's Turn 4 amendment, cause
        // 4). The kg conversion (next.secondary) is dropped here entirely (ADR-0008's Turn 4
        // amendment) rather than kept on its own quieter line.
        GymLoadRow(
            number = next.number,
            unit = next.unit,
            wordFallback = next.primary,
            reps = upNext.prefill.reps.toString(),
            numeralRole = GymTextRoles.NumeralLg,
            wordRole = GymTextRoles.WordUnit,
        )
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
 * ADR-0011's Turn 4 amendment: 320dp, 130% font scale, the longest exercise name in the bundled
 * database, and a bodyweight movement — the two worst cases frame `4c` names, together. Previews
 * [RestingBody] directly rather than through the full `LoggingScreen`/`SessionUiState` stack:
 * this is a layout check on the composable this pass actually changed, not a state-wiring one.
 */
@Preview(widthDp = 320, fontScale = 1.3f)
@Composable
private fun RestingBodyNarrowWorstCasePreview() {
    val appearance =
        SessionExercise(
            id = SessionExerciseId("preview-worst-case"),
            sessionId = SessionId("preview"),
            exerciseId = ExerciseId("preview-worst-case"),
            position = 1,
            target = MovementTarget(sets = 5, reps = 12, weightKg = null),
        )
    GymTrackerTheme {
        RestingBody(
            remaining = Duration.ofSeconds(30),
            total = Duration.ofSeconds(60),
            upNext =
                UpNextSet(
                    sessionExerciseId = appearance.id,
                    exerciseId = appearance.exerciseId,
                    setNumber = 4,
                    prefill = SetPrefill(weight = null, reps = 12),
                    comparison = null,
                ),
            exerciseName = "Barbell Incline Bench Press - Medium Grip",
            progress = null,
            exercises = listOf(SessionExerciseRow(appearance, exercise = null, sets = emptyList())),
            unit = WeightUnit.LB,
            justSetRecord = null,
            onSkipRest = {},
            onLogNext = {},
            onAdjust = {},
        )
    }
}
