package com.nazofobi.arrivalalarm

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunOnboardingAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun optionalQuickSetupIsDiscoverableAndDoesNotBlockContinue() {
        val continued = mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued.value = true },
                        offlineDataState = NationwideDataState.Idle,
                        onDownloadOfflineData = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-accessibility-setup")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-accessibility-settings")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-complete")
            .performScrollTo().performClick()
        rule.runOnIdle { assertTrue(continued.value) }
    }

    @Test fun offlineDownloadIsOptionalAndHasLiveRetryAndReadyStates() {
        var offlineState by androidx.compose.runtime.mutableStateOf<NationwideDataState>(
            NationwideDataState.Idle
        )
        var downloadCalls = 0
        var continued = false
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued = true },
                        offlineDataState = offlineState,
                        onDownloadOfflineData = { downloadCalls += 1 },
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-offline-setup").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-offline-download").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, downloadCalls) }

        rule.runOnIdle { offlineState = NationwideDataState.Loading("downloading") }
        rule.onNodeWithTag("onboarding-offline-status").assertIsDisplayed()
        rule.onNodeWithTag("onboarding-offline-download").assertDoesNotExist()

        rule.runOnIdle { offlineState = NationwideDataState.Error("network unavailable") }
        rule.onNodeWithTag("onboarding-offline-download").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(2, downloadCalls) }

        rule.runOnIdle {
            offlineState = NationwideDataState.Ready(
                stopCount = 12_345,
                fetchedAtEpochSeconds = 1L,
                sourceVersion = "test",
            )
        }
        rule.onNodeWithTag("onboarding-offline-download").assertDoesNotExist()
        rule.onNodeWithTag("onboarding-complete").performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(continued)
            assertEquals(2, downloadCalls)
        }
    }

    @Test fun offlineDownloadNeverStartsWhenUserSkipsSetup() {
        var downloadCalls = 0
        var continued = false
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued = true },
                        offlineDataState = NationwideDataState.Idle,
                        onDownloadOfflineData = { downloadCalls++ },
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-complete").performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(continued)
            assertEquals(0, downloadCalls)
        }
    }


    @Test fun announcementLanguageCanBeSetBeforeFirstTripWithoutBlockingContinue() {
        var selected by mutableStateOf(GuidanceLanguage.TURKISH)
        var callbacks = 0
        var continued = false
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued = true },
                        offlineDataState = NationwideDataState.Idle,
                        onDownloadOfflineData = {},
                        guidanceLanguage = selected,
                        onGuidanceLanguageChange = { language ->
                            callbacks++
                            selected = language
                        },
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-audio-setup").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-audio-language-de-DE")
            .performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(GuidanceLanguage.GERMAN, selected)
            assertEquals(1, callbacks)
        }
        rule.onNodeWithTag("onboarding-audio-language-de-DE").assertIsNotEnabled()
        rule.onNodeWithTag("onboarding-complete").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(continued) }
    }

    @Test fun announcementLanguageAndBluetoothAreOptionalUntilExplicitChoice() {
        var callbacks = 0
        var continued = false
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued = true },
                        offlineDataState = NationwideDataState.Idle,
                        onDownloadOfflineData = {},
                        guidanceLanguage = GuidanceLanguage.ENGLISH,
                        onGuidanceLanguageChange = { callbacks++ },
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-bluetooth-settings")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-complete").performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(continued)
            assertEquals(0, callbacks)
        }
        val intent = bluetoothQuickSetupIntent()
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, intent.action)
        assertEquals(null, intent.component)
    }

    @Test fun replayDismissalPreservesCompletedSetupAndNeverStartsOfflineDownload() {
        val replayOpen = mutableStateOf(true)
        var continueCalls = 0
        var downloads = 0
        rule.setContent {
            MaterialTheme {
                if (replayOpen.value) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        FirstRunOnboarding(
                            onContinue = { continueCalls++ },
                            onDismissReplay = { replayOpen.value = false },
                            offlineDataState = NationwideDataState.Idle,
                            onDownloadOfflineData = { downloads++ },
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("onboarding-dismiss-replay").performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(!replayOpen.value)
            assertEquals(0, continueCalls)
            assertEquals(0, downloads)
        }
        rule.onNodeWithTag("first-run-onboarding").assertDoesNotExist()
    }

    @Test fun replayContinueIsOptionalAndDoesNotTriggerDownloads() {
        var dismissed = false
        var continued = false
        var downloads = 0
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued = true },
                        onDismissReplay = { dismissed = true },
                        offlineDataState = NationwideDataState.Idle,
                        onDownloadOfflineData = { downloads++ },
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-complete").performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(continued)
            assertTrue(!dismissed)
            assertEquals(0, downloads)
        }
    }

    @Test fun appPermissionQuickSetupOpensOnlyOwnAppDetails() {
        val intent = onboardingAppPermissionSettingsIntent("com.nazofobi.arrivalalarm")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:com.nazofobi.arrivalalarm", intent.data?.toString())
        assertEquals(null, intent.component)
    }

    @Test fun permissionReviewIsVisibleButOptionalOnFirstRun() {
        var continued = false
        var downloads = 0
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued = true },
                        offlineDataState = NationwideDataState.Idle,
                        onDownloadOfflineData = { downloads++ },
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-app-permissions")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-complete")
            .performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(continued)
            assertEquals(0, downloads)
        }
    }

    @Test fun alarmPermissionOptInIsExplicitAndDoesNotStartJourney() {
        var permissionRequests = 0
        var continued = false
        var grants by mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued = true },
                        offlineDataState = NationwideDataState.Idle,
                        onDownloadOfflineData = {},
                        journeyPermissionsGranted = grants,
                        onRequestJourneyPermissions = { permissionRequests++ },
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-alarm-permission-status")
            .performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(0, permissionRequests)
            assertTrue(!continued)
        }
        rule.onNodeWithTag("onboarding-request-alarm-permissions")
            .performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(1, permissionRequests)
            assertTrue(!continued)
            grants = true
        }
        rule.onNodeWithTag("onboarding-alarm-permission-status").assertIsDisplayed()
        rule.onNodeWithTag("onboarding-request-alarm-permissions").assertDoesNotExist()
    }

    @Test fun alarmPermissionOptInCanBeSkippedWithoutAnyRequest() {
        var permissionRequests = 0
        var continued = false
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(
                        onContinue = { continued = true },
                        offlineDataState = NationwideDataState.Idle,
                        onDownloadOfflineData = {},
                        onRequestJourneyPermissions = { permissionRequests++ },
                    )
                }
            }
        }
        rule.onNodeWithTag("onboarding-complete").performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(continued)
            assertEquals(0, permissionRequests)
        }
    }

    @Test fun firstRunAlarmRequestNeverIncludesBackgroundLocation() {
        val permissions = ActiveJourneyPermissions.runtimePermissions().toSet()
        assertTrue(android.Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
        assertTrue(android.Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertTrue(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION !in permissions)
    }

    @Test fun quickSetupUsesOnlySystemAccessibilitySettings() {
        val intent = accessibilityQuickSetupIntent()
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        assertEquals(null, intent.component)
    }
}
