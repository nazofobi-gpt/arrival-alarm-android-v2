package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun resetPersistedJourneyState() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("journey_state", Context.MODE_PRIVATE).edit().clear().commit()
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun launchExposesRealNationwideSearchAndLocationControls() {
        rule.onNodeWithTag("catalog-search").assertIsDisplayed()
        rule.onNodeWithTag("catalog-search-submit").assertIsNotEnabled()
        rule.onNodeWithTag("nationwide-data-state").assertIsDisplayed()
        rule.onNodeWithTag("current-location-origin").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("arm").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun productionUiHasNoFixtureOrDemoTransitControls() {
        rule.onNodeWithText("Durak ara: Flughafen").assertDoesNotExist()
        rule.onNodeWithText("Bremen Hbf → Flughafen rotası").assertDoesNotExist()
        rule.onNodeWithText("Mevcut konum (demo)").assertDoesNotExist()
        rule.onNodeWithText("Test: hedefe yaklaş").assertDoesNotExist()
    }

    @Test fun fullGermanyIndexDownloadControlIsReachableBeforeCacheExists() {
        rule.onNodeWithTag("nationwide-index-download").performScrollTo().assertIsDisplayed()
    }
}
