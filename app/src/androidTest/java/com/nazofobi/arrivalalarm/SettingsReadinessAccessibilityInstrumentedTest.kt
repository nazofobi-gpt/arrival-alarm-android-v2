package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsReadinessAccessibilityInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun talkBackCanIdentifyThemePickerStateAndReadinessChanges() {
        var theme by mutableStateOf(ArrivalThemeMode.SYSTEM)
        var locationAvailable by mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expanded = context.getString(R.string.readiness_theme_options_expanded)
        val collapsed = context.getString(R.string.readiness_theme_options_collapsed)
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsReadinessPanel(
                        state = SettingsReadinessState(
                            themeMode = theme,
                            locationPermission = true,
                            notificationPermission = true,
                            locationServicesAvailable = locationAvailable,
                            bluetoothPermission = true,
                            guidancePermissionState = GuidancePermissionState.GRANTED,
                            audioRoute = GuidanceAudioRoute.DEVICE,
                            dataState = NationwideDataState.Idle,
                            backgroundJourneyActive = false,
                            connectorConfigured = false,
                            connectorConnected = false,
                        ),
                        connectorBusy = false,
                        connectorStatus = "",
                        onThemeModeChange = { theme = it },
                        onRequestJourneyPermissions = {},
                        onRequestGuidancePermissions = {},
                        onRefreshReadiness = {},
                        onShareDiagnostics = {},
                        onOpenAppSettings = {},
                        onRetryData = {},
                        onConnectorAction = {},
                    )
                }
            }
        }
        val picker = rule.onNodeWithTag("theme-mode-picker")
        picker.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        picker.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, collapsed))
        rule.onNodeWithTag("theme-system").assertDoesNotExist()

        picker.performClick()
        picker.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expanded))
        rule.onNodeWithTag("theme-dark").assertExists().performClick()
        picker.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, collapsed))
        rule.runOnIdle { assertEquals(ArrivalThemeMode.DARK, theme) }

        val summary = rule.onNodeWithTag("readiness-summary")
        summary.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.runOnIdle { locationAvailable = true }
        summary.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
}
