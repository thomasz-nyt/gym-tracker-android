package com.gymtracker.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset

/**
 * Rep's running pose, transcribed from the maintainer's source SVG (viewBox `0 0 200 210`) as
 * pure Kotlin (ADR-0035). Android has no way to play the source's SMIL animation
 * (`<animate>`/`<animateTransform>` with `keyTimes`/`keySplines`) directly, so this is the
 * re-authoring — and it is pure Kotlin specifically so [RepMascotGeometryTest] can assert on it
 * without Robolectric or a device, which is what lets this land test-first at all.
 *
 * [RepMascot] is the only consumer; it turns [poseAt] into drawn geometry every frame.
 */
internal object RepMascotGeometry {
    /**
     * The fit box `RepMascot`'s `Canvas` scales and centres the drawing into (ADR-0035's Turn 3
     * amendment). The source SVG's own `viewBox` is `0 0 200 210`, but the figure's ink only
     * spans a fraction of it: [VIEW_BOX_LEFT]/[VIEW_BOX_TOP] and this width/height crop that
     * unused margin away, so a caller sizing `RepMascot` by height gets a box that matches what
     * is actually drawn rather than one padded by empty space on every side. The values below
     * are the design bundle's own crop (`viewBox="46 20 84 148"`), verified by
     * `RepMascotGeometryTest` to contain every animated pose's ink with margin.
     */
    const val VIEW_BOX_LEFT = 46f
    const val VIEW_BOX_TOP = 20f
    const val VIEW_BOX_WIDTH = 84f
    const val VIEW_BOX_HEIGHT = 148f

    /** `RepMascot` sizes its `Canvas` to this ratio so a height-only modifier still fits. */
    const val ASPECT_RATIO = VIEW_BOX_WIDTH / VIEW_BOX_HEIGHT

    /** The source's `calcMode="spline" keySplines="0.4 0 0.5 1"`, on every animated segment. */
    private val Spline = CubicBezierEasing(0.4f, 0f, 0.5f, 1f)

    // ── Static geometry (view-box units, before the bob offset) ────────────────────────────
    // `M92,70 Q84,96 86,120`, stroke-width 14.
    val bodyStart = Offset(92f, 70f)
    val bodyControl = Offset(84f, 96f)
    val bodyEnd = Offset(86f, 120f)

    // The head's own <g transform="translate(90,52)">; everything below is head-local.
    val headCenter = Offset(90f, 52f)
    const val HEAD_RADIUS = 20f

    // `M-19,-2 q-7,4 -2,9`, head-local, relative quad.
    val noseStart = Offset(-19f, -2f)
    val noseControlDelta = Offset(-7f, 4f)
    val noseEndDelta = Offset(-2f, 9f)

    val eyeCenter = Offset(-9f, -4f)
    const val EYE_RADIUS = 3.1f

    // `M-18.5,-7 A20,20 0 0 1 14,-13.5`: a true circular arc (rx == ry), so its centre and
    // sweep are precomputed once via the SVG spec's endpoint-to-centre formula rather than
    // solved at draw time — this circle never moves relative to the head, only the head (and
    // the whole figure) does.
    val bandArcCenter = Offset(-0.054f, 0.730f)
    const val BAND_ARC_RADIUS = 20f
    const val BAND_ARC_START_DEGREES = -157.264f
    const val BAND_ARC_SWEEP_DEGREES = 111.908f

    // `M14,-13 q16,2 19,10`, head-local, relative quad. Rotates about [bandTailPivot].
    val bandTailStart = Offset(14f, -13f)
    val bandTailControlDelta = Offset(16f, 2f)
    val bandTailEndDelta = Offset(19f, 10f)
    val bandTailPivot = Offset(14f, -13f)

    // ── Animated geometry ───────────────────────────────────────────────────────────────────
    // The near leg/arm are the ones drawn at full opacity, `class="bd"`/`"bdt"`; the far ones
    // are `class="far"` at 24% opacity. Each pair swaps a "forward" and a "back" pose exactly
    // half a cycle out of phase with its partner — a scissor stride, not independent motion.
    private val legForward = listOf(Offset(86f, 120f), Offset(104f, 146f), Offset(118f, 156f))
    private val legBack = listOf(Offset(86f, 120f), Offset(62f, 138f), Offset(54f, 160f))
    private val armForward = listOf(Offset(88f, 86f), Offset(104f, 98f), Offset(98f, 110f))
    private val armBack = listOf(Offset(88f, 86f), Offset(70f, 96f), Offset(76f, 110f))

    // `values="0,0; 0,-6; 0,0; 0,-6; 0,0"` at `keyTimes="0;0.25;0.5;0.75;1"`.
    private val bobKeyTimes = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    private val bobValues = floatArrayOf(0f, -6f, 0f, -6f, 0f)

    /** `values="0 14 -13; -18 14 -13; 0 14 -13"` at `keyTimes="0;0.5;1"`. */
    private const val BAND_TAIL_ROTATION_PEAK_DEGREES = -18f

    /**
     * The pose at a point in the 0.86s loop. [phase] is fractional progress through the loop —
     * `0f` and `1f` are the same pose — and wraps for any input outside `[0, 1)`.
     */
    fun poseAt(phase: Float): RepPose {
        val p = phase.mod(1f)
        return RepPose(
            bobOffsetY = keyframed(p, bobKeyTimes, bobValues),
            nearLeg = halfCyclePoints(p, legBack, legForward),
            farLeg = halfCyclePoints(p, legForward, legBack),
            nearArm = halfCyclePoints(p, armBack, armForward),
            farArm = halfCyclePoints(p, armForward, armBack),
            bandTailRotationDeg = halfCycleScalar(p, 0f, BAND_TAIL_ROTATION_PEAK_DEGREES),
        )
    }

    /**
     * SMIL's `calcMode="spline"` over an arbitrary keyframe list: find the segment [phase]
     * falls in, ease the local fraction with [Spline], and lerp that segment's two values.
     */
    private fun keyframed(
        phase: Float,
        keyTimes: FloatArray,
        values: FloatArray,
    ): Float {
        for (i in 0 until keyTimes.size - 1) {
            val segmentEnd = keyTimes[i + 1]
            if (phase <= segmentEnd || i == keyTimes.size - 2) {
                val span = segmentEnd - keyTimes[i]
                val local = if (span == 0f) 0f else (phase - keyTimes[i]) / span
                val eased = Spline.transform(local.coerceIn(0f, 1f))
                return lerp(values[i], values[i + 1], eased)
            }
        }
        return values.last()
    }

    /** The `A -> B -> A` shape every animated value in this file shares, over `keyTimes 0;0.5;1`. */
    private fun halfCycleScalar(
        phase: Float,
        a: Float,
        b: Float,
    ): Float {
        val (from, to, local) =
            if (phase <= 0.5f) Triple(a, b, phase / 0.5f) else Triple(b, a, (phase - 0.5f) / 0.5f)
        return lerp(from, to, Spline.transform(local.coerceIn(0f, 1f)))
    }

    private fun halfCyclePoints(
        phase: Float,
        a: List<Offset>,
        b: List<Offset>,
    ): List<Offset> {
        val (from, to, local) =
            if (phase <= 0.5f) Triple(a, b, phase / 0.5f) else Triple(b, a, (phase - 0.5f) / 0.5f)
        val eased = Spline.transform(local.coerceIn(0f, 1f))
        return from.zip(to) { f, t -> Offset(lerp(f.x, t.x, eased), lerp(f.y, t.y, eased)) }
    }

    private fun lerp(
        start: Float,
        stop: Float,
        fraction: Float,
    ): Float = start + (stop - start) * fraction
}

/** One frame of [RepMascotGeometry.poseAt], everything a draw call needs and nothing static. */
internal data class RepPose(
    val bobOffsetY: Float,
    val nearLeg: List<Offset>,
    val farLeg: List<Offset>,
    val nearArm: List<Offset>,
    val farArm: List<Offset>,
    val bandTailRotationDeg: Float,
)
