package com.nazofobi.arrivalalarm

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsTripSearchConsentPanelInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun serviceDateMustBeChosenBeforeTripInspectionOrConfirmation() {
        val template = GtfsTripSearchIdentityTemplate(
            tripId = "trip-1",
            routeId = "route-1",
            serviceId = "service-1",
            directionId = 0,
            displayLabel = "RE 9 → Achim",
            sourceVersion = "feed-v1",
        )
        var chooseCalls = 0

        compose.setContent {
            GtfsTripSearchConsentPanel(
                template = template,
                selection = null,
                inspection = null,
                confirmed = null,
                busy = false,
                statusMessage = null,
                completionRevision = 0L,
                onChooseServiceDate = { chooseCalls++ },
                onChangeServiceDate = {},
                onConfirm = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithTag("gtfs-trip-search-choose-date")
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle { assertEquals(1, chooseCalls) }
        compose.onNodeWithTag("gtfs-trip-search-confirm").assertDoesNotExist()
    }
}
