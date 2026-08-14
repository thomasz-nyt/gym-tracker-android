package com.gymtracker.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * A bundled exercise photograph, always through the Modernist design system's grayscale
 * treatment (the imported `_ds` bundle's stylesheet): "Wrap hero and inline images in
 * the `.grayscale` class — they print in pure black and white," and "Do not tint or colorize
 * imagery." Redesign audit, PR A finding 2 — the app's three photo call sites (catalog
 * thumbnail, exercise detail hero, workout-detail thumbnail) each called `AsyncImage` directly
 * and none of them carried this. Routing all three through one composable means a future call
 * site cannot forget it the way these three did.
 */
@Composable
fun GymPhoto(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = ColorFilter.colorMatrix(GrayscaleColorMatrix),
        modifier = modifier,
    )
}

/**
 * `filter: grayscale(1) contrast(1.08)` as one affine transform: a luminance-weighted
 * desaturation (the standard Rec. 601-ish weights `ColorMatrix.setToSaturation(0f)` uses),
 * scaled by the contrast gain and re-centred on mid-grey so the stretch pivots there rather
 * than at black. Built directly rather than composed from two `ColorMatrix` values, so the
 * numbers are pinned by [com.gymtracker.core.designsystem.component.GymPhotoTest] without
 * depending on `ColorMatrix.timesAssign`'s multiplication order.
 */
internal val GrayscaleColorMatrix: ColorMatrix =
    run {
        val r = LUMA_R * GRAYSCALE_CONTRAST
        val g = LUMA_G * GRAYSCALE_CONTRAST
        val b = LUMA_B * GRAYSCALE_CONTRAST
        val translate = MID_GREY * (1f - GRAYSCALE_CONTRAST)

        ColorMatrix(
            floatArrayOf(
                r,
                g,
                b,
                0f,
                translate,
                r,
                g,
                b,
                0f,
                translate,
                r,
                g,
                b,
                0f,
                translate,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
        )
    }

// The Rec. 601-ish luminance weights ColorMatrix.setToSaturation(0f) uses.
private const val LUMA_R = 0.213f
private const val LUMA_G = 0.715f
private const val LUMA_B = 0.072f

/** `.grayscale`'s `contrast(1.08)`. */
private const val GRAYSCALE_CONTRAST = 1.08f

/** The pivot for the contrast stretch — 8-bit mid-grey. */
private const val MID_GREY = 127.5f
