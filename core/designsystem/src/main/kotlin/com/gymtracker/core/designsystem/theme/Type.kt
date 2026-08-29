package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
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
 *
 * **Five more roles, added by ADR-0029** for pixel values the session-screen redesign needs
 * that nothing above covers, filling previously-unused `Typography` slots rather than adding a
 * parallel token system:
 *
 * - `displayLarge` — the rest/warm-up countdown. This is the role `Type.kt` always said would
 *   be the countdown (see the comment on `displayLarge` below, which predates ADR-0029 and was
 *   never actually wired up — `RestPanel` read `displayMedium` instead). ADR-0029 left
 *   `displayMedium` alone specifically because `GuidedExerciseScreen`'s rep counter (ADR-0017,
 *   a different feature) also read it; ADR-0033 moved that screen's countdown to `displayLarge`
 *   too, so `displayMedium` is now read nowhere in the app. It stays at Material's default size
 *   with Archivo wired — the same treatment `displaySmall`/`headlineLarge` get — rather than
 *   being claimed for something that does not yet exist, so an accidental future use does not
 *   silently fall back to the system font.
 * - `displaySmall` — the persistent live-heart-rate readout (US-47). It is large enough to
 *   read at arm's length without competing with `headlineMedium` or the countdown. Keeping the
 *   complete 36sp/44sp style here prevents a feature-level font-size copy from inheriting the
 *   16sp line height of a section eyebrow.
 * - `headlineMedium` — the rest banner's big weight readout.
 * - `headlineSmall` — a movement name, on the session screen or in the rest banner's "Up next".
 * - `labelMedium` / `labelSmall` — row labels and section eyebrows. **Both sit below ADR-0011's
 *   16sp content floor, deliberately.** `GymTypographyTest`'s "nothing smaller than sixteen sp"
 *   test covers primary content — what a member reads under load, the reason that rule exists.
 *   These two are wayfinding labels (`SET 1`, `NEXT`, section eyebrows), a different class of
 *   text the same way Material's own `labelSmall` default (11sp) already treats it, and they
 *   are deliberately left out of that test's `roles` map rather than silently exempted.
 *
 * The design's 15sp meta text (a session's elapsed-time line, a movement's target line) reads
 * through the existing `bodySmall` (16sp) instead of a sixth new role — one pixel below the
 * design and above ADR-0011's own floor is the smaller deviation.
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
            // ADR-0029: the rest/warm-up countdown. Read directly from the design bundle's `1a
            // Session resting` frame — 104sp is not a typo, it is meant to be legible from where
            // you are actually standing, several feet from the phone.
            displayLarge =
                displayLarge.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 104.sp,
                    lineHeight = 108.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.05).em,
                ),
            // Untouched at Material's default size (ADR-0033): no role reads this slot since
            // GuidedExerciseScreen's countdown moved to displayLarge. Left unclaimed rather than
            // pre-committed to a screen that does not exist, and still carries Archivo so an
            // accidental future use does not silently fall back to Roboto.
            displayMedium = displayMedium.copy(fontFamily = ArchivoFamily),
            // US-47: a persistent vital reading, selected on device at arm's length. This is a
            // content role rather than a section eyebrow, so it owns a complete type style.
            displaySmall =
                displaySmall.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 36.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Medium,
                ),
            headlineLarge = headlineLarge.copy(fontFamily = ArchivoFamily),
            // ADR-0029: the rest banner's big weight readout ("100 lb × 8 · 45.4 kg").
            headlineMedium =
                headlineMedium.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.03).em,
                ),
            // ADR-0029: a movement's name, on the session screen or in "Up next".
            headlineSmall =
                headlineSmall.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 27.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).em,
                ),
            // ADR-0029: "SET 1" / "NEXT" row labels. Below ADR-0011's 16sp content floor on
            // purpose — see the class doc's "Five more roles" note.
            labelMedium =
                labelMedium.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            // ADR-0029: section eyebrows ("Exercise 3 of 6", "Still to come", "Rest", "Up next").
            // Uppercased at the call site, not here — see NumeralText/ButtonLabel's precedent for
            // why a visual-only transform is applied to the string rather than the TextStyle.
            labelSmall =
                labelSmall.copy(
                    fontFamily = ArchivoFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.12.em,
                ),
        )
    }

/**
 * ADR-0011's Turn 4 amendment: a role that carries its own line ceiling, so a call site names a
 * role and cannot forget `maxLines` the way 205 raw `Text()` calls across the feature modules
 * did before this existed — see [GymTextRoles] for the ten roles and
 * [com.gymtracker.core.designsystem.component.GymText] for the composable that reads both.
 *
 * **Additive to [GymTypography], not a replacement for it.** Nothing in `MaterialTheme.typography`
 * changes value here — see the amendment's "An additive scale, not a value change" for why: those
 * slots are shared across every screen in the app, most of them untouched by this pass, and
 * repointing a shared slot's value to a Turn-4 number would reflow every reader as a side effect
 * of a change scoped to six screens.
 *
 * @property overflow [TextOverflow.Clip] for the numeral/word roles — an ellipsis mid-digit or
 *   mid-unit-word reads as broken, and the whole point of the split baseline row (a load line as
 *   separate `Text`s rather than one formatted string) is that each piece is short enough it
 *   should never need to truncate in the first place.
 */
data class GymTextRole(
    val style: TextStyle,
    val maxLines: Int,
    val overflow: TextOverflow = TextOverflow.Ellipsis,
)

/**
 * The ten roles from `Redesign.dc.html` Turn 4, frame `4f` (ADR-0011's amendment). Every size
 * on the six migrated screens comes from here; feature code never names a raw `sp`, the same
 * rule [GymTypography]'s own class doc states for the original scale.
 */
object GymTextRoles {
    /** The rest and warm-up countdown. Nowhere else. Tabular figures. */
    val DisplayTimer =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 88.sp,
                    // Deliberately tighter than the font size — the same sub-1 line-height ratio
                    // Redesign.dc.html's own display type uses throughout. A single-line tabular
                    // countdown, not body copy: there is no second line for a tight box to crowd.
                    lineHeight = 75.68.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )

    /** Target load, rep count, est. 1RM. Digits only. */
    val NumeralLg =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 34.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )

    /** A stepper's value. */
    val NumeralMd =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )

    /** Screen titles, an exercise name in rest / up next, a dialog question. */
    val TitleLg =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 22.sp,
                    lineHeight = 23.76.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            maxLines = 2,
        )

    /** List row names, history titles, a log button's value line. */
    val TitleMd =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 17.sp,
                    lineHeight = 20.4.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            maxLines = 2,
        )

    /** A word standing where a numeral would (`Bodyweight`), and unit suffixes (`lb`, `kg`). */
    val WordUnit =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )

    /** Card names, the little prose that survives. */
    val Body =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 15.sp,
                    lineHeight = 20.25.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 2,
        )

    /** Set counts, dates, targets, secondary units, `of 1:00`. */
    val Meta =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 13.sp,
                    lineHeight = 16.9.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            maxLines = 1,
        )

    /** `REST`, `THEN`, `UP NEXT`, section labels, stat rows, secondary buttons. */
    val LabelCaps =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 12.sp,
                    lineHeight = 13.2.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.12.em,
                ),
            maxLines = 1,
        )

    /** The `ADDED` tag, kickers inside filled buttons. The floor — nothing smaller ships. */
    val TagCaps =
        GymTextRole(
            style =
                TextStyle(
                    fontFamily = ArchivoFamily,
                    fontSize = 11.sp,
                    lineHeight = 12.1.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.06.em,
                ),
            maxLines = 1,
        )
}
