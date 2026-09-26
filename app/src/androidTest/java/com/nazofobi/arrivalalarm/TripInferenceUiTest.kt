package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripInferenceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun resetJourneyState() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("journey_state", Context.MODE_PRIVATE).edit().clear().commit()
            activity.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_complete", true)
                .commit()
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun inferenceIsOptInVisibleAndConfirmationDoesNotArmAlarm() {
        rule.onNodeWithTag("nav-settings").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("trip-inference-status").performScrollTo()
            .assertTextContains(rule.activity.getString(R.string.inference_disabled), substring = true).assertIsDisplayed()
        rule.onNodeWithTag("trip-inference-run").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("trip-inference-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("trip-inference-status").performScrollTo()
            .assertTextContains(rule.activity.getString(R.string.inference_enabled), substring = true)
        rule.onNodeWithTag("trip-inference-run").performScrollTo().assertIsNotEnabled()
        rule.activityRule.scenario.onActivity { activity ->
            assertEquals(
                JourneyPhase.EMPTY,
                ArrivalAlarmRuntimeGraph.get(activity).controller.state.phase,
            )
        }
    }
}
