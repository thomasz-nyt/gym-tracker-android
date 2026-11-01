package com.gymtracker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import com.gymtracker.core.designsystem.component.StepperField
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class NumericFieldAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun typedNumericFieldCanBeIdentifiedWithoutReadingItsValue() {
        var typed = ""
        compose.setContent { GymTrackerTheme { StepperField("Reps", "12", { typed = it }, {}) } }
        compose.onNodeWithContentDescription("Reps").assertIsDisplayed().performTextReplacement("10")
        assertEquals("10", typed)
    }
}
