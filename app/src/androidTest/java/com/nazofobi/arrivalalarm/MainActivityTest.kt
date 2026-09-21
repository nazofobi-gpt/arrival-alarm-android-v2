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

    private fun assertPhase(expected: String) {
        rule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                rule.onNodeWithTag("phase").fetchSemanticsNode()
                    .config.toString().contains(expected)
            }.getOrDefault(false)
        }
        rule.onNodeWithTag("phase").assertTextContains(expected).assertIsDisplayed()
    }

    @Test fun startDestinationArmedArrivedFlow() {
        rule.waitForIdle()
        assertPhase("EMPTY")
        rule.onNodeWithTag("start").performClick()
        assertPhase("START_SELECTED")
        rule.onNodeWithTag("destination").assertIsEnabled().performClick()
        assertPhase("DESTINATION_SELECTED")
        rule.onNodeWithTag("arm").assertIsEnabled().performClick()
        assertPhase("ARMED")
        rule.onNodeWithTag("approach").assertIsEnabled().performClick()
        assertPhase("ARRIVED")
    }
}
