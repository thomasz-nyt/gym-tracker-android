package com.gymtracker.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-43 / ADR-0035: the running pose is transcribed from the maintainer's source SVG (viewBox
 * `0 0 200 210`) as pure Kotlin so it can be asserted on the JVM, with no Robolectric and no
 * device — the thing that lets this land test-first at all, since Android has no way to play
 * the source's SMIL animation directly.
 *
 * What is pinned here is the *shape* of the motion, not pixel output: the loop closes, the two
 * legs (and the two arms) are exact mirror-phase copies of each other the way the source SVG's
 * `far`/`bd` polyline pairs are, and the bob and band-tail rotation hit the values the source's
 * `keyTimes`/`values` name. A wrong transcription that still "moves" would pass a screenshot
 * test's absence just as easily as it fails these.
 */
class RepMascotGeometryTest {
    @Test
    fun `the loop closes -- phase 0 and phase 1 are the same pose`() {
        val start = RepMascotGeometry.poseAt(0f)
        val end = RepMascotGeometry.poseAt(1f)

        assertApprox(start.bobOffsetY, end.bobOffsetY)
        assertPointsApprox(start.nearLeg, end.nearLeg)
        assertPointsApprox(start.farLeg, end.farLeg)
        assertPointsApprox(start.nearArm, end.nearArm)
        assertPointsApprox(start.farArm, end.farArm)
        assertApprox(start.bandTailRotationDeg, end.bandTailRotationDeg)
    }

    @Test
    fun `the bob rests at 0 on the beat and peaks at -6 on the offbeat`() {
        // Source: values="0,0; 0,-6; 0,0; 0,-6; 0,0" at keyTimes 0;0.25;0.5;0.75;1.
        assertApprox(0f, RepMascotGeometry.poseAt(0f).bobOffsetY)
        assertApprox(-6f, RepMascotGeometry.poseAt(0.25f).bobOffsetY)
        assertApprox(0f, RepMascotGeometry.poseAt(0.5f).bobOffsetY)
        assertApprox(-6f, RepMascotGeometry.poseAt(0.75f).bobOffsetY)
        assertApprox(0f, RepMascotGeometry.poseAt(1f).bobOffsetY)
    }

    @Test
    fun `the bob never overshoots -6, the peak the source animation names`() {
        val samples = generateSequence(0f) { it + SAMPLE_STEP }.takeWhile { it <= 1f }
        samples.forEach { phase ->
            val bob = RepMascotGeometry.poseAt(phase).bobOffsetY
            assertTrue(bob in BOB_PEAK..0f, "bob at phase $phase was $bob, outside [$BOB_PEAK, 0]")
        }
    }

    @Test
    fun `the near and far legs are exact mirror-phase copies of each other`() {
        // Source: the far leg goes forward-to-back-to-forward over 0;0.5;1, and the near leg
        // (the one actually bearing weight) goes back-to-forward-to-back on the same clock —
        // exact opposites, half a cycle out of phase with each other.
        val samples = generateSequence(0f) { it + SAMPLE_STEP }.takeWhile { it <= 0.5f }
        samples.forEach { phase ->
            val near = RepMascotGeometry.poseAt(phase).nearLeg
            val far = RepMascotGeometry.poseAt(phase + 0.5f).farLeg
            assertPointsApprox(near, far, "at phase $phase")
        }
    }

    @Test
    fun `the near and far arms are exact mirror-phase copies of each other`() {
        val samples = generateSequence(0f) { it + SAMPLE_STEP }.takeWhile { it <= 0.5f }
        samples.forEach { phase ->
            val near = RepMascotGeometry.poseAt(phase).nearArm
            val far = RepMascotGeometry.poseAt(phase + 0.5f).farArm
            assertPointsApprox(near, far, "at phase $phase")
        }
    }

    @Test
    fun `each leg has its own near-far mirror, distinct from the arms' motion`() {
        // A transcription slip that wired the arm keyframes into the legs (or vice versa) would
        // still pass every test above, since both pairs share the same mirror-phase shape.
        val legs = RepMascotGeometry.poseAt(0f).nearLeg
        val arms = RepMascotGeometry.poseAt(0f).nearArm
        assertTrue(legs != arms, "legs and arms must not share endpoints")
    }

    @Test
    fun `the band tail swings to -18 degrees at the midpoint and back, per the source keyframes`() {
        // Source: values="0 14 -13; -18 14 -13; 0 14 -13" at keyTimes 0;0.5;1.
        assertApprox(0f, RepMascotGeometry.poseAt(0f).bandTailRotationDeg)
        assertApprox(-18f, RepMascotGeometry.poseAt(0.5f).bandTailRotationDeg)
        assertApprox(0f, RepMascotGeometry.poseAt(1f).bandTailRotationDeg)
    }

    @Test
    fun `phase outside 0 to 1 wraps rather than throwing or extrapolating`() {
        val wrapped = RepMascotGeometry.poseAt(1.25f)
        val direct = RepMascotGeometry.poseAt(0.25f)
        assertApprox(direct.bobOffsetY, wrapped.bobOffsetY)
    }

    /**
     * ADR-0035's Turn 3 amendment: the source viewBox (`0 0 200 210`) was transcribed unchanged
     * from the SVG, but the figure's ink only ever occupied a fraction of it — `RepMascot`'s
     * `Canvas` fit the whole box, so an inline call site reserved far more room than the drawing
     * needed. The fix crops [RepMascotGeometry]'s box to the ink's bounding box, computed here
     * across the whole animation loop (not just phase 0) rather than eyeballed once from a
     * screenshot, which is what let the previous crop-free box go unnoticed in the first place —
     * every test above passes unchanged under a re-baselined coordinate space or a resized box,
     * so nothing here previously pinned the fit box to the ink at all.
     *
     * This union covers every drawn primitive from [RepMascot]'s draw calls — both limb
     * polylines, the torso curve, the head circle and its stroke, the nose, the band arc, and the
     * band tail through its full rotation — padded by each one's own stroke half-width, and
     * shifted by the bob at whichever phase produces it. It does not replicate the exact
     * rasterised pixels (round stroke caps add a fraction of a unit this ignores), which is why
     * the containment check below has real margin rather than an exact-edge assertion.
     */
    @Test
    fun `the crop contains every pose's ink, with margin, and is not much bigger than it needs to be`() {
        val ink = inkBoundingBox()

        val left = RepMascotGeometry.VIEW_BOX_LEFT
        val top = RepMascotGeometry.VIEW_BOX_TOP
        val right = left + RepMascotGeometry.VIEW_BOX_WIDTH
        val bottom = top + RepMascotGeometry.VIEW_BOX_HEIGHT

        assertTrue(ink.minX >= left, "ink's left edge ${ink.minX} is outside the crop's left edge $left")
        assertTrue(ink.maxX <= right, "ink's right edge ${ink.maxX} is outside the crop's right edge $right")
        assertTrue(ink.minY >= top, "ink's top edge ${ink.minY} is outside the crop's top edge $top")
        assertTrue(ink.maxY <= bottom, "ink's bottom edge ${ink.maxY} is outside the crop's bottom edge $bottom")

        // Tight, not just safe: a crop that (re)grew back toward the original 200x210 box would
        // still pass the containment checks above, and this is the only thing that would notice.
        val filledWidth = (ink.maxX - ink.minX) / RepMascotGeometry.VIEW_BOX_WIDTH
        val filledHeight = (ink.maxY - ink.minY) / RepMascotGeometry.VIEW_BOX_HEIGHT
        assertTrue(filledWidth >= MIN_FILL_FRACTION, "ink fills only ${filledWidth * 100}% of the crop's width")
        assertTrue(filledHeight >= MIN_FILL_FRACTION, "ink fills only ${filledHeight * 100}% of the crop's height")
    }

    /** The union of every drawn primitive's bounds, across the whole animation loop. */
    private fun inkBoundingBox(): Bounds {
        val bounds = Bounds()
        val phases = generateSequence(0f) { it + SAMPLE_STEP }.takeWhile { it < 1f }
        val head = RepMascotGeometry.headCenter

        phases.forEach { phase ->
            val pose = RepMascotGeometry.poseAt(phase)
            val bob = pose.bobOffsetY

            fun expand(
                point: Offset,
                pad: Float,
            ) = bounds.expand(point.x, point.y + bob, pad)

            pose.nearLeg.forEach { expand(it, NEAR_LEG_WIDTH / 2f) }
            pose.farLeg.forEach { expand(it, FAR_LIMB_WIDTH / 2f) }
            pose.nearArm.forEach { expand(it, NEAR_ARM_WIDTH / 2f) }
            pose.farArm.forEach { expand(it, FAR_LIMB_WIDTH / 2f) }

            sampleQuadratic(RepMascotGeometry.bodyStart, RepMascotGeometry.bodyControl, RepMascotGeometry.bodyEnd)
                .forEach { expand(it, BODY_WIDTH / 2f) }

            expand(head, RepMascotGeometry.HEAD_RADIUS + SKIN_OUTLINE_WIDTH / 2f)

            val noseStart = head + RepMascotGeometry.noseStart
            val noseControl = noseStart + RepMascotGeometry.noseControlDelta
            val noseEnd = noseStart + RepMascotGeometry.noseEndDelta
            sampleQuadratic(noseStart, noseControl, noseEnd).forEach { expand(it, NOSE_WIDTH / 2f) }

            expand(head + RepMascotGeometry.eyeCenter, RepMascotGeometry.EYE_RADIUS)

            sampleArc(
                center = head + RepMascotGeometry.bandArcCenter,
                radius = RepMascotGeometry.BAND_ARC_RADIUS,
                startDegrees = RepMascotGeometry.BAND_ARC_START_DEGREES,
                sweepDegrees = RepMascotGeometry.BAND_ARC_SWEEP_DEGREES,
            ).forEach { expand(it, BAND_ARC_WIDTH / 2f) }

            val tailStart = head + RepMascotGeometry.bandTailStart
            val tailControl = tailStart + RepMascotGeometry.bandTailControlDelta
            val tailEnd = tailStart + RepMascotGeometry.bandTailEndDelta
            val pivot = head + RepMascotGeometry.bandTailPivot
            sampleQuadratic(tailStart, tailControl, tailEnd)
                .map { rotateAbout(it, pivot, pose.bandTailRotationDeg) }
                .forEach { expand(it, BAND_TAIL_WIDTH / 2f) }
        }

        return bounds
    }

    /** Dense sampling rather than an analytic extremum — exact enough for a quadratic curve. */
    private fun sampleQuadratic(
        start: Offset,
        control: Offset,
        end: Offset,
        steps: Int = CURVE_SAMPLE_STEPS,
    ): List<Offset> =
        (0..steps).map { i ->
            val t = i / steps.toFloat()
            val u = 1f - t
            Offset(
                u * u * start.x + 2f * u * t * control.x + t * t * end.x,
                u * u * start.y + 2f * u * t * control.y + t * t * end.y,
            )
        }

    /** Samples the swept portion of a true circular arc, in the direction [sweepDegrees] runs. */
    private fun sampleArc(
        center: Offset,
        radius: Float,
        startDegrees: Float,
        sweepDegrees: Float,
        steps: Int = CURVE_SAMPLE_STEPS,
    ): List<Offset> =
        (0..steps).map { i ->
            val t = i / steps.toFloat()
            val radians = Math.toRadians((startDegrees + sweepDegrees * t).toDouble())
            Offset(
                (center.x + radius * cos(radians)).toFloat(),
                (center.y + radius * sin(radians)).toFloat(),
            )
        }

    /** Rotates [point] about [pivot] by [degrees], matching `DrawScope.rotate`'s clockwise sense. */
    private fun rotateAbout(
        point: Offset,
        pivot: Offset,
        degrees: Float,
    ): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val dx = (point.x - pivot.x).toDouble()
        val dy = (point.y - pivot.y).toDouble()
        val cosT = cos(radians)
        val sinT = sin(radians)
        return Offset(
            (pivot.x + dx * cosT - dy * sinT).toFloat(),
            (pivot.y + dx * sinT + dy * cosT).toFloat(),
        )
    }

    private data class Bounds(
        var minX: Float = Float.POSITIVE_INFINITY,
        var minY: Float = Float.POSITIVE_INFINITY,
        var maxX: Float = Float.NEGATIVE_INFINITY,
        var maxY: Float = Float.NEGATIVE_INFINITY,
    ) {
        fun expand(
            x: Float,
            y: Float,
            pad: Float,
        ) {
            minX = min(minX, x - pad)
            maxX = max(maxX, x + pad)
            minY = min(minY, y - pad)
            maxY = max(maxY, y + pad)
        }
    }

    private fun assertApprox(
        expected: Float,
        actual: Float,
        message: String = "",
    ) {
        assertTrue(
            abs(expected - actual) <= TOLERANCE,
            "expected $expected, was $actual $message".trim(),
        )
    }

    private fun assertPointsApprox(
        expected: List<Offset>,
        actual: List<Offset>,
        message: String = "",
    ) {
        assertEquals(expected.size, actual.size, "point count differs $message")
        expected.zip(actual).forEach { (e, a) ->
            assertApprox(e.x, a.x, message)
            assertApprox(e.y, a.y, message)
        }
    }

    private companion object {
        const val TOLERANCE = 0.01f
        const val SAMPLE_STEP = 0.05f
        const val BOB_PEAK = -6f
        const val CURVE_SAMPLE_STEPS = 24
        const val MIN_FILL_FRACTION = 0.85f
    }
}
