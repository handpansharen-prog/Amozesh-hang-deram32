package com.sharn.handpan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sharn.handpan.ui.HandpanViewModel
import com.sharn.handpan.ui.screens.PatternEditorScreen
import com.sharn.handpan.ui.theme.MyApplicationTheme
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PatternEditorUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectingEventShowsInspectorAndDeletingClearsSelection() {
        val viewModel = HandpanViewModel(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            MyApplicationTheme {
                PatternEditorScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("event_card_0").performClick()
        composeTestRule.onNodeWithTag("event_inspector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("event_delete_button").performClick()
        composeTestRule.onNodeWithTag("event_inspector").assertDoesNotExist()
    }
}
