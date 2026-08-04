package com.gymtracker.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gymtracker.core.designsystem.theme.GymDimens

/**
 * The one action a screen exists for (ADR-0016): full width, [GymDimens.PrimaryAction] tall,
 * in the accent colour.
 *
 * There is at most one of these per screen, and it is the screen's most *frequent* action
 * rather than its most important-sounding one — Add set, not Finish workout.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(GymDimens.PrimaryAction),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * An action worth a full-width target but not the accent: "Past workouts", "Done".
 *
 * Tonal rather than outlined so it still reads as a button at arm's length — a hairline border
 * is the first thing to disappear in bad light.
 */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.MinTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}
