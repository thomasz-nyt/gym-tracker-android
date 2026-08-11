package com.gymtracker.core.designsystem.component

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gymtracker.core.designsystem.theme.GymDimens

/**
 * The app's row divider (ADR-0019): [GymDimens.DividerThickness], not Material's 1dp hairline.
 *
 * Reads `outlineVariant` exactly as the bare `HorizontalDivider()` it replaces did — finding 08
 * was that token shipping unset as Material's lavender default, and `GymColorSchemeTest` now
 * gates it, so this is a thickness override only, not a colour one.
 */
@Composable
fun GymDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = GymDimens.DividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
