package com.gymtracker.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a preview in both colour schemes (ADR-0016).
 *
 * The palette has two halves and both of them ship, so a preview that only shows one of them
 * is only half a review — dark-mode contrast bugs are exactly the kind that survive to a gym
 * floor. Annotate with this rather than with `@Preview` and every screen gets both for free.
 */
@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class GymPreviews
