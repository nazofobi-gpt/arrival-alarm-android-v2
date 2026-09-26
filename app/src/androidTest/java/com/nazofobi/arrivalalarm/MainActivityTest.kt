package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
            activity.getSharedPreferences("guidance_state", Context.MODE_PRIVATE).edit().clear().commit()
            activity.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun firstRunOnboardingExplainsConsentAndPersistsCompletion() {
        rule.onNodeWithTag("first-run-onboarding")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("onboarding-privacy-note")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("onboarding-complete")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue(rule.onAllNodesWithTag("first-run-onboarding").fetchSemanticsNodes().isEmpty())

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        assertTrue(rule.onAllNodesWithTag("first-run-onboarding").fetchSemanticsNodes().isEmpty())
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

    @Test fun journeyCoordinatesRestoreIntoProductionUiAfterRecreation() {
        rule.activityRule.scenario.onActivity { activity ->
            SharedPreferencesJourneyStateStore(activity).save(
                JourneyUiState(
                    phase = JourneyPhase.DESTINATION_SELECTED,
                    start = GeoPoint(52.665, 8.237),
                    destination = GeoPoint(53.083, 8.813),
                )
            )
            ArrivalAlarmRuntimeGraph.resetForTests()
        }

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.onNodeWithTag("origin-label").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("destination-label").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("arm").performScrollTo().assertIsDisplayed()
    }

    @Test fun largeFontKeepsCriticalJourneyControlsReachable() {
        rule.onNodeWithTag("catalog-search").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("transit-map").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("arm").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("guidance-permissions").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("trip-inference-toggle").performScrollTo().assertIsDisplayed()
    }

    @Test fun productionUiExposesInteractiveMapSurface() {
        val mapLabel = rule.activity.getString(R.string.map_point_label)
        rule.onNodeWithTag("transit-map")
            .performScrollTo()
            .assertIsDisplayed()
            .assertContentDescriptionEquals(mapLabel)
        rule.onNodeWithTag("map-instruction").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("map-provider").performScrollTo().assertIsDisplayed()
    }

    @Test fun productionUiExposesNoLegacyFixtureControls() {
        assertTrue(rule.onAllNodesWithTag("search-airport").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("plan-airport").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("approach").fetchSemanticsNodes().isEmpty())
    }

    @Test fun fullGermanyIndexDownloadControlIsReachableBeforeCacheExists() {
        rule.onNodeWithTag("nationwide-index-download").performScrollTo().assertIsDisplayed()
    }

    @Test fun settingsReadinessExposesExplicitRecoveryActions() {
        rule.onNodeWithTag("settings-readiness-panel")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("readiness-summary")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("readiness-permissions")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("readiness-guidance-permissions")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("readiness-refresh")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("readiness-app-settings")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("readiness-privacy")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun manualDarkThemePersistsAcrossActivityRecreation() {
        val darkLabel = rule.activity.getString(R.string.theme_dark)
        rule.onNodeWithTag("theme-dark")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        rule.onNodeWithTag("theme-mode-status")
            .performScrollTo()
            .assertTextContains(darkLabel, substring = true)

        rule.activityRule.scenario.onActivity { activity ->
            val persisted = activity
                .getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                .getString(ArrivalUiPreferences.KEY_THEME_MODE, null)
            assertEquals(ArrivalThemeMode.DARK.name, persisted)
        }

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.onNodeWithTag("theme-mode-status")
            .performScrollTo()
            .assertTextContains(darkLabel, substring = true)
    }

    @Test fun themeControlsExposeSelectionSemantics() {
        rule.onNodeWithTag("theme-system")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsSelected()
        rule.onNodeWithTag("theme-dark")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotSelected()
            .performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("theme-dark")
            .performScrollTo()
            .assertIsSelected()
        rule.onNodeWithTag("theme-system")
            .performScrollTo()
            .assertIsNotSelected()
    }

    @Test fun guidanceLanguageChoicePersistsAcrossActivityRecreation() {
        rule.onNodeWithTag("guidance-language-de-DE")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.onNodeWithTag("guidance-language-status")
            .performScrollTo()
            .assertTextContains("Deutsch", substring = true)

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.onNodeWithTag("guidance-language-status")
            .performScrollTo()
            .assertTextContains("Deutsch", substring = true)
    }
}
