package com.gymtracker.feature.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
    ) {
        Text(text = "Workout complete", style = MaterialTheme.typography.titleLarge)
        // US-32 (ADR-0028): leads with the routine this session was started from, the same
        // way HistoryScreen's row does — falling back to "Freestyle" for an ordinary start.
        Text(
            text =
                detail.summary.session.routine
                    ?.name ?: "Freestyle",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = detail.summary.describe(unit), style = MaterialTheme.typography.bodyMedium)

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
 * `HistoryScreen`'s `describe()` by hand — the bodyweight segment below is the one they had
 * drifted apart on before US-32.
 */
private fun SessionSummary.describe(unit: WeightUnit): String =
    buildString {
        duration?.let { append("${it.asLength()}  ·  ") }
        append("$exerciseCount ${"exercise".orPlural(exerciseCount)}")
        append("  ·  $setCount ${"set".orPlural(setCount)}")
        WeightFormatter.formatVolume(volumeKg, unit)?.let { append("  ·  $it") }
        if (bodyweightSetCount > 0) {
            append("  ·  $bodyweightSetCount bodyweight")
        }
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

private fun previewDetail(): SessionDetail {
    val started = Instant.parse("2026-08-09T17:10:00Z")
    val session =
        WorkoutSession(
            id = SessionId("preview"),
            userId = UserId("preview"),
            gymName = null,
            startedAt = started,
            endedAt = started.plus(Duration.ofMinutes(PREVIEW_DURATION_MINUTES)),
            metrics = null,
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
