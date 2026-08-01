package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

/**
 * The app's type scale (ADR-0011).
 *
 * Material 3's defaults are sized for reading a phone held at a desk. This app is read
 * standing up, at arm's length, between sets — the completed-set line was a 12sp `bodySmall`,
 * which is what prompted the change. Every role the app renders is raised, so the decision
 * lives here rather than being re-made by whoever writes the next screen.
 *
 * Two rules go with it:
 *
 * - **Feature code never hard-codes an `sp` value.** It picks a role off
 *   `MaterialTheme.typography`, so M7's accessibility pass tunes one file.
 * - **Sizes stay in `sp`**, so the OS font-size setting still multiplies them. A member who
 *   has already turned system text up gets larger text still; capping that would be the
 *   accessibility bug M7 exists to catch.
 */
val GymTypography: Typography =
    Typography().run {
        copy(
            bodySmall = bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
            bodyMedium = bodyMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
            bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
            // Button and chip text.
            labelLarge = labelLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
            titleSmall = titleSmall.copy(fontSize = 20.sp, lineHeight = 26.sp),
            // The logged-set line: the primary content of the session screen, sized by role
            // rather than by an ad-hoc fontSize so the rule above stays honest.
            titleMedium = titleMedium.copy(fontSize = 22.sp, lineHeight = 28.sp),
            titleLarge = titleLarge.copy(fontSize = 28.sp, lineHeight = 36.sp),
        )
    }
