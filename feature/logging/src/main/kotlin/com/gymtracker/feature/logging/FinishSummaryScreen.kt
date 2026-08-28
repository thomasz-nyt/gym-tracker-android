package com.gymtracker.feature.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.progress.PersonalRecord
import com.gymtracker.core.domain.session.PerformedExercise
import com.gymtracker.core.domain.session.SessionDetail
import com.gymtracker.core.domain.session.SessionSummary
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * What a workout added up to (US-31), shown once the confirm dialog has already been accepted.
 *
 * "Showing the work is a better check than asking are you sure" — the confirm dialog itself is
 * unchanged (`FinishWorkoutDialog`); this is what replaces the plain return to the session list
 * once the member has said yes. It has one exit, [onDone], per ADR-0016's one-primary-action
 * rule: there is nothing left to decide here, only to acknowledge.
 *
 * [records] is already deduplicated to the best per (exercise, reps) by
 * [com.gymtracker.core.domain.progress.PersonalRecordsAchievedIn] — a section listing every
 * intermediate improvement in one workout would be noise, not news.
 *
 * Unlike `WorkoutDetailScreen` or `WeeklyVolumeScreen`, this is not a nav destination and owns
 * no `Scaffold` or screen padding of its own: [SessionBody] already provides both, the same way
 * it does for `ActiveSession`, which this sits beside in that composable's `when`.
 */
@Composable
internal fun FinishSummaryScreen(
    detail: SessionDetail,
    records: List<PersonalRecord>,
    unit: WeightUnit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = detail.exercises.associate { it.sessionExercise.exerciseId to it.exercise?.name }

    Column(
        // No padding here — this screen's own KDoc is explicit that SessionBody already
        // provides it; adding it here would double it.
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
    ) {
        // ADR-0011's Turn 4 amendment: the same two causes HistoryScreen's row had — an
        // unbounded title and a dot-joined stats sentence — fixed the same way here.
        GymText(text = "Workout complete", role = GymTextRoles.TitleLg)
        // US-32 (ADR-0028): leads with the routine this session was started from, the same
        // way HistoryScreen's row does — falling back to "Freestyle" for an ordinary start.
        GymText(
            text =
                detail.summary.session.routine
                    ?.name ?: "Freestyle",
            role = GymTextRoles.TitleMd,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GymDimens.MetricFlowRowGapHorizontal),
            verticalArrangement = Arrangement.spacedBy(GymDimens.MetricFlowRowGapVertical),
        ) {
            detail.summary.describe(unit).forEach { metric ->
                GymText(
                    text = metric,
                    role = GymTextRoles.LabelCaps,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // US-22: absent entirely unless a health read actually ran (US-20's absence pattern) —
        // `metrics` is null both when the integration is off and when the device can't use it,
        // which is exactly the point (ADR-0038). Present, it may still carry a null field for
        // one it read and found nothing for; that renders as "not recorded", never zero.
        detail.summary.session.metrics?.let { metrics ->
            Text(
                text = metrics.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (records.isNotEmpty()) {
            RecordsSection(records, names, unit, modifier = Modifier.weight(1f))
        } else {
            // US-13's absence pattern: nothing set, so nothing is said — not a "no records
            // this time" line standing in for one.
            Column(modifier = Modifier.weight(1f)) {}
        }

        PrimaryActionButton(text = "Done", onClick = onDone)
    }
}

/** Every record this session set, one line each: the movement, the reps, and the load. */
@Composable
private fun RecordsSection(
    records: List<PersonalRecord>,
    names: Map<ExerciseId, String?>,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
        Text(text = "New personal records", style = MaterialTheme.typography.titleMedium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
            items(records, key = { "${it.exerciseId.value}-${it.reps}" }) { record ->
                Text(
                    text = record.describe(names[record.exerciseId], unit),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * "New PR: Bench Press, 5 reps, 102.5 kg" — the exercise, the rep count the record was set at,
 * and the load, in the member's own unit. Falls back to the raw id on the same absent-name edge
 * case `PerformedExerciseCard` in `WorkoutDetailScreen.kt` already handles: the schema forbids a
 * row outliving its exercise, so this is defensive, not an expected path.
 */
private fun PersonalRecord.describe(
    exerciseName: String?,
    unit: WeightUnit,
): String {
    val name = exerciseName ?: exerciseId.value
    val reps = "$reps ${"rep".orPlural(reps)}"
    return "New PR: $name, $reps, ${WeightFormatter.format(weightKg, unit).primary}"
}

/**
 * Duration · exercises · sets · volume — the same figures history already shows for this
 * session (`WorkoutHeader` in `WorkoutDetailScreen.kt`), copied rather than shared: it is ten
 * lines, and `PastLoggedSets`'s own doc comment already sets the precedent for duplicating a
 * short private UI text-builder over reaching across files for it. Kept in sync with
 * `HistoryScreen`'s `describe()` by hand — including, since ADR-0011's Turn 4 amendment, the
 * return type: a list of separate measurements, not one joined sentence, drawn by the call site
 * as a `FlowRow` with no separators — the fix for the same dot-joined wrap HistoryScreen's row
 * had, applied here for the same reason rather than left inconsistent one screen later in the
 * same flow. "bodyweight" shortens to "bw" to match.
 */
private fun SessionSummary.describe(unit: WeightUnit): List<String> =
    buildList {
        duration?.let { add(it.asLength()) }
        add("$exerciseCount ${"exercise".orPlural(exerciseCount)}")
        add("$setCount ${"set".orPlural(setCount)}")
        WeightFormatter.formatVolume(volumeKg, unit)?.let { add(it) }
        if (bodyweightSetCount > 0) {
            add("$bodyweightSetCount bw")
        }
    }

/**
 * "Avg HR 128 bpm · Peak 171 bpm · 340 kcal active", or "not recorded" per field that was read
 * for but came back empty (US-22) — never a zero, per constitution §2.4. Shared with
 * `WorkoutDetailScreen`'s own header, which renders the exact same session the same way once
 * it becomes history.
 */
internal fun SessionMetrics.describe(): String =
    buildString {
        append(avgHeartRate?.let { "Avg HR $it bpm" } ?: "Heart rate not recorded")
        maxHeartRate?.let { append("  ·  Peak $it bpm") }
        append("  ·  ")
        append(activeKilocalories?.let { "$it kcal active" } ?: "Calories not recorded")
    }

private fun String.orPlural(count: Int): String = if (count == 1) this else "${this}s"

/** "1h 12m", or "48m" under the hour — nobody reads "0h 48m". */
private fun Duration.asLength(): String {
    val hours = toHours()
    val minutes = toMinutes() % MINUTES_IN_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private const val MINUTES_IN_HOUR = 60L

private const val PREVIEW_DURATION_MINUTES = 47L
private const val PREVIEW_WEIGHT_KG = 102.5
private const val PREVIEW_REPS = 5
private const val PREVIEW_VOLUME_KG = 512.5

private fun previewDetail(metrics: SessionMetrics? = null): SessionDetail {
    val started = Instant.parse("2026-08-09T17:10:00Z")
    val session =
        WorkoutSession(
            id = SessionId("preview"),
            userId = UserId("preview"),
            gymName = null,
            startedAt = started,
            endedAt = started.plus(Duration.ofMinutes(PREVIEW_DURATION_MINUTES)),
            metrics = metrics,
            routine = RoutineOrigin(id = "r1", name = "Upper A"),
        )
    val appearance = SessionExercise(SessionExerciseId("se-1"), session.id, ExerciseId("bench"), 1)
    val sets = listOf(ExerciseSet("a", appearance.id, 1, PREVIEW_WEIGHT_KG, PREVIEW_REPS, null, started))

    return SessionDetail(
        summary =
            SessionSummary(
                session,
                exerciseCount = 1,
                setCount = 1,
                volumeKg = PREVIEW_VOLUME_KG,
                bodyweightSetCount = 0,
            ),
        exercises =
            listOf(
                PerformedExercise(
                    sessionExercise = appearance,
                    exercise =
                        Exercise(
                            id = ExerciseId("bench"),
                            name = "Bench Press",
                            aliases = emptyList(),
                            primaryMuscles = emptyList(),
                            secondaryMuscles = emptyList(),
                            equipment = Equipment.BARBELL,
                            instructions = emptyList(),
                            mediaUrl = null,
                            mediaType = null,
                            youtubeUrl = null,
                            source = "free-exercise-db",
                        ),
                    sets = sets,
                    volumeKg = PREVIEW_VOLUME_KG,
                    bodyweightSetCount = 0,
                ),
            ),
    )
}

@Preview
@Composable
private fun FinishSummaryWithRecordsPreview() {
    GymTrackerTheme {
        FinishSummaryScreen(
            detail = previewDetail(),
            records =
                listOf(
                    PersonalRecord(
                        ExerciseId("bench"),
                        reps = 5,
                        weightKg = 102.5,
                        achievedOn = LocalDate.parse("2026-08-09"),
                    ),
                ),
            unit = WeightUnit.LB,
            onDone = {},
        )
    }
}

@Preview
@Composable
private fun FinishSummaryNoRecordsPreview() {
    GymTrackerTheme {
        FinishSummaryScreen(
            detail = previewDetail(),
            records = emptyList(),
            unit = WeightUnit.LB,
            onDone = {},
        )
    }
}

@Preview
@Composable
private fun FinishSummaryWithHealthMetricsPreview() {
    GymTrackerTheme {
        FinishSummaryScreen(
            detail = previewDetail(metrics = SessionMetrics(128, 171, 340, "health_connect")),
            records = emptyList(),
            unit = WeightUnit.LB,
            onDone = {},
        )
    }
}
