package com.gymtracker.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.NumeralText
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.health.LiveHeartRate

/**
 * US-47/US-48 (ADR-0039): the current heart rate, visible from every screen — not just the
 * session screen — for as long as a reading exists. Sits in [GymTrackerNavHost]'s `topBar`
 * slot, which nothing else in the app uses.
 *
 * Renders **nothing at all** while [LiveHeartRate.Unavailable] — no Bluetooth adapter, below
 * API 31, unpaired, or the toggle off — so the slot measures zero height and adds no visual
 * weight for a member who never turned this on (the no-op-renders-nothing convention
 * `tech-stack.md`'s optional-feature contract requires everywhere else). Once the feature is
 * on, [LiveHeartRate.Searching] and [LiveHeartRate.Lost] are shown as their own distinct,
 * honestly-labelled states (US-48) — neither is ever presented as a live BPM.
 */
@Composable
fun LiveHeartRateChip(
    modifier: Modifier = Modifier,
    viewModel: LiveHeartRateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveHeartRateChipContent(state = state, modifier = modifier)
}

@Composable
internal fun LiveHeartRateChipContent(
    state: LiveHeartRate,
    modifier: Modifier = Modifier,
) {
    if (state == LiveHeartRate.Unavailable) return

    Row(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = GymDimens.ScreenPadding, vertical = GymDimens.TightGap),
    ) {
        HeartRateReading(state)
    }
}

/**
 * ADR-0036 reserves red for the thing you tap — this readout is not tappable, so it stays
 * `onSurfaceVariant`/`onSurface` rather than reaching for the accent, and no new color token is
 * introduced (every token in the scheme is asserted for WCAG AA and achromatic saturation by
 * `GymColorSchemeTest`).
 *
 * Sized with the design system's `displaySmall` vital-reading role rather than a bare
 * `labelSmall` (12sp) — legible from arm's length mid-set, chosen from a side-by-side render of
 * 12/24/36/48sp on device rather than by formula. Deliberately short of `headlineMedium` (44sp,
 * the rest timer's own weight): a persistent element visible on every screen, the whole workout,
 * competing with page content at headline weight would be the same overreach ADR-0036 argues
 * against for colour.
 */
@Composable
private fun RowScope.HeartRateReading(state: LiveHeartRate) {
    when (state) {
        LiveHeartRate.Unavailable -> Unit
        LiveHeartRate.Searching ->
            Text(
                text = "Heart rate: searching…",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        LiveHeartRate.Lost ->
            Text(
                text = "Heart rate: lost",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        is LiveHeartRate.Beating ->
            NumeralText(
                text = "${state.bpm} bpm",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
    }
}
