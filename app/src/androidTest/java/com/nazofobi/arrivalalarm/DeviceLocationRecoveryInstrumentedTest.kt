package com.nazofobi.arrivalalarm

import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** System-location recovery is user-directed and never toggles device settings itself. */
@RunWith(AndroidJUnit4::class)
class DeviceLocationRecoveryInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private fun showSettings(locationEnabled: Boolean) {
        val state = SettingsReadinessState(
            themeMode = ArrivalThemeMode.SYSTEM,
            locationPermission = true,
            notificationPermission = true,
            locationServicesAvailable = locationEnabled,
            bluetoothPermission = true,
            guidancePermissionState = GuidancePermissionState.GRANTED,
            audioRoute = GuidanceAudioRoute.DEVICE,
            dataState = NationwideDataState.Idle,
            backgroundJourneyActive = false,
            connectorConfigured = false,
            connectorConnected = false,
        )
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsReadinessPanel(
                        state = state,
                        connectorBusy = false,
                        connectorStatus = "",
                        onThemeModeChange = {},
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
    }

    @Test
    fun locationEnabledDoesNotOfferUnnecessarySystemRecovery() {
        showSettings(locationEnabled = true)
        rule.onNodeWithTag("readiness-device-location-settings").assertDoesNotExist()
        rule.onNodeWithTag("readiness-app-settings").assertExists()
    }

    @Test
    fun locationDisabledOffersSystemRecoveryWithoutEnablingTracking() {
        showSettings(locationEnabled = false)
        rule.onNodeWithTag("readiness-device-location-settings").assertExists()
        rule.onNodeWithTag("readiness-app-settings").assertExists()
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, deviceLocationSettingsIntent().action)
        assertTrue(deviceLocationSettingsIntent().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun locationRecoveryOpensSystemScreenOnlyAfterExplicitClick() {
        showSettings(locationEnabled = false)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = Instrumentation.ActivityMonitor(
            IntentFilter(Settings.ACTION_LOCATION_SOURCE_SETTINGS), null, true,
        )
        instrumentation.addMonitor(monitor)
        try {
            rule.onNodeWithTag("readiness-device-location-settings")
                .performScrollTo()
                .performClick()
            rule.waitForIdle()
            assertTrue("Expected explicit system-location settings intent", instrumentation.checkMonitorHit(monitor, 1))
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
