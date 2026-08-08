package com.gymtracker.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.exercise.CatalogFilter
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId

/**
 * Browse and filter the catalog (US-12).
 *
 * One screen, two entry points. From home it is a way to look something up, and tapping a
 * result opens its detail screen. From an active session it is "add an exercise", and tapping
 * a result adds it — US-02's path, unchanged and no longer than it was.
 *
 * @param onChosen what a tap means here. The caller decides, which is the distinction the
 *   old state-derived routing could not express.
 * @param pickForSession true when tapping adds to the workout in progress. The screen then
 *   stays put and counts what it added, so picking three exercises is one visit rather than
 *   three (US-02a), and [onBack] is how that visit ends — the "Done · N added" button. False is
 *   the look-something-up path (a tab of its own now, ADR-0024), where there is no dead-end
 *   button any more: the bar and the system back gesture are the way out.
 */
@Composable
fun BrowseRoute(
    onChosen: (ExerciseId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pickForSession: Boolean = false,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BrowseScreen(
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onBodyPartToggled = viewModel::onBodyPartToggled,
        onEquipmentToggled = viewModel::onEquipmentToggled,
        onClearFilters = viewModel::onFiltersCleared,
        onChosen = { id ->
            // Counted here and appended by the session. Recording before handing it on keeps
            // the marker and the workout describing the same tap.
            if (pickForSession) viewModel.onAddedToSession(id)
            onChosen(id)
        },
        onBack = onBack,
        pickForSession = pickForSession,
        modifier = modifier,
    )
}

@Composable
internal fun BrowseScreen(
    state: CatalogUiState,
    onQueryChanged: (String) -> Unit,
    onBodyPartToggled: (BodyPart) -> Unit,
    onEquipmentToggled: (Equipment) -> Unit,
    onClearFilters: () -> Unit,
    onChosen: (ExerciseId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pickForSession: Boolean = false,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            // Only when picking. Browsing from home has no workout to be done adding to, and
            // a floating "Done" there would be a button with nothing to finish (US-02a).
            if (pickForSession) DoneAdding(state.addedThisVisit.size, onBack)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                label = { Text("Search exercises") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FilterChips(
                filter = state.filter,
                onBodyPartToggled = onBodyPartToggled,
                onEquipmentToggled = onEquipmentToggled,
            )

            ResultCount(state, onClearFilters)

            if (state.results.isEmpty() && !state.isLoading) {
                Text(
                    text = "Nothing matches. Try fewer filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                Results(
                    results = state.results,
                    timesAdded = state::timesAdded,
                    onChosen = onChosen,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

/**
 * The way back to the workout, reporting what this visit added (US-02a).
 *
 * Floating rather than in the column, because the list scrolls and this should not: after
 * three picks the member wants to leave from wherever they happen to be.
 */
@Composable
private fun DoneAdding(
    added: Int,
    onDone: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onDone,
        modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
    ) {
        Text(if (added == 0) "Done" else "Done  ·  $added added")
    }
}

/** How many exercises are left, and the way back to all of them (US-12). */
@Composable
private fun ResultCount(
    state: CatalogUiState,
    onClearFilters: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${state.results.size} ${if (state.results.size == 1) "exercise" else "exercises"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.isNarrowed) {
            TextButton(onClick = onClearFilters, modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET)) {
                Text("Clear")
            }
        }
    }
}

/**
 * Body part and equipment, as two scrolling rows of chips.
 *
 * Equipment reads "Not specified" for the value the catalog never recorded, rather than
 * folding it into "Other" (ADR-0015).
 */
@Composable
private fun FilterChips(
    filter: CatalogFilter,
    onBodyPartToggled: (BodyPart) -> Unit,
    onEquipmentToggled: (Equipment) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        ) {
            BodyPart.entries.forEach { part ->
                FilterChip(
                    selected = part in filter.bodyParts,
                    onClick = { onBodyPartToggled(part) },
                    label = { Text(part.label()) },
                    modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        ) {
            Equipment.entries.forEach { kit ->
                FilterChip(
                    selected = kit in filter.equipment,
                    onClick = { onEquipmentToggled(kit) },
                    label = { Text(kit.label()) },
                    modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                )
            }
        }
    }
}

@Composable
private fun Results(
    results: List<Exercise>,
    timesAdded: (ExerciseId) -> Int,
    onChosen: (ExerciseId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        // The floating button sits over the list, so the last result would be unreachable
        // underneath it without this.
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
    ) {
        items(results, key = { it.id.value }) { exercise ->
            val added = timesAdded(exercise.id)
            ListItem(
                headlineContent = { Text(exercise.name, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(exercise.equipment.label()) },
                leadingContent = { ExerciseThumbnail(exercise.imageAsset) },
                trailingContent = {
                    // Counted, not just flagged: US-02 allows the same exercise twice, so
                    // tapping it again is a real action and the row should say so.
                    if (added > 0) {
                        Text(
                            text = if (added == 1) "Added" else "Added $added×",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                // A whole row is the target, and it is a comfortable one: this list is scrolled
                // and tapped one-handed, standing in front of a machine (ADR-0016).
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = ROW_HEIGHT)
                        .clickable { onChosen(exercise.id) },
            )
            HorizontalDivider()
        }
    }
}

/**
 * A bundled photo where one ships, and empty space where none does (ADR-0007, ADR-0014).
 * An image that says nothing is worse than no image.
 */
@Composable
internal fun ExerciseThumbnail(imageAsset: String?) {
    if (imageAsset == null) {
        Box(modifier = Modifier.size(THUMBNAIL))
        return
    }

    AsyncImage(
        model = "file:///android_asset/exercise_images/$imageAsset",
        // The name is right beside it, so repeating it would only add noise for TalkBack.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(THUMBNAIL)
                .clip(RoundedCornerShape(THUMBNAIL_CORNER))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** "Full body", not "FULL_BODY". */
internal fun BodyPart.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** [Equipment.UNSPECIFIED] reads as what it means, and never as "Other" (ADR-0015). */
internal fun Equipment.label(): String =
    if (this == Equipment.UNSPECIFIED) {
        "Not specified"
    } else {
        name.lowercase().replaceFirstChar { it.uppercase() }
    }

private val SCREEN_PADDING = GymDimens.ScreenPadding
private val GAP = GymDimens.Gap
private val CHIP_GAP = GymDimens.TightGap
private val MIN_TOUCH_TARGET = GymDimens.MinTouchTarget
private val THUMBNAIL = GymDimens.Thumbnail
private val ROW_HEIGHT = 88.dp
private val THUMBNAIL_CORNER = 8.dp

/** Enough for the extended FAB plus its margin, so the last result clears it. */
private val FAB_CLEARANCE = 88.dp

@Preview
@Composable
private fun BrowsePreview() {
    GymTrackerTheme {
        BrowseScreen(
            state = CatalogUiState(isLoading = false),
            onQueryChanged = {},
            onBodyPartToggled = {},
            onEquipmentToggled = {},
            onClearFilters = {},
            onChosen = {},
            onBack = {},
        )
    }
}
