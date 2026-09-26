package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun resetPersistedJourneyState() {
        rule.activityRule.scenario.onActivity { activity ->
            ArrivalAlarmRuntimeGraph.resetForTests()
            activity.getSharedPreferences("journey_state", Context.MODE_PRIVATE).edit().clear().commit()
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun launchExposesRealNationwideSearchAndLocationControls() {
        rule.onNodeWithTag("catalog-search").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("catalog-search-submit").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("search-target-status").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("search-target-origin").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag("search-target-destination").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag("nationwide-data-state").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("connector-setup-status").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("connector-connect").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("current-location-origin").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("arm").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag("cancel-alarm").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun productionUiExposesNoLegacyFixtureControls() {
        assertTrue(rule.onAllNodesWithTag("search-airport").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("plan-airport").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("approach").fetchSemanticsNodes().isEmpty())
    }

    @Test fun fullGermanyIndexDownloadControlIsReachableBeforeCacheExists() {
        rule.onNodeWithTag("nationwide-index-download").performScrollTo().assertIsDisplayed()
    }
}
