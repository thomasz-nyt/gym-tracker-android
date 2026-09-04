package com.gymtracker.feature.logging.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.GymLoadRow
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.component.NumeralText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
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
 * **The running state moved to [WarmUpStep], full screen (ADR-0045, Turn 5 file `02`).** This
 * composable now only ever draws the idle trigger — the countdown, "Done", and `RepMascot` that
 * used to render inline here (Turn 3, finding 01 / frame `3a`, "stacked, not a `Row`") are
 * [WarmUpStep]'s problem now, not this one's. [warmUp]'s idle branch is otherwise unchanged.
 */
@Composable
internal fun WarmUpPanel(warmUp: WarmUp) {
    // The running state is drawn full screen by WarmUpStep instead (ADR-0045); a caller that
    // still reaches this function while a warm-up is running is the one case that shouldn't
    // happen, so this draws nothing rather than a second copy of a screen already up elsewhere.
    if (warmUp.elapsed != null) return

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
}

/**
 * Resting, as ADR-0047 redraws ADR-0036's frame: the rest band is ink at all times — only the
 * countdown numeral's own colour flips to accent for the final ten seconds, never the band's
 * container — and "Up next" — the movement, its target, and the comparison to last time — sits
 * below it on the bare ground, not inside a coloured block. `SKIP REST` and `Add set` share one
 * `label.caps` secondary row above the log button, the same shape sub-piece 3 (US-54) already
 * gave the mid-set state's `Add set`/`Add exercise` row. The log button beneath both stays live
 * (ADR-0023: resting never blocks logging) and, since the band is never filled, always stays
 * filled itself — there is no swap left to make.
 *
 * **The design's `+30s` and its audio-cue label ("CUE AT 0:10 & 0:00") are both left out**, a
 * second time — confirmed again when ADR-0036 was written, unrevisited by ADR-0047.
 * `RestTimer.extend()` does not exist yet, and a button that visibly does nothing is worse than
 * an absent one — as is a label promising a sound the phone never makes. Both arrive together
 * with the use case that backs them, rather than as chrome drawn ahead of its behaviour. The
 * countdown numeral's own colour flip at 0:10 is kept: it costs nothing undelivered and is
 * itself the cue.
 *
 * [total] is nullable rather than required because it is a display refinement, not a
 * precondition (ADR-0029/US-29): a member on this exact screen the moment the app is upgraded
 * has a rest already running with no total on record (`RestTimerStore`'s pinned-total migration
 * is additive, so an in-flight rest predates the field entirely). The countdown number itself
 * never depended on it and still renders; only the `"of {total}"` readout is skipped, rather
 * than drawing it against a guessed total.
 *
 * **[justSetRecord] (US-18) adds a banner above the band, rather than replacing it** the way
 * `Redesign.dc.html`'s `2a PR moment` frame does. ADR-0047 makes that frame's own reasoning
 * (drop the countdown to unfilled so only one accent surface is on screen) moot here — the band
 * is never filled outside its own numeral now, so there is no second filled surface to avoid in
 * the first place. The frame's "Beats 95 lb from Sat 26 Jul" comparison line stays left out:
 * [PersonalRecord] carries the new best, not the one it beat, and manufacturing that number here
 * would be inventing a comparison nobody computed (constitution §2.4) rather than reading one
 * back.
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

        RestBand(remaining = remaining, total = total)

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

            RestSecondaryRow(onSkipRest = onSkipRest, onAdjust = onAdjust)

            // "— DON'T WAIT" is cut (ADR-0011's Turn 4 amendment, frame 4c): it was the longest
            // string on the screen, and the value line beneath already says what tapping it will
            // do — ADR-0023's "resting never blocks logging" rule is unchanged, it just no
            // longer needs restating in the button's own label.
            PrimaryActionButton(
                eyebrow = "LOG SET ${upNext.setNumber}",
                detail = logButtonDetail(upNext, unit),
                onClick = onLogNext,
                modifier =
                    Modifier.padding(horizontal = GymDimens.CompactScreenPadding, vertical = GymDimens.Gap),
            )
        } else {
            // US-05: "I can dismiss or skip it. It never blocks logging the next set." upNext is
            // null whenever nothing is logged in this session right now (`DetermineUpNextSet`'s
            // own contract) — which a set deleted out from under an already-running rest produces
            // directly, since the rest itself does not stop when its set does. Without this
            // branch SKIP REST vanished along with Up next and the log button, leaving no control
            // on screen at all: a dead end this app has never had anywhere else. Up next and the
            // log button both need a real set to describe and stay absent; SKIP REST needs
            // nothing but a rest to leave, so it is the one control that always renders.
            RestSecondaryRow(onSkipRest = onSkipRest, onAdjust = onAdjust)
        }
    }
}

/**
 * `SKIP REST` and `Add set` (ADR-0047): the resting state's own `label.caps × 2` secondary row,
 * the same shape sub-piece 3 (US-54) already gave `BottomLogBar`'s mid-set
 * `Add set`/`Add exercise`. `Add set`, not the design's "ADJUST" — see `SessionScaffold.kt`'s
 * `BottomLogBar` for why: this opens the exact sheet that label already names, and
 * `TwoTapSetLoggingTest` matches both strings literally.
 */
@Composable
private fun RestSecondaryRow(
    onSkipRest: () -> Unit,
    onAdjust: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        modifier = Modifier.padding(horizontal = GymDimens.CompactScreenPadding, vertical = GymDimens.TightGap),
    ) {
        TextButton(onClick = onSkipRest, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
            GymText(text = "SKIP REST", role = GymTextRoles.LabelCaps, color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = onAdjust, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
            GymText(text = "Add set", role = GymTextRoles.LabelCaps, color = MaterialTheme.colorScheme.primary)
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
 * The rest band (ADR-0047, amending ADR-0036): ink at all times — `inverseSurface`, never a
 * second filled element competing with the log button — and 56dp, not a hero block. Only the
 * countdown numeral's own colour flips to accent for the final ten seconds; the band's container
 * never does. `label.caps` "REST", `numeral.md` (24sp, the closer of this app's two numeral
 * roles to the design's literal 28sp — see the ADR for why a new role wasn't added for one call
 * site), and a muted `"of {total}"` meta, all on one baseline row.
 *
 * [total] stays nullable — see [RestingBody]'s doc for why — and the `"of {total}"` readout is
 * skipped when it is absent, rather than rendering against a guessed total.
 */
@Composable
private fun RestBand(
    remaining: Duration,
    total: Duration?,
) {
    val urgent = remaining <= FINAL_STRETCH
    val containerColor = MaterialTheme.colorScheme.inverseSurface
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    val numeralColor = if (urgent) MaterialTheme.colorScheme.primary else contentColor

    GymDivider()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GymDimens.StepperTarget)
                .background(containerColor)
                .padding(horizontal = GymDimens.CompactScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
    ) {
        GymText(text = "REST", role = GymTextRoles.LabelCaps, color = contentColor)
        GymText(
            text = remaining.asMinutesSeconds(),
            role = GymTextRoles.NumeralMd,
            color = numeralColor,
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
    GymDivider()
}

/** ADR-0047: the rest band's numeral flips to accent for the last ten seconds of a rest. */
private const val FINAL_STRETCH_SECONDS = 10L
private val FINAL_STRETCH: Duration = Duration.ofSeconds(FINAL_STRETCH_SECONDS)
private const val MUTED_ALPHA = 0.6f

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
