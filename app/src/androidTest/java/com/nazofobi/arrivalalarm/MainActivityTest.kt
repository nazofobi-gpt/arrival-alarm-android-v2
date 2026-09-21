package com.nazofobi.arrivalalarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun startDestinationArmedArrivedFlow() {
        rule.onNodeWithTag("phase").assertTextContains("EMPTY").assertIsDisplayed()
        rule.onNodeWithTag("start").performClick()
        rule.onNodeWithTag("phase").assertTextContains("START_SELECTED")
        rule.onNodeWithTag("destination").assertIsEnabled().performClick()
        rule.onNodeWithTag("phase").assertTextContains("DESTINATION_SELECTED")
        rule.onNodeWithTag("arm").assertIsEnabled().performClick()
        rule.onNodeWithTag("phase").assertTextContains("ARMED")
        rule.onNodeWithTag("approach").assertIsEnabled().performClick()
        rule.onNodeWithTag("phase").assertTextContains("ARRIVED")
    }
}
