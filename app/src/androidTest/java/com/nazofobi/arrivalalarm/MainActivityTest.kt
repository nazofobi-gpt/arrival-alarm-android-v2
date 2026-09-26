package com.nazofobi.arrivalalarm

import android.content.Context
import java.io.File
import java.io.FileOutputStream
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
import androidx.test.platform.app.InstrumentationRegistry
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
            activity.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putBoolean("onboarding_complete", true)
                .commit()
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun firstRunOnboardingExplainsConsentAndPersistsCompletion() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_complete", false)
                .commit()
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

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
        rule.onNodeWithTag("bottom-navigation").assertIsDisplayed()

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        assertTrue(rule.onAllNodesWithTag("first-run-onboarding").fetchSemanticsNodes().isEmpty())
    }

    @Test fun launchExposesRealNationwideSearchAndLocationControls() {
        rule.onNodeWithTag("home-overview").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("bottom-navigation").assertIsDisplayed()
        rule.onNodeWithTag("home-current-location").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("transit-map").performScrollTo().assertIsDisplayed()

        navigateTo("nav-search")
        rule.onNodeWithTag("catalog-search").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("catalog-search-submit").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("search-target-status").performScrollTo().assertIsDisplayed()

        navigateTo("nav-settings")
        rule.onNodeWithTag("nationwide-data-state").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("connector-setup-status").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("connector-connect").performScrollTo().assertIsDisplayed()

        navigateTo("nav-journey")
        rule.onNodeWithTag("journey-empty-state").performScrollTo().assertIsDisplayed()
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

        rule.onNodeWithTag("home-origin").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("home-destination").performScrollTo().assertIsDisplayed()
        navigateTo("nav-journey")
        rule.onNodeWithTag("route-refresh").performScrollTo().assertIsDisplayed()
    }

    @Test fun largeFontKeepsCriticalJourneyControlsReachable() {
        rule.onNodeWithTag("home-overview").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("transit-map").performScrollTo().assertIsDisplayed()
        navigateTo("nav-search")
        rule.onNodeWithTag("catalog-search").performScrollTo().assertIsDisplayed()
        navigateTo("nav-settings")
        rule.onNodeWithTag("guidance-permissions").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("trip-inference-toggle").performScrollTo().assertIsDisplayed()
    }

    @Test fun wideWindowKeepsPrimaryContentAtReadableWidth() {
        val widthDp = rule.activity.resources.configuration.screenWidthDp
        val requireWide = InstrumentationRegistry.getArguments()
            .getString("requireWide")
            .equals("true", ignoreCase = true)

        if (widthDp < 900) {
            assertTrue(
                "Wide-window fixture was requested but screenWidthDp=$widthDp",
                !requireWide,
            )
            return
        }

        val density = rule.activity.resources.displayMetrics.density
        val contentWidthPx = rule.onNodeWithTag("adaptive-content")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val contentWidthDp = contentWidthPx / density

        assertTrue(
            "Adaptive content width was $contentWidthDp dp; expected <= 840dp",
            contentWidthDp <= 840.5f,
        )
    }

    @Test fun productionUiExposesInteractiveMapSurface() {
        val mapLabel = rule.activity.getString(R.string.map_point_label)
        rule.onNodeWithTag("transit-map")
            .performScrollTo()
            .assertIsDisplayed()
            .assertContentDescriptionEquals(mapLabel)
        rule.onNodeWithTag("home-map-provider").performScrollTo().assertIsDisplayed()
    }

    @Test fun productionUiExposesNoLegacyFixtureControls() {
        assertTrue(rule.onAllNodesWithTag("search-airport").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("plan-airport").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("approach").fetchSemanticsNodes().isEmpty())
    }

    @Test fun fullGermanyIndexDownloadControlIsReachableBeforeCacheExists() {
        navigateTo("nav-settings")
        rule.onNodeWithTag("nationwide-index-download").performScrollTo().assertIsDisplayed()
    }

    @Test fun settingsReadinessExposesExplicitRecoveryActions() {
        navigateTo("nav-settings")
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
        navigateTo("nav-settings")
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
        navigateTo("nav-settings")

        rule.onNodeWithTag("theme-mode-status")
            .performScrollTo()
            .assertTextContains(darkLabel, substring = true)
    }

    @Test fun tripInferenceSwitchHasTalkBackLabel() {
        navigateTo("nav-settings")
        val label = rule.activity.getString(R.string.inference_title)
        rule.onNodeWithTag("trip-inference-toggle")
            .performScrollTo()
            .assertIsDisplayed()
            .assertContentDescriptionEquals(label)
    }

    @Test fun criticalActionsMeet48DpTouchTargetFloor() {
        val minPixels = 48f * rule.activity.resources.displayMetrics.density
        listOf(
            "nav-home",
            "nav-search",
            "nav-journey",
            "nav-departures",
            "nav-settings",
        ).forEach { tag ->
            val node = rule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .fetchSemanticsNode()
            assertTrue(
                "$tag height=${node.boundsInRoot.height}px, required>=$minPixels",
                node.boundsInRoot.height + 0.5f >= minPixels,
            )
        }
        val homeLocation = rule.onNodeWithTag("home-current-location")
            .performScrollTo()
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertTrue(
            "home-current-location height=${homeLocation.boundsInRoot.height}px, required>=$minPixels",
            homeLocation.boundsInRoot.height + 0.5f >= minPixels,
        )
        navigateTo("nav-settings")
        listOf("theme-system", "theme-light", "theme-dark", "readiness-refresh").forEach { tag ->
            val node = rule.onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode()
            assertTrue(
                "$tag height=${node.boundsInRoot.height}px, required>=$minPixels",
                node.boundsInRoot.height + 0.5f >= minPixels,
            )
        }
    }

    @Test fun themeControlsExposeSelectionSemantics() {
        navigateTo("nav-settings")
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

    @Test fun primaryScreensAreSeparatedByBottomNavigation() {
        rule.onNodeWithTag("home-overview").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("settings-readiness-panel").fetchSemanticsNodes().isEmpty())

        navigateTo("nav-search")
        rule.onNodeWithTag("catalog-search").performScrollTo().assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("home-overview").fetchSemanticsNodes().isEmpty())

        navigateTo("nav-settings")
        rule.onNodeWithTag("settings-readiness-panel").performScrollTo().assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("catalog-search").fetchSemanticsNodes().isEmpty())
    }

    @Test fun capturePrimaryScreensForVisualQa() {
        captureScreen("home")
        navigateTo("nav-search")
        captureScreen("search")
        navigateTo("nav-journey")
        captureScreen("journey")
        navigateTo("nav-departures")
        captureScreen("departures")
        navigateTo("nav-settings")
        captureScreen("settings")
    }

    @Test fun guidanceLanguageChoicePersistsAcrossActivityRecreation() {
        navigateTo("nav-settings")
        rule.onNodeWithTag("guidance-language-de-DE")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.onNodeWithTag("guidance-language-status")
            .performScrollTo()
            .assertTextContains("Deutsch", substring = true)

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        navigateTo("nav-settings")

        rule.onNodeWithTag("guidance-language-status")
            .performScrollTo()
            .assertTextContains("Deutsch", substring = true)
    }

    private fun captureScreen(name: String) {
        rule.waitForIdle()
        val root = rule.activity.getExternalFilesDir(null) ?: return
        val dir = File(root, "ui-qa").apply { mkdirs() }
        val file = File(dir, "$name.png")
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        FileOutputStream(file).use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
    }

    private fun navigateTo(tag: String) {
        rule.onNodeWithTag(tag).assertIsDisplayed().performClick()
        rule.waitForIdle()
    }
}
