package com.gymtracker.core.designsystem.component

import org.junit.Test
import kotlin.math.abs
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
        expected: List<androidx.compose.ui.geometry.Offset>,
        actual: List<androidx.compose.ui.geometry.Offset>,
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
    }
}
