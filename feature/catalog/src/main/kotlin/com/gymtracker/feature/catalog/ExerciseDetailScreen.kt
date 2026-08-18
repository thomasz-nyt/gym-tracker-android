package com.gymtracker.feature.catalog

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.component.GymPhoto
import com.gymtracker.core.designsystem.component.RepMascot
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.exercise.YouTubeSearch
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId

/**
 * What a machine is and how it is used (US-13).
 *
 * Everything on this screen ships in the app, so it is fully usable in airplane mode on
 * first launch with nothing to cache first. The one exception is the YouTube search
 * (US-14), which is the only thing here that needs the network and which nothing else
 * depends on.
 *
 * The dead-end "Done" is gone (finding 06, ADR-0024), replaced by a real up affordance rather
 * than by nothing: the bottom bar is hidden on drill-downs, so removing the button left an edge
 * swipe as the only exit. See [DrillDownTopBar].
 *
 * US-43 / ADR-0035: `RepMascot` plays beside the exercise name — a brand mark, not a stand-in
 * for [MovementPhoto]'s empty slot. That slot stays empty on purpose for the 866 of 873
 * exercises with no bundled image (US-13's absence rule); Rep does not fill it.
 */
@Composable
fun ExerciseDetailRoute(
    exerciseId: ExerciseId,
    onBack: () -> Unit,
    onSeeProgress: (ExerciseId) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val exercise by viewModel.exercise(exerciseId).collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    ExerciseDetailScreen(
        exercise = exercise,
        onSeeProgress = { onSeeProgress(exerciseId) },
        onWatchSearch = { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun ExerciseDetailScreen(
    exercise: Exercise?,
    onSeeProgress: () -> Unit = {},
    onWatchSearch: (String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DrillDownTopBar(onBack = onBack) },
    ) { padding ->
        if (exercise == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = DETAIL_PADDING)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = DETAIL_PADDING),
            verticalArrangement = Arrangement.spacedBy(DETAIL_GAP),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DETAIL_GAP),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = DETAIL_PADDING),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                RepMascot(modifier = Modifier.height(GymDimens.MascotInline))
            }

            MovementPhoto(exercise.imageAsset)

            MuscleTags(exercise)

            Instructions(exercise.instructions)

            // US-16, reached from the movement it is about. A push rather than a panel here,
            // so :feature:catalog does not have to depend on :feature:progress.
            OutlinedButton(
                onClick = onSeeProgress,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MIN_TARGET),
            ) {
                Text("See your progress on this exercise")
            }

            // US-14: a search, and it says so. Nobody has vetted the result (ADR-0015).
            YouTubeSearch.forExercise(exercise.name)?.let { url ->
                OutlinedButton(
                    onClick = { onWatchSearch(url) },
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MIN_TARGET),
                ) {
                    Text("Search YouTube for this exercise")
                }
            }
        }
    }
}

/** Bundled for the starter set only; the rest show nothing rather than a placeholder. */
@Composable
private fun MovementPhoto(imageAsset: String?) {
    if (imageAsset == null) return

    GymPhoto(
        model = "file:///android_asset/exercise_images/$imageAsset",
        contentDescription = "Photo of the movement",
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GymDimens.PhotoHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Primary and secondary muscles, and what it is performed on (US-13). */
@Composable
private fun MuscleTags(exercise: Exercise) {
    Text("Works", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(TAG_GAP)) {
        AssistChip(
            onClick = {},
            label = { Text(exercise.equipment.label()) },
            // ADR-0019: AssistChip reads CornerFull unless told otherwise — redesign audit,
            // PR A finding 1.
            shape = MaterialTheme.shapes.large,
        )
    }
    Text(
        text = exercise.primaryMuscles.joinToString { it.label() }.ifEmpty { "Not recorded" },
        style = MaterialTheme.typography.bodyLarge,
    )
    if (exercise.secondaryMuscles.isNotEmpty()) {
        Text(
            text = "Also: ${exercise.secondaryMuscles.joinToString { it.label() }}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The numbered steps (US-13).
 *
 * Five exercises in the catalog have none. They say so rather than rendering an empty panel,
 * because a blank space reads as a bug and constitution §2 asks for absent to be shown as
 * absent.
 */
@Composable
private fun Instructions(steps: List<String>) {
    Text("How to do it", style = MaterialTheme.typography.titleMedium)

    if (steps.isEmpty()) {
        Text(
            text = "The catalog records no instructions for this exercise.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
        return
    }

    steps.forEachIndexed { index, step ->
        Text(
            text = "${index + 1}.  $step",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private val DETAIL_PADDING = GymDimens.ScreenPadding
private val DETAIL_GAP = GymDimens.Gap
private val TAG_GAP = GymDimens.TightGap
private val MIN_TARGET = GymDimens.MinTouchTarget

@Preview
@Composable
private fun DetailPreview() {
    GymTrackerTheme {
        ExerciseDetailScreen(
            exercise =
                Exercise(
                    id = ExerciseId("preview"),
                    name = "Wide-Grip Lat Pulldown",
                    aliases = listOf("pulldown"),
                    primaryMuscles = listOf(BodyPart.BACK),
                    secondaryMuscles = listOf(BodyPart.BICEPS),
                    equipment = Equipment.CABLE,
                    instructions = listOf("Sit down.", "Pull the bar to your chest.", "Return under control."),
                    mediaUrl = null,
                    mediaType = null,
                    youtubeUrl = null,
                    source = "free-exercise-db",
                ),
            onWatchSearch = {},
        )
    }
}
