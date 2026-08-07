package com.gymtracker.core.designsystem.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

/**
 * ADR-0019: every radius is 0, as one `Shapes()` object.
 *
 * The rule this protects is the same one ADR-0011 wrote for `sp` and ADR-0016 wrote for `dp`:
 * feature code names a role, never a corner. If a screen ever wants a rounded thing it has to
 * come back here and change the system, which is the point.
 */
class GymShapesTest {
    private val density = Density(density = 1f)
    private val anySize = Size(width = 100f, height = 100f)

    private fun cornersOf(shape: RoundedCornerShape): List<Float> =
        listOf(
            shape.topStart,
            shape.topEnd,
            shape.bottomEnd,
            shape.bottomStart,
        ).map { it.toPxOnAnySurface() }

    private fun CornerSize.toPxOnAnySurface(): Float = toPx(anySize, density)

    @Test
    fun `every corner in the scale is square`() {
        val roles =
            mapOf(
                "extraSmall" to GymShapes.extraSmall,
                "small" to GymShapes.small,
                "medium" to GymShapes.medium,
                "large" to GymShapes.large,
                "extraLarge" to GymShapes.extraLarge,
            )
        roles.forEach { (role, shape) ->
            cornersOf(shape as RoundedCornerShape).forEachIndexed { index, px ->
                assertEquals(
                    0f,
                    px,
                    "$role corner $index is ${px}px — ADR-0019 puts every radius at 0",
                )
            }
        }
    }

    @Test
    fun `the scale does not vary by role, so no screen can pick a rounder one`() {
        // A single shared shape means there is no "slightly rounder" option to reach for.
        assertEquals(RoundedCornerShape(0.dp), GymShapes.small)
        assertEquals(GymShapes.small, GymShapes.large)
    }
}
