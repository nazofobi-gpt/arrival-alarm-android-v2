package com.nazofobi.arrivalalarm

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsTripConsentPanelInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val identity = GtfsTripInstanceIdentity(
        tripId = "trip-1",
        serviceDate = GtfsServiceDate(2026, 10, 20),
        serviceId = "service-1",
        routeId = "route-1",
        directionId = 0,
    )

    private fun stop(sequence: Int, dropOff: Int? = null) = GtfsTripDetailStop(
        stopSequence = sequence,
        stopId = "stop-$sequence",
        name = "Stop $sequence",
        parentStationId = null,
        platformCode = null,
        wheelchairBoarding = null,
        scheduledArrival = "08:00:00",
        scheduledDeparture = "08:01:00",
        latitude = 52.66,
        longitude = 8.24,
        dropOffType = dropOff,
    )

    private val detail = GtfsTripDetail(
        identity,
        "Achim",
        listOf(stop(10), stop(20, dropOff = 1), stop(30)),
    )

    @Test fun previewIsNotConsentAndConfirmationDoesNotArmAnything() {
        var saved: GtfsSelectedTrip? = null
        var confirmations = 0
        compose.setContent {
            GtfsTripConsentPanel(
                preview = GtfsDepartureInspection.Ready(detail, detail.stops.first()),
                busy = false,
                onConfirm = {
                    confirmations++
                    saved = it
                },
                onDismiss = {},
            )
        }

        compose.onNodeWithTag("gtfs-consent-confirm").assertIsNotEnabled()
        assertEquals(0, confirmations)
        assertNull(saved)
        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-30").performClick()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsEnabled().performClick()
        assertEquals(1, confirmations)
        assertEquals(identity, saved?.identity)
        assertEquals(GtfsSelectedStopRef(10, "stop-10"), saved?.boarding)
        assertEquals(GtfsSelectedStopRef(30, "stop-30"), saved?.destination)
        assertEquals(saved?.destination, saved?.alarmTarget)
    }

    @Test fun explicitIntermediateAlarmAndChangingDestinationRequireFreshChoice() {
        var chosen: GtfsSelectedTrip? = null
        compose.setContent {
            GtfsTripConsentPanel(
                preview = GtfsDepartureInspection.Ready(detail, detail.stops.first()),
                busy = false,
                onConfirm = { chosen = it },
                onDismiss = {},
            )
        }
        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-30").performClick()
        compose.onNodeWithTag("gtfs-alarm-target-picker").performClick()
        // Reaching the boarding stop is not a meaningful arrival notification.
        compose.onNodeWithTag("gtfs-alarm-10").assertDoesNotExist()
        compose.onNodeWithTag("gtfs-alarm-20").performClick()
        compose.onNodeWithTag("gtfs-consent-confirm").performClick()
        assertEquals(GtfsSelectedStopRef(20, "stop-20"), chosen?.alarmTarget)
        assertEquals(GtfsSelectedStopRef(30, "stop-30"), chosen?.destination)
    }
}
