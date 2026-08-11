package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gymtracker.core.designsystem.R

/**
 * The five Archivo cuts the app draws with.
 *
 * Named rather than numeric so detekt's `MagicNumber` rule stays on. Numbers take ExtraBold, so
 * a load reads across a gym floor (ADR-0019).
 */
private val ArchivoWeights =
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
    )

/**
 * Archivo (ADR-0019), shipped as the single variable font Google publishes rather than a set of
 * static weights: one 640 KB file covers 100–900, which is smaller than the four static cuts the
 * app would otherwise need and leaves every other weight available for free.
 *
 * It is **bundled, not a Downloadable Font.** Constitution §2 says the gym has no signal, and a
 * typeface that arrives over the network is a typeface that is missing exactly where the app is
 * read. Variable-font weights need API 26, which is the app's `minSdk`.
 *
 * `variationSettings` is still marked experimental in Compose, and it is the only way to pin a
 * weight axis on a variable font from Kotlin. The alternative is an XML `<font-family>` that
 * hard-codes the same axis values in a second place. Opting in keeps one source of truth, and
 * the API is additive — if it changes, this file is the only thing that has to follow.
 */
@OptIn(ExperimentalTextApi::class)
internal val ArchivoFamily =
    FontFamily(
        ArchivoWeights.map { weight ->
            Font(
                resId = R.font.archivo_variable,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

/**
 * The app's type scale (ADR-0011), set in Archivo (ADR-0019).
 *
 * Material 3's defaults are sized for reading a phone held at a desk. This app is read
 * standing up, at arm's length, between sets — the completed-set line was a 12sp `bodySmall`,
 * which is what prompted the change. Every role the app renders is raised, so the decision
 * lives here rather than being re-made by whoever writes the next screen.
 *
 * ADR-0019 changed the typeface and kept every size, so the two rules below are unchanged:
 *
 * - **Feature code never hard-codes an `sp` value.** It picks a role off
 *   `MaterialTheme.typography`, so M7's accessibility pass tunes one file.
 * - **Sizes stay in `sp`**, so the OS font-size setting still multiplies them. A member who
 *   has already turned system text up gets larger text still; capping that would be the
 *   accessibility bug M7 exists to catch.
 *
 * **Weight hierarchy**, added on top of ADR-0019's sizes. ADR-0019 says numbers carry weight
 * 800 (see `NumeralText`) but never said what titles and meta text carry, which is why every
 * screen sat in the same narrow mid-weight band regardless of role. `titleLarge` (screen and
 * section titles — "Routines", "Workout complete", an exercise name) and `titleSmall` (row
 * labels — "Rest", "Up next") move to ExtraBold; the body roles, which read as meta text under
 * a heavier line, move to Medium. **`titleMedium` is deliberately left alone.** It is Compose's
 * default weight, not ADR-0019's, and it is also the role `LoggedSets` and `RestPanel`'s "Up
 * next" render — lines that mix words and numbers in one string. `NumeralText` creates its
 * contrast by bolding only the digit runs *within* a line; if the line's own base weight were
 * already ExtraBold, that span would be invisible and the two would read identically. Every
 * other role above is either pure words or pure meta, so raising its base weight costs nothing.
 */
val GymTypography: Typography =
    Typography().run {
        copy(
            bodySmall =
                bodySmall.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                ),
            bodyMedium =
                bodyMedium.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                ),
            bodyLarge =
                bodyLarge.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                ),
            // Button and chip text.
            labelLarge = labelLarge.copy(fontFamily = ArchivoFamily, fontSize = 18.sp, lineHeight = 24.sp),
            titleSmall =
                titleSmall.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            // The logged-set line: the primary content of the session screen, sized by role
            // rather than by an ad-hoc fontSize so the rule above stays honest. Weight is left at
            // Compose's default — see the class doc's "titleMedium is deliberately left alone".
            titleMedium = titleMedium.copy(fontFamily = ArchivoFamily, fontSize = 22.sp, lineHeight = 28.sp),
            titleLarge =
                titleLarge.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            // The roles below keep Material's sizes — ADR-0011 only raised what the app renders
            // today. They carry the family anyway so that the next screen to reach for one gets
            // Archivo rather than silently reintroducing Roboto. `displayLarge` is the rest
            // countdown (ADR-0016), which is the first of these that will actually be used.
            displayLarge = displayLarge.copy(fontFamily = ArchivoFamily),
            displayMedium = displayMedium.copy(fontFamily = ArchivoFamily),
            displaySmall = displaySmall.copy(fontFamily = ArchivoFamily),
            headlineLarge = headlineLarge.copy(fontFamily = ArchivoFamily),
            headlineMedium = headlineMedium.copy(fontFamily = ArchivoFamily),
            headlineSmall = headlineSmall.copy(fontFamily = ArchivoFamily),
            labelMedium = labelMedium.copy(fontFamily = ArchivoFamily),
            labelSmall = labelSmall.copy(fontFamily = ArchivoFamily),
        )
    }
