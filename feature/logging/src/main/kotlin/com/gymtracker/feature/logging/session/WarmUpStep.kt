package com.gymtracker.feature.logging.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.RepMascot
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
import com.gymtracker.feature.logging.WarmUp
import com.gymtracker.feature.logging.asMinutesSeconds
import java.time.Duration

/**
 * The warm-up's running state, full screen (ADR-0045, Turn 5 file `02`). Replaces
 * [WarmUpPanel]'s old inline running block — this composable is only ever shown in place of the
 * session screen, never alongside it, which is what makes ADR-0045's "no state where both are
 * visible" true by construction rather than by convention.
 *
 * `SKIP` and [PrimaryActionButton]'s `DONE — START LIFTING` both call [WarmUp.onStop] — ADR-0021:
 * stopping the timer discards it the same way regardless of which one ends it, so there is no
 * second callback to wire.
 *
 * No step count in the kicker ("`WARM-UP`", not "`STEP 1 OF 2`") — this build has no cool-down
 * step to count against yet; see ADR-0045.
 */
@Composable
internal fun WarmUpStep(
    warmUp: WarmUp,
    nextExerciseName: String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WarmUpStepHeader(onSkip = warmUp.onStop)
        WarmUpTimerBlock(elapsed = warmUp.elapsed ?: Duration.ZERO)
        if (nextExerciseName != null) WarmUpNextExerciseRow(nextExerciseName)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RepMascot(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .height(GymDimens.MascotWarmUp)
                        .padding(end = GymDimens.CompactScreenPadding),
            )
        }

        PrimaryActionButton(text = "DONE — START LIFTING", onClick = warmUp.onStop)
    }
}

@Composable
private fun WarmUpStepHeader(onSkip: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = GymDimens.MinTouchTarget)
                .padding(horizontal = GymDimens.CompactScreenPadding, vertical = GymDimens.TightGap),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GymText(text = "Warm-up", role = GymTextRoles.TitleLg)
        TextButton(onClick = onSkip) {
            GymText(text = "SKIP", role = GymTextRoles.LabelCaps, color = MaterialTheme.colorScheme.primary)
        }
    }
    GymDivider()
}

@Composable
private fun WarmUpTimerBlock(elapsed: Duration) {
    GymText(
        text = "WARM-UP",
        role = GymTextRoles.LabelCaps,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.padding(horizontal = GymDimens.CompactScreenPadding).padding(top = GymDimens.SectionSpace),
    )
    GymText(
        text = elapsed.asMinutesSeconds(),
        role = GymTextRoles.DisplayTimer,
        modifier =
            Modifier
                .padding(horizontal = GymDimens.CompactScreenPadding)
                .padding(top = GymDimens.CompactScreenPadding),
        semantics = { contentDescription = "Warm-up ${elapsed.asMinutesSeconds()} elapsed, not recorded" },
    )
    GymText(
        text = "Counting up. No target.",
        role = GymTextRoles.Meta,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = GymDimens.CompactScreenPadding).padding(top = GymDimens.HairGap),
    )
}

@Composable
private fun WarmUpNextExerciseRow(nextExerciseName: String) {
    GymDivider(modifier = Modifier.padding(top = GymDimens.SectionSpace))
    Row(
        // StepperTarget, not a new token: this codebase's existing 56dp value.
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GymDimens.StepperTarget)
                .padding(horizontal = GymDimens.CompactScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GymText(text = "THEN", role = GymTextRoles.LabelCaps)
        GymText(text = nextExerciseName, role = GymTextRoles.TitleMd)
    }
    GymDivider()
}
