package com.gymtracker.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import com.gymtracker.core.designsystem.theme.LocalMascotBand
import kotlin.math.min

/**
 * Rep, running (US-43, ADR-0035). The generic mascot, not one of the maintainer's seven
 * machine-specific placards — those are out of scope for this ADR.
 *
 * Purely decorative: it carries no semantics node (a `Canvas` gets none unless one is added),
 * so it adds nothing for TalkBack to announce and nothing `TabNavigationTest` or any other
 * semantics-based test can trip over by existing.
 *
 * **Colour.** Ink reads `onSurface`, the head fill reads `surface`, so the drawing inverts with
 * the theme for free. The band reads [LocalMascotBand] — ADR-0035's one deliberate exception to
 * the mono palette — except when [monochrome] is set, which every accent-coloured surface (the
 * guided screen's `RestHero`) must pass: gold-on-red measures nowhere near WCAG's floor.
 *
 * **Motion.** One 860ms loop (the source SVG's `dur="0.86s"`), driven by
 * [rememberInfiniteTransition]. When the OS's "remove animations" developer option is on
 * (`ANIMATOR_DURATION_SCALE == 0`), this renders the phase-0 pose statically instead — both the
 * accessibility answer and a CI necessity, since the instrumented job runs with
 * `disable-animations: true`.
 */
@Composable
fun RepMascot(
    modifier: Modifier = Modifier,
    monochrome: Boolean = false,
) {
    val context = LocalContext.current
    val reduceMotion =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }

    val phase =
        if (reduceMotion) {
            0f
        } else {
            val transition = rememberInfiniteTransition(label = "rep-mascot")
            val animatedPhase by
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = LOOP_DURATION_MS, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "rep-mascot-phase",
                )
            animatedPhase
        }

    val pose = RepMascotGeometry.poseAt(phase)
    val inkColor = if (monochrome) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val skinColor = if (monochrome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val bandColor = if (monochrome) MaterialTheme.colorScheme.onPrimary else LocalMascotBand.current

    Canvas(modifier = modifier) {
        val scaleFactor =
            min(size.width / RepMascotGeometry.VIEW_BOX_WIDTH, size.height / RepMascotGeometry.VIEW_BOX_HEIGHT)
        val contentWidth = RepMascotGeometry.VIEW_BOX_WIDTH * scaleFactor
        val contentHeight = RepMascotGeometry.VIEW_BOX_HEIGHT * scaleFactor

        translate(
            left = (size.width - contentWidth) / 2f,
            top = (size.height - contentHeight) / 2f,
        ) {
            scale(scaleFactor, pivot = Offset.Zero) {
                translate(top = pose.bobOffsetY) {
                    drawFigure(pose, inkColor, skinColor, bandColor)
                }
            }
        }
    }
}

/** One frame: limbs, then the torso, then the head — the source SVG's own document order. */
private fun DrawScope.drawFigure(
    pose: RepPose,
    ink: Color,
    skin: Color,
    band: Color,
) {
    drawLimbs(pose, ink)
    drawTorso(ink)
    translate(left = RepMascotGeometry.headCenter.x, top = RepMascotGeometry.headCenter.y) {
        drawHead(pose, ink, skin, band)
    }
}

/**
 * Far limbs first, at reduced opacity, so the near limbs draw over them — matching the source
 * SVG's document order and its `.far { opacity: .24 }`.
 */
private fun DrawScope.drawLimbs(
    pose: RepPose,
    ink: Color,
) {
    drawPolyline(pose.farLeg, ink, FAR_LIMB_WIDTH, alpha = FAR_LIMB_ALPHA)
    drawPolyline(pose.farArm, ink, FAR_LIMB_WIDTH, alpha = FAR_LIMB_ALPHA)
    drawPolyline(pose.nearLeg, ink, NEAR_LEG_WIDTH)
    drawPolyline(pose.nearArm, ink, NEAR_ARM_WIDTH)
}

private fun DrawScope.drawTorso(ink: Color) {
    drawPath(
        path =
            Path().apply {
                moveTo(RepMascotGeometry.bodyStart.x, RepMascotGeometry.bodyStart.y)
                quadraticTo(
                    RepMascotGeometry.bodyControl.x,
                    RepMascotGeometry.bodyControl.y,
                    RepMascotGeometry.bodyEnd.x,
                    RepMascotGeometry.bodyEnd.y,
                )
            },
        color = ink,
        style = Stroke(width = BODY_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Head-local: the caller has already translated to [RepMascotGeometry.headCenter]. */
private fun DrawScope.drawHead(
    pose: RepPose,
    ink: Color,
    skin: Color,
    band: Color,
) {
    // `center` must be given explicitly: DrawScope.drawCircle's default is `this.center`, the
    // *untransformed canvas's* midpoint in raw pixel space — it ignores the translate this
    // function is already nested inside, unlike a coordinate baked into a Path. Omitting it
    // here drew a second, correctly-sized head circle stranded at the canvas's own centre.
    drawCircle(color = skin, radius = RepMascotGeometry.HEAD_RADIUS, center = Offset.Zero)
    drawCircle(
        color = ink,
        radius = RepMascotGeometry.HEAD_RADIUS,
        center = Offset.Zero,
        style = Stroke(width = SKIN_OUTLINE_WIDTH),
    )
    drawPath(
        path =
            Path().apply {
                moveTo(RepMascotGeometry.noseStart.x, RepMascotGeometry.noseStart.y)
                relativeQuadraticTo(
                    RepMascotGeometry.noseControlDelta.x,
                    RepMascotGeometry.noseControlDelta.y,
                    RepMascotGeometry.noseEndDelta.x,
                    RepMascotGeometry.noseEndDelta.y,
                )
            },
        color = ink,
        style = Stroke(width = NOSE_WIDTH, cap = StrokeCap.Round),
    )
    drawCircle(color = ink, radius = RepMascotGeometry.EYE_RADIUS, center = RepMascotGeometry.eyeCenter)
    drawBandArc(band)
    drawBandTail(pose, band)
}

private fun DrawScope.drawBandArc(band: Color) {
    val bandArcRect =
        Rect(center = RepMascotGeometry.bandArcCenter, radius = RepMascotGeometry.BAND_ARC_RADIUS)
    drawPath(
        path =
            Path().apply {
                arcTo(
                    rect = bandArcRect,
                    startAngleDegrees = RepMascotGeometry.BAND_ARC_START_DEGREES,
                    sweepAngleDegrees = RepMascotGeometry.BAND_ARC_SWEEP_DEGREES,
                    forceMoveTo = true,
                )
            },
        color = band,
        style = Stroke(width = BAND_ARC_WIDTH, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawBandTail(
    pose: RepPose,
    band: Color,
) {
    rotate(degrees = pose.bandTailRotationDeg, pivot = RepMascotGeometry.bandTailPivot) {
        drawPath(
            path =
                Path().apply {
                    moveTo(RepMascotGeometry.bandTailStart.x, RepMascotGeometry.bandTailStart.y)
                    relativeQuadraticTo(
                        RepMascotGeometry.bandTailControlDelta.x,
                        RepMascotGeometry.bandTailControlDelta.y,
                        RepMascotGeometry.bandTailEndDelta.x,
                        RepMascotGeometry.bandTailEndDelta.y,
                    )
                },
            color = band,
            style = Stroke(width = BAND_TAIL_WIDTH, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawPolyline(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    alpha: Float = 1f,
) {
    val path =
        Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** The source SVG's `dur="0.86s"` on the figure's bob (and, on the same clock, every limb). */
private const val LOOP_DURATION_MS = 860

// Stroke widths, transcribed from the source stylesheet's `.bd`/`.bdt`/`.far`/`.skin`/`.nose`/
// `.band` classes and the explicit `stroke-width` overrides on the body path and both band paths.
private const val BODY_WIDTH = 14f
private const val NEAR_LEG_WIDTH = 7f
private const val NEAR_ARM_WIDTH = 6f
private const val FAR_LIMB_WIDTH = 6f
private const val FAR_LIMB_ALPHA = 0.24f
private const val SKIN_OUTLINE_WIDTH = 4.5f
private const val NOSE_WIDTH = 4.5f
private const val BAND_ARC_WIDTH = 7f
private const val BAND_TAIL_WIDTH = 6f
