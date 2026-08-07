package com.gymtracker.feature.logging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gymtracker.core.designsystem.component.SecondaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymPreviews
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.PerformedExercise
import com.gymtracker.core.domain.session.SessionDetail
import com.gymtracker.core.domain.session.SessionSummary
import com.gymtracker.core.domain.set.SetGroup
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One past workout in full (US-06b).
 *
 * History says how much; this says what. Exercises read in the order they were performed,
 * unlike the active session, which puts the newest first (US-02b).
 *
 * Instruction steps, GIFs and filtering by body part are US-12 and US-13, at M3. What is shown
 * here is only what the catalog row already carries.
 */
@Composable
internal fun WorkoutDetailScreen(
    detail: SessionDetail,
    unit: WeightUnit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = GymDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        ) {
            WorkoutHeader(detail.summary, unit)

            if (detail.exercises.isEmpty()) {
                Text(
                    text = "No exercises were added to this workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
                ) {
                    items(detail.exercises, key = { it.sessionExercise.id.value }) { performed ->
                        PerformedExerciseCard(performed, unit)
                        HorizontalDivider()
                    }
                }
            }

            // Full width and bottom-anchored like every other way out of a screen (ADR-0016),
            // but tonal rather than accented: leaving a past workout is not what you came here
            // to do.
            SecondaryActionButton(
                text = "Back",
                onClick = onBack,
                modifier = Modifier.padding(bottom = GymDimens.Gap),
            )
        }
    }
}

/** Date, how long it ran, and the totals the history row already showed. */
@Composable
private fun WorkoutHeader(
    summary: SessionSummary,
    unit: WeightUnit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        modifier = Modifier.padding(top = GymDimens.ScreenPadding),
    ) {
        Text(
            text = summary.session.startedAt.asDetailDate(),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text =
                buildString {
                    summary.duration?.let { append("${it.asLength()}  ·  ") }
                    append("${summary.exerciseCount} ${"exercise".orPlural(summary.exerciseCount)}")
                    append("  ·  ${summary.setCount} ${"set".orPlural(summary.setCount)}")
                    WeightFormatter.formatVolume(summary.volumeKg, unit)?.let { append("  ·  $it") }
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        // Until M5 there is nothing to read, and an unrecorded metric is shown as unrecorded
        // rather than as zero (constitution §2.4, and the rule US-22 will inherit).
        Text(
            text = "Heart rate and calories: not recorded",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One exercise: what it was, and every set logged against it. */
@Composable
private fun PerformedExerciseCard(
    performed: PerformedExercise,
    unit: WeightUnit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GymDimens.Gap),
    ) {
        DetailThumbnail(performed.exercise?.imageAsset)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // The catalog entry is only absent if the row outlived its exercise, which the
                // schema forbids; show the id rather than a blank line.
                text = performed.exercise?.name ?: performed.sessionExercise.exerciseId.value,
                style = MaterialTheme.typography.titleMedium,
            )
            performed.exercise?.let { exercise ->
                Text(
                    text = exercise.describeEquipmentAndMuscles(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (performed.groups.isEmpty()) {
                // ADR-0004 makes this representable: added, then never used.
                Text("No sets logged", style = MaterialTheme.typography.bodyMedium)
            } else {
                performed.groups.forEach { group ->
                    Text(group.describe(unit), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = performed.describeTotals(unit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * "Barbell  ·  Chest, Triceps" — the catalog metadata the app already stores and has never
 * shown. Filtering by either is US-12, at M3.
 */
private fun Exercise.describeEquipmentAndMuscles(): String =
    buildString {
        append(equipment.readable())
        if (primaryMuscles.isNotEmpty()) {
            append("  ·  ")
            append(primaryMuscles.joinToString { it.readable() })
        }
    }

/** "3 × 12   135 lb  ·  61.2 kg   RPE 8", matching how the session screen reads. */
private fun SetGroup.describe(unit: WeightUnit): String {
    val weight = WeightFormatter.format(weightKg, unit)
    return buildString {
        if (count > 1) append("$count × $reps") else append("$firstSetIndex.  $reps reps")
        append("   ${weight.primary}")
        weight.secondary?.let { append("  ·  $it") }
        rpe?.let { append("   RPE $it") }
    }
}

/**
 * Volume for this exercise, with bodyweight sets counted alongside rather than folded in as
 * zero (US-06, constitution §2).
 */
private fun PerformedExercise.describeTotals(unit: WeightUnit): String =
    buildString {
        WeightFormatter.formatVolume(volumeKg, unit)?.let { append(it) }
        if (bodyweightSetCount > 0) {
            if (isNotEmpty()) append("  ·  ")
            append("$bodyweightSetCount bodyweight")
        }
        if (isEmpty()) append("No weight recorded")
    }

/** As on the search screen: no image rather than a placeholder that pretends (ADR-0007). */
@Composable
private fun DetailThumbnail(imageAsset: String?) {
    if (imageAsset == null) {
        Box(modifier = Modifier.size(GymDimens.Thumbnail))
        return
    }

    AsyncImage(
        model = "file:///android_asset/exercise_images/$imageAsset",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(GymDimens.Thumbnail)
                .clip(RoundedCornerShape(DETAIL_THUMBNAIL_CORNER))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun Equipment.readable(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun BodyPart.readable(): String =
    name.split('_').joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }

private fun String.orPlural(count: Int): String = if (count == 1) this else "${this}s"

/** "1h 12m", or "48m" under the hour — nobody reads "0h 48m". */
private fun Duration.asLength(): String {
    val hours = toHours()
    val minutes = toMinutes() % MINUTES_IN_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun Instant.asDetailDate(): String = DETAIL_DATE.format(atZone(ZoneId.systemDefault()))

private val DETAIL_DATE = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

private const val MINUTES_IN_HOUR = 60L
private val DETAIL_THUMBNAIL_CORNER = 8.dp

@GymPreviews
@Composable
private fun WorkoutDetailPreview() {
    val started = Instant.parse("2026-08-01T17:10:00Z")
    val session =
        WorkoutSession(
            id = SessionId("preview"),
            userId = UserId("preview"),
            gymName = null,
            startedAt = started,
            endedAt = started.plus(Duration.ofMinutes(72)),
            metrics = null,
        )
    val appearance = SessionExercise(SessionExerciseId("se-1"), session.id, ExerciseId("bench"), 1)
    val sets =
        listOf(
            ExerciseSet("a", appearance.id, 1, 61.23, 10, null, started),
            ExerciseSet("b", appearance.id, 2, 61.23, 10, null, started),
        )

    GymTrackerTheme {
        WorkoutDetailScreen(
            detail =
                SessionDetail(
                    summary = SessionSummary(session, 1, 2, 1224.6, 0),
                    exercises =
                        listOf(
                            PerformedExercise(
                                sessionExercise = appearance,
                                exercise =
                                    Exercise(
                                        id = ExerciseId("bench"),
                                        name = "Bench Press",
                                        aliases = emptyList(),
                                        primaryMuscles = listOf(BodyPart.CHEST),
                                        secondaryMuscles = listOf(BodyPart.TRICEPS),
                                        equipment = Equipment.BARBELL,
                                        instructions = emptyList(),
                                        mediaUrl = null,
                                        mediaType = null,
                                        youtubeUrl = null,
                                        source = "free-exercise-db",
                                    ),
                                sets = sets,
                                groups = SetGroup.of(sets),
                                volumeKg = 1224.6,
                                bodyweightSetCount = 0,
                            ),
                        ),
                ),
            unit = WeightUnit.LB,
            onBack = {},
        )
    }
}
