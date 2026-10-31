package com.gymtracker

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Follow the secondary actions without depending on retired guided-screen controls. */
internal fun ComposeContentTestRule.openFocusMore() {
    waitUntil(10_000) {
        onAllNodesWithContentDescription("More exercise actions").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithContentDescription("More exercise actions").performClick()
}

internal fun ComposeContentTestRule.openFocusRecords() {
    openFocusMore()
    onNodeWithText("Sets, history & target").performScrollTo().performClick()
}

internal fun ComposeContentTestRule.finishFocusWorkout(empty: Boolean = false) {
    openFocusMore()
    onNodeWithText("Finish workout").performScrollTo().performClick()
    onAllNodesWithText(if (empty) "Discard workout" else "Finish workout").onLast().performClick()
}
