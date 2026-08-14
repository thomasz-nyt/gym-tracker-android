package com.gymtracker.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.progress.BodyPartVolume
import com.gymtracker.core.domain.progress.VolumeWeek
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Weekly training volume, grouped by muscle (US-17), and the honest absence of it (US-19).
 *
 * **There is no charting library on this screen, and that is the design.** ADR-0019 leaves the
 * app one accent on an achromatic ground, and the obvious rendering of this data — a stacked
 * column per week, one hue per muscle — would need twelve hues the palette does not have. So the
 * muscles are stacked *vertically* as labelled bars instead: the accent carries length, and the
 * name and the load are read as text. It also comes out better on the two counts that matter
 * here, because a bar with its number written next to it is legible at arm's length and audible
 * to TalkBack, where a colour-keyed stack is neither.
 */
@Composable
fun WeeklyVolumeRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeeklyVolumeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.open() }

    WeeklyVolumeScreen(state = state, onBack = onBack, modifier = modifier)
}

@Composable
internal fun WeeklyVolumeScreen(
    state: WeeklyVolumeUiState,
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
                    .padding(padding)
                    .padding(horizontal = GymDimens.ScreenPadding)
                    .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            Text("Weekly volume", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Last $WEEKS_SHOWN weeks, by muscle. Weight moved: sets x reps x load.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.hasVolume) {
                WeekList(state = state, modifier = Modifier.fillMaxWidth().weight(1f))
            } else if (!state.isLoading) {
                // US-19: nothing logged is said, not drawn as a floor of empty bars.
                Text(
                    text =
                        "Nothing logged in the last $WEEKS_SHOWN weeks. Finish a workout and " +
                            "the weeks fill in here.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

/** One block per week in the window, most recent first. */
@Composable
private fun WeekList(
    state: WeeklyVolumeUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
    ) {
        items(state.weeks, key = { it.weekStarting.toString() }) { week ->
            WeekBlock(week = week, peakVolumeKg = state.peakVolumeKg, unit = state.unit)
        }
    }
}

/**
 * One week: its Monday, then a bar per muscle it loaded.
 *
 * A week with nothing in it keeps its heading and says so. The domain returns those weeks on
 * purpose — closing the gap would imply the member trained every week — and dropping them here
 * would undo that at the last step.
 */
@Composable
private fun WeekBlock(
    week: VolumeWeek,
    peakVolumeKg: Double,
    unit: WeightUnit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
        Text(
            text = "Week of ${WEEK_FORMAT.format(week.weekStarting)}",
            style = MaterialTheme.typography.titleMedium,
        )

        if (week.byBodyPart.isEmpty()) {
            Text(
                text = "Nothing logged",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            week.byBodyPart.forEach { entry ->
                VolumeBar(entry = entry, peakVolumeKg = peakVolumeKg, unit = unit)
            }
        }
    }
}

/**
 * One muscle's week: the name and the load as text, the bar underneath as the comparison.
 *
 * The number is written rather than left to the bar's length, so the screen is readable when the
 * bar is not — at arm's length, at 200% font scale, and through TalkBack. The bar itself is
 * marked as carrying no semantics for that reason: it repeats what the row above it already
 * says, and announcing it twice is worse than not announcing it.
 */
@Composable
private fun VolumeBar(
    entry: BodyPartVolume,
    peakVolumeKg: Double,
    unit: WeightUnit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap / 2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(entry.bodyPart.label(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = WeightFormatter.formatVolume(entry.volumeKg, unit).orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // The track, and the filled part of it. A Row rather than a Box so the fill is laid out
        // from the start edge, which is also what makes it read right-to-left in an RTL locale.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(GymDimens.VolumeBarHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clearAndSetSemantics {},
        ) {
            // The app's one accent (ADR-0019).
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(entry.fractionOf(peakVolumeKg))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * How long this muscle's bar is, against the busiest muscle-week on screen.
 *
 * Floored at [MIN_BAR_FRACTION] so a light week still draws something: a muscle that was
 * trained and a muscle that was not have to look different, and a bar rounded to nothing would
 * make them look the same.
 */
private fun BodyPartVolume.fractionOf(peakVolumeKg: Double): Float =
    if (peakVolumeKg <= 0.0) {
        0f
    } else {
        (volumeKg / peakVolumeKg).toFloat().coerceIn(MIN_BAR_FRACTION, 1f)
    }

/**
 * "Hamstrings" from `HAMSTRINGS`.
 *
 * A private copy of the one in `:feature:catalog`, which is `internal` to that module. Two
 * one-line copies is the cheaper answer here; a shared home for it would have to be
 * `:core:designsystem`, and that module does not know `:core:domain`'s types today.
 */
private fun BodyPart.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private val WEEK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** Small, but never zero — see [fractionOf]. */
private const val MIN_BAR_FRACTION = 0.02f

@Preview
@Composable
private fun WeeklyVolumePreview() {
    val monday = LocalDate.parse("2026-07-20")
    GymTrackerTheme {
        WeeklyVolumeScreen(
            state =
                WeeklyVolumeUiState(
                    isLoading = false,
                    weeks =
                        listOf(
                            VolumeWeek(
                                weekStarting = monday,
                                byBodyPart =
                                    listOf(
                                        BodyPartVolume(BodyPart.HAMSTRINGS, 1912.5),
                                        BodyPartVolume(BodyPart.BACK, 1837.5),
                                        BodyPartVolume(BodyPart.QUADS, 1612.5),
                                        BodyPartVolume(BodyPart.CHEST, 1106.25),
                                    ),
                            ),
                            VolumeWeek(weekStarting = monday.minusWeeks(1), byBodyPart = emptyList()),
                        ),
                    unit = WeightUnit.LB,
                ),
        )
    }
}
