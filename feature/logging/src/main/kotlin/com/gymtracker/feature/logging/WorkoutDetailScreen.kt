package com.gymtracker.feature.logging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.GymPhoto
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
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One past workout in full (US-06b), as a destination of its own (ADR-0024).
 *
 * Reads through the same [HistoryViewModel] class [HistoryRoute] uses, but its own instance:
 * each is scoped to its own place in the back stack, and this one is told which session to load
 * through [sessionId] rather than through a shared "opened workout" flag.
 */
@Composable
fun WorkoutDetailRoute(
    sessionId: SessionId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { viewModel.openWorkout(sessionId) }

    val detail = state.detail
    if (detail == null) {
        // Loading, or the workout was deleted from under the screen (US-06a) — either way
        // there is nothing to show yet, and no dead end to offer: the bar and back still work.
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    WorkoutDetailScreen(
        onBack = onBack,
        detail = detail,
        unit = state.unit,
        onEditSet = viewModel::onEditPastSet,
        modifier = modifier,
    )

    state.setEdit?.let { edit ->
        SetEditSheet(edit = edit, unit = state.unit, callbacks = viewModel.setEditCallbacks())
    }
}

/**
 * One past workout in full (US-06b).
 *
 * History says how much; this says what. Exercises read in the order they were performed,
 * unlike the active session, which puts the newest first (US-02b).
 *
 * Instruction steps, GIFs and filtering by body part are US-12 and US-13, at M3. What is shown
 * here is only what the catalog row already carries.
 *
 * Sets render one row per [ExerciseSet], each its own tap target for [onEditSet] — the same
 * shape `LoggedSets` in `ActiveSessionScreen.kt` uses, and for the same reason (ADR-0022, US-04's
 * third criterion: correcting a past session's set).
 *
 * The dead-end "Back" button is gone (finding 06, ADR-0024), replaced by a real up affordance
 * rather than by nothing: the bottom bar is hidden on drill-downs, so removing it left an edge
 * swipe as the only exit. See [DrillDownTopBar].
 */
@Composable
internal fun WorkoutDetailScreen(
    detail: SessionDetail,
    unit: WeightUnit,
    onEditSet: (PerformedExercise, ExerciseSet) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DrillDownTopBar(onBack = onBack) },
    ) { padding ->
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
                        PerformedExerciseCard(performed, unit, onEditSet)
                        GymDivider()
                    }
                }
            }
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
                    // Matches HistoryScreen's describe(), which this duplicates on purpose
                    // (see this file's own doc comment on why) — the two had drifted apart
                    // on exactly this segment before US-32.
                    if (summary.bodyweightSetCount > 0) {
                        append("  ·  ${summary.bodyweightSetCount} bodyweight")
                    }
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        // US-22: absent entirely unless a health read actually ran — the same absence pattern
        // `FinishSummaryScreen`'s own health line uses, since this renders the same session
        // once it becomes history. A field that was read for and found nothing shows as "not
        // recorded", never zero (constitution §2.4) — SessionMetrics.describe() carries that.
        summary.session.metrics?.let { metrics ->
            Text(
                text = metrics.describe(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** One exercise: what it was, and every set logged against it. */
@Composable
private fun PerformedExerciseCard(
    performed: PerformedExercise,
    unit: WeightUnit,
    onEditSet: (PerformedExercise, ExerciseSet) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GymDimens.Gap),
    ) {
        // US-06b promises no placeholder when the catalog has no photo. Omitting the child is
        // also what lets the exercise details use the full row width instead of reserving an
        // invisible thumbnail for almost every catalog entry.
        performed.exercise?.imageAsset?.let { DetailThumbnail(it) }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // The derived catalog can be wiped and re-seeded across an app upgrade, so
                // Room deliberately does not FK this appearance to it. If an older id ever
                // disappears, show that stable id rather than a blank line or invented name.
                text = performed.exercise?.name ?: performed.sessionExercise.exerciseId.value,
                style = MaterialTheme.typography.titleMedium,
            )
            performed.exercise?.let { exercise ->
                Text(
                    text = exercise.describeEquipmentAndMuscles(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (performed.sets.isEmpty()) {
                // ADR-0004 makes this representable: added, then never used.
                Text("No sets logged", style = MaterialTheme.typography.bodyMedium)
            } else {
                PastLoggedSets(performed.sets, unit) { set -> onEditSet(performed, set) }
                Text(
                    text = performed.describeTotals(unit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * The sets logged against one exercise in a past workout, one row per [ExerciseSet] and each
 * its own tap target for correcting it (US-04's third criterion, ADR-0022).
 *
 * Copies `LoggedSets` in `ActiveSessionScreen.kt` row for row — same label, same content
 * description, same tap target — because a past set and an active one are corrected through the
 * same editor and must be indistinguishable in how they read.
 */
@Composable
private fun PastLoggedSets(
    sets: List<ExerciseSet>,
    unit: WeightUnit,
    onEditSet: (ExerciseSet) -> Unit,
) {
    Column {
        sets.forEach { set ->
            val weight = WeightFormatter.format(set.weightKg, unit)
            Text(
                text =
                    buildString {
                        append("${set.setIndex}.  ${set.reps} reps")
                        append("   ${weight.primary}")
                        weight.secondary?.let { append("  ·  $it") }
                        set.rpe?.let { append("   RPE $it") }
                    },
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = GymDimens.MinTouchTarget)
                        .clickable { onEditSet(set) }
                        .semantics { contentDescription = "Edit set ${set.setIndex}" }
                        .wrapContentHeight(Alignment.CenterVertically),
            )
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
private fun DetailThumbnail(imageAsset: String) {
    GymPhoto(
        model = "file:///android_asset/exercise_images/$imageAsset",
        contentDescription = null,
        modifier =
            Modifier
                .size(GymDimens.Thumbnail)
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
                                volumeKg = 1224.6,
                                bodyweightSetCount = 0,
                            ),
                        ),
                ),
            unit = WeightUnit.LB,
        )
    }
}
