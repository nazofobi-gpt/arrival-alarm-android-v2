package com.nazofobi.arrivalalarm

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteShareUiInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val option = RouteOption(
        id = "selected",
        origin = MapPoint(52.66, 8.23, "Lohne"),
        destination = MapPoint(53.08, 8.80, "Bremen"),
        line = "RE 9",
        direction = "Bremen",
        departure = "10:00",
        arrival = "11:00",
        walkingMinutes = 0,
        transfers = 0,
        stops = emptyList(),
    )

    private fun render(selectedId: String?, failShare: Boolean = false) {
        rule.setContent {
            val baseContext = LocalContext.current
            val shareContext = remember(baseContext, failShare) {
                if (failShare) object : ContextWrapper(baseContext) {
                    override fun startActivity(intent: Intent) {
                        throw ActivityNotFoundException("No share target")
                    }
                } else baseContext
            }
            CompositionLocalProvider(LocalContext provides shareContext) {
                MaterialTheme {
                RouteJourneyPanel(
                    origin = option.origin,
                    destination = option.destination,
                    routeOptions = listOf(option),
                    routeBusy = false,
                    routeStatus = null,
                    routeOffline = false,
                    selectedRouteId = selectedId,
                    journey = JourneyUiState(JourneyPhase.EMPTY, null, null, null),
                    onRefreshRoutes = {},
                    onSelectRoute = {},
                    onArmAlarm = {},
                    onCancelAlarm = {},
                    mode = RouteJourneyMode.DETAIL,
                )
                }
            }
        }
    }

    @Test fun shareIsAvailableOnlyForActualSelectedRoute() {
        render(option.id)
        rule.onNodeWithTag("journey-share-route").assertExists()
    }

    @Test fun shareButtonLaunchesAndroidChooser() {
        render(option.id)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Intercept the OS chooser rather than presenting a system dialog in this UI test.
        val monitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_CHOOSER),
            null,
            true,
        )
        try {
            rule.onNodeWithTag("journey-share-route").performClick()
            rule.waitForIdle()
            assertTrue(
                "Sharing the selected route must launch an explicit chooser",
                instrumentation.checkMonitorHit(monitor, 1),
            )
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun unavailableShareTargetShowsErrorWithoutCrashingOrLeavingJourney() {
        render(option.id, failShare = true)
        rule.onNodeWithTag("journey-share-route").performClick()
        rule.onNodeWithTag("journey-share-error").assertIsDisplayed()
        rule.onNodeWithTag("journey-detail").assertExists()
    }

    @Test fun noSelectionHasNoShareSideEffect() {
        render(null)
        rule.onNodeWithTag("journey-share-route").assertDoesNotExist()
    }
}
