package com.nazofobi.arrivalalarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun assertPhase(expected: String) {
        rule.waitForIdle()
        rule.onNodeWithTag("phase")
            .assertTextContains("Durum: $expected", substring = true)
            .assertIsDisplayed()
    }

    @Test fun startDestinationArmedArrivedFlow() {
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

    @Test fun staticTransitSearchItineraryAndOfflineRecreation() {
        rule.waitForIdle()
        rule.onNodeWithTag("transit-state")
            .assertTextContains("READY", substring = true)
            .assertIsDisplayed()

        rule.onNodeWithTag("start").performClick()
        assertPhase("START_SELECTED")
        rule.onNodeWithTag("search-airport").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("stop-results")
            .performScrollTo()
            .assertTextContains("Flughafen", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithTag("plan-airport").performScrollTo().assertIsEnabled().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("itinerary-line")
            .performScrollTo()
            .assertTextContains("6", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithTag("itinerary-stops")
            .performScrollTo()
            .assertTextContains("Bremen Hbf", substring = true)
            .assertTextContains("Flughafen", substring = true)
            .assertIsDisplayed()

        rule.onNodeWithTag("destination").performScrollTo().assertIsEnabled().performClick()
        assertPhase("DESTINATION_SELECTED")
        rule.onNodeWithTag("arm").performScrollTo().assertIsEnabled().performClick()
        assertPhase("ARMED")

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("transit-state")
            .performScrollTo()
            .assertTextContains("READY", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithTag("search-airport").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("stop-results")
            .performScrollTo()
            .assertTextContains("Flughafen", substring = true)
            .assertIsDisplayed()
    }

    @Test fun compactScreenCriticalControlsAreScrollReachable() {
        rule.waitForIdle()
        rule.onNodeWithTag("start").assertIsDisplayed().performClick()
        rule.onNodeWithTag("destination").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        rule.onNodeWithTag("arm").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        rule.onNodeWithTag("approach").performScrollTo().assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithTag("search-airport").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("stop-results").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("plan-airport").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        rule.onNodeWithTag("itinerary-stops").performScrollTo().assertIsDisplayed()
    }
}
