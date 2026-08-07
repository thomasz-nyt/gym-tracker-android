package com.gymtracker.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's shape scale (ADR-0019): every radius is 0.
 *
 * Two reasons, both about the gym floor rather than taste. A square button is a larger target
 * than a stadium of the same height — the corners are area you can actually hit with a thumb
 * you are not looking at. And a flush-left label in a square field sits where the eye already
 * is, instead of being inset by a curve.
 *
 * Material's scale varies the radius by role, which is exactly the choice this removes: every
 * role is the same square, so no screen can reach for a slightly rounder one. Feature code
 * names a role and never a corner, the same rule ADR-0011 wrote for `sp` and ADR-0016 for `dp`.
 *
 * **Setting this object is not sufficient, and that is a trap worth knowing about.** Buttons,
 * FABs and chips do not read the scale at all — their default is `CornerFull`, a 50% radius
 * that is not one of the five roles below, so they stay stadium-shaped no matter what is set
 * here. They have to be passed a shape explicitly. `PrimaryActionButton` and
 * `SecondaryActionButton` do exactly that.
 *
 * This is the same shape of bug as the violet `outlineVariant` in finding 08: the theme looks
 * overridden, the component reads a different token, and it only shows up on a device. If a
 * control renders round, check whether it reads `CornerFull` before assuming this file is
 * wrong.
 */
private val Square = RoundedCornerShape(0.dp)

val GymShapes: Shapes =
    Shapes(
        extraSmall = Square,
        small = Square,
        medium = Square,
        large = Square,
        extraLarge = Square,
    )
