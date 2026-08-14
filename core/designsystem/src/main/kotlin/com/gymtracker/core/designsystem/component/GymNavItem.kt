package com.gymtracker.core.designsystem.component

import androidx.annotation.DrawableRes

/** One tab in [GymNavigationBar]: its icon and its label — the label is what tests and TalkBack read. */
data class GymNavItem(
    @param:DrawableRes val icon: Int,
    val label: String,
)
