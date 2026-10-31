package com.nazofobi.arrivalalarm

import androidx.compose.ui.test.onAllNodesWithTag
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
        assertEquals(0, compose.onAllNodesWithTag("gtfs-trip-search-confirm").fetchSemanticsNodes().size)
    }
    @Test
    fun orderedStopListShowsPlatformParentAndWheelchairMetadata() {
        val template = GtfsTripSearchIdentityTemplate(
            tripId = "trip-1",
            routeId = "route-1",
            serviceId = "service-1",
            directionId = 0,
            displayLabel = "RE 9 → Achim",
            sourceVersion = "feed-v1",
        )
        val selection = template.forServiceDate(GtfsServiceDate(2026, 10, 31))
        val detail = GtfsTripDetail(
            identity = selection.identity,
            headsign = "Achim",
            stops = listOf(
                GtfsTripDetailStop(
                    stopSequence = 1,
                    stopId = "stop-1",
                    name = "Bremen Hbf",
                    parentStationId = "station-1",
                    platformCode = "4",
                    wheelchairBoarding = 1,
                    scheduledArrival = "08:00:00",
                    scheduledDeparture = "08:00:00",
                    latitude = 53.083,
                    longitude = 8.813,
                    pickupType = 0,
                    dropOffType = 0,
                ),
                GtfsTripDetailStop(
                    stopSequence = 2,
                    stopId = "stop-2",
                    name = "Achim",
                    parentStationId = null,
                    platformCode = "2",
                    wheelchairBoarding = 0,
                    scheduledArrival = "08:20:00",
                    scheduledDeparture = "08:20:00",
                    latitude = 53.013,
                    longitude = 9.026,
                    pickupType = 0,
                    dropOffType = 0,
                ),
            ),
        )

        compose.setContent {
            GtfsTripSearchConsentPanel(
                template = template,
                selection = selection,
                inspection = GtfsTripDetailResult.Available(detail),
                confirmed = null,
                busy = false,
                statusMessage = null,
                completionRevision = 0L,
                onChooseServiceDate = {},
                onChangeServiceDate = {},
                onConfirm = {},
                onDismiss = {},
            )
        }

        assertEquals(1, compose.onAllNodesWithTag("gtfs-trip-search-stop-1-platform").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithTag("gtfs-trip-search-stop-1-parent").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithTag("gtfs-trip-search-stop-1-wheelchair").fetchSemanticsNodes().size)
    }

}
