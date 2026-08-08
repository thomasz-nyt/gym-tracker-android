package com.gymtracker.core.designsystem.component

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gymtracker.core.designsystem.theme.GymDimens

/**
 * The way out of a drill-down: exercise detail (US-13) and workout detail (US-06b).
 *
 * ADR-0024 removed the dead-end "Done" from these screens, which was right — finding 06 was
 * about a button that was the *only* exit. Removing it left them with no exit at all, because
 * the bottom bar is deliberately hidden on drill-downs, so an edge swipe was the only way back.
 * That is the least discoverable control on Android, on an app used one-handed with chalk on
 * your fingers. This is the correction: a real up affordance, and the bottom bar still does not
 * come back on these screens.
 *
 * **The label is text, not an icon.** There is no icon dependency in this app and adding one
 * needs an ADR (constitution §7) — `StepperField` draws its own +/− as text for the same
 * reason. A back arrow is not worth a dependency.
 *
 * **It carries no title.** Both screens that use it already name themselves in the body — the
 * exercise name above its photo, the date above the workout — and putting that in the bar as
 * well just prints it twice. This is the way out, and nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillDownTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {},
        navigationIcon = {
            TextButton(
                onClick = onBack,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Back", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
