package com.gymtracker.feature.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.GymPhoto
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
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
                    // ADR-0019: FilterChip reads CornerFull unless told otherwise (Shape.kt's
                    // documented trap) — redesign audit, PR A finding 1.
                    shape = MaterialTheme.shapes.large,
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
                    shape = MaterialTheme.shapes.large,
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
        contentPadding = PaddingValues(bottom = GymDimens.FabClearance),
    ) {
        items(results, key = { it.id.value }) { exercise ->
            val added = timesAdded(exercise.id)
            // CI's real device measured this row at 71dp (Material3's own two-line ListItem
            // spec, ~72dp) even with sizeIn(minHeight = 80.dp) passed as ListItem's own
            // modifier — ListItem caps its height to its internal one/two/three-line spec
            // regardless of a looser external minHeight, so the floor has to be enforced by an
            // outer container ListItem sizes itself within, not by ListItem's own modifier.
            //
            // CATALOG_ROW_TEST_TAG: OutlinedTextField also exposes a click semantics action (so
            // an accessibility service can "tap to focus" it), so hasText(name) and
            // hasClickAction() together still matched the search field ahead of this row in
            // tree order once a query had been typed into it — a text/click-action combinator
            // could never disambiguate the two, however it was built. A test tag sidesteps that
            // entirely: nothing else in this screen's tree carries it.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = GymDimens.CatalogRowHeight)
                        .clickable { onChosen(exercise.id) }
                        .testTag(CATALOG_ROW_TEST_TAG),
                contentAlignment = Alignment.CenterStart,
            ) {
                ListItem(
                    headlineContent = {
                        GymText(
                            text = exercise.name,
                            role = GymTextRoles.TitleMd,
                        )
                    },
                    supportingContent = {
                        GymText(
                            text = exercise.equipment.label(),
                            role = GymTextRoles.LabelCaps,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    // US-13's absence rule applies to layout too: without an image there is no
                    // leading slot, rather than a thumbnail-sized blank that narrows every row.
                    leadingContent =
                        exercise.imageAsset?.let { imageAsset ->
                            { ExerciseThumbnail(imageAsset) }
                        },
                    // A fixed-width cell (frame 4a) — an "ADDED" tag or a "+" invitation, always
                    // the same width either way, so adding an exercise never changes the name
                    // column's width and never reflows the row the way a variable-width label
                    // did.
                    trailingContent = { AddedCell(added) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GymDivider()
        }
    }
}

/**
 * The picker row's trailing cell (frame `4a`): [GymDimens.AddExerciseCellWidth] wide regardless
 * of state, holding a `tag.caps` "ADDED" label once tapped or a visual `+` invitation before —
 * the fixed width is what stops adding an exercise from narrowing the name column next to it.
 *
 * The `+` is decorative, not a second tap target: the row itself is already the full clickable
 * area (US-02's "tap again to add a second time" reads the same either way), so this box carries
 * no semantics of its own rather than duplicating what TalkBack already announces for the row.
 */
@Composable
private fun AddedCell(added: Int) {
    Box(
        modifier = Modifier.width(GymDimens.AddExerciseCellWidth),
        contentAlignment = Alignment.Center,
    ) {
        if (added > 0) {
            // Counted, not just flagged: US-02 allows the same exercise twice, so tapping it
            // again is a real action and the row should say so.
            GymText(
                text = if (added == 1) "ADDED" else "ADDED $added×",
                role = GymTextRoles.TagCaps,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(GymDimens.AddExerciseButtonHeight)
                        .border(
                            BorderStroke(GymDimens.DividerThickness, MaterialTheme.colorScheme.outline),
                            MaterialTheme.shapes.large,
                        ).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
        }
    }
}

/**
 * A bundled photo where one ships (ADR-0007, ADR-0014). The caller omits `leadingContent`
 * entirely when none ships; an empty slot is still a placeholder, even when it has no pixels.
 */
@Composable
internal fun ExerciseThumbnail(imageAsset: String) {
    GymPhoto(
        model = "file:///android_asset/exercise_images/$imageAsset",
        // The name is right beside it, so repeating it would only add noise for TalkBack.
        contentDescription = null,
        modifier =
            Modifier
                .size(GymDimens.CatalogThumbnail)
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

// ADR-0011's Turn 4 amendment: 20dp, not ScreenPadding's 24 — this screen's own gutter now,
// not the app-wide one, since ScreenPadding is still read by thirteen other files this pass
// does not touch.
private val SCREEN_PADDING = GymDimens.CompactScreenPadding
private val GAP = GymDimens.Gap
private val CHIP_GAP = GymDimens.TightGap
private val MIN_TOUCH_TARGET = GymDimens.MinTouchTarget

/**
 * A plain literal, not a shared constant: `app`'s instrumented tests consume this module as a
 * compiled dependency, not source, so a `private`/`internal` constant here would not be visible
 * to them regardless — the test file matches this exact string directly, with a comment pointing
 * back to this declaration. See the test-tag comment above [Results]'s `Box` for why a test tag
 * exists here at all rather than matching on text and a click action.
 */
private const val CATALOG_ROW_TEST_TAG = "catalog-row"

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

/**
 * ADR-0011's Turn 4 amendment: the rule this pass adds to every changed composable's own
 * previews — 320dp width, 130% font scale, and the longest exercise name in the bundled
 * database. If the row still fits two lines and stays 80dp+ here, it survives a Pixel 4a with
 * large text on, which is where the wraps this amendment fixes were actually measured.
 */
@Preview(widthDp = 320, fontScale = 1.3f)
@Composable
private fun BrowseNarrowWorstCasePreview() {
    val worstCase =
        Exercise(
            id = ExerciseId("preview-worst-case"),
            name = "Barbell Incline Bench Press - Medium Grip",
            aliases = emptyList(),
            primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(),
            equipment = Equipment.BARBELL,
            instructions = emptyList(),
            mediaUrl = null,
            mediaType = null,
            youtubeUrl = null,
            source = "free-exercise-db",
        )
    GymTrackerTheme {
        BrowseScreen(
            state = CatalogUiState(isLoading = false, results = listOf(worstCase)),
            onQueryChanged = {},
            onBodyPartToggled = {},
            onEquipmentToggled = {},
            onClearFilters = {},
            onChosen = {},
            onBack = {},
            pickForSession = true,
        )
    }
}
