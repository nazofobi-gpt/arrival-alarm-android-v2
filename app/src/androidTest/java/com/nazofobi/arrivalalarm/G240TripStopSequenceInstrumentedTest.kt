package com.nazofobi.arrivalalarm

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G240TripStopSequenceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val identity = GtfsTripInstanceIdentity(
        tripId = "g240-real-identity-fixture",
        serviceDate = GtfsServiceDate(2026, 11, 4),
        serviceId = "service-1",
        routeId = "route-1",
        directionId = 0,
    )
    private fun stop(seq: Int, arrival: String?, departure: String?, platform: String?) =
        GtfsTripDetailStop(
            stopSequence = seq, stopId = "stop-$seq", name = "Stop $seq",
            parentStationId = null, platformCode = platform, wheelchairBoarding = null,
            scheduledArrival = arrival, scheduledDeparture = departure,
            latitude = 52.66, longitude = 8.24,
        )
    private val detail = GtfsTripDetail(
        identity, "Bremen",
        listOf(
            stop(10, "08:00:00", "08:01:00", "1"),
            stop(20, null, null, null),
            stop(30, "08:30:00", "08:31:00", "3"),
        ),
    )

    @Test fun previewShowsEverySourceStopAndScheduledEvidenceWithoutConsent() {
        var saved = 0
        compose.setContent {
            GtfsTripConsentPanel(
                preview = GtfsDepartureInspection.Ready(detail, detail.stops.first()),
                busy = false,
                onConfirm = { saved++ },
                onDismiss = {},
            )
        }
        compose.onNodeWithTag("g240-trip-stop-row-20").assertDoesNotExist()
        compose.onNodeWithTag("g240-trip-stop-toggle").performClick()
        compose.onNodeWithTag("g240-trip-stop-row-10").assertExists()
        compose.onNodeWithTag("g240-trip-stop-row-20").assertExists()
        compose.onNodeWithTag("g240-trip-stop-row-30").assertExists()
        compose.onNodeWithTag("g240-trip-stop-schedule-10")
            .assertTextContains("08:00:00", substring = true)
        compose.onNodeWithTag("g240-trip-stop-schedule-30")
            .assertTextContains("08:30:00", substring = true)
        compose.onNodeWithTag("g240-trip-stop-schedule-20").assertDoesNotExist()
        compose.onNodeWithTag("g240-trip-stop-boarding").assertExists()
        compose.onNodeWithTag("g240-trip-stop-source").assertExists()
        assertEquals(0, saved)
    }

    @Test fun confirmedRestoredJourneyStillExposesExactStopSequenceReadOnly() {
        val selection = GtfsSelectedTrip(
            identity,
            GtfsSelectedStopRef(10, "stop-10"),
            GtfsSelectedStopRef(30, "stop-30"),
            GtfsSelectedStopRef(30, "stop-30"),
        )
        compose.setContent {
            GtfsConfirmedTripSummary(GtfsSelectionRestore.Verified(selection, detail))
        }
        compose.onNodeWithTag("g240-trip-stop-toggle").performClick()
        compose.onNodeWithTag("g240-trip-stop-name-20")
            .assertTextContains("Stop 20", substring = true)
        compose.onNodeWithTag("gtfs-consent-confirm").assertDoesNotExist()
        compose.onNodeWithTag("gtfs-departure-arm-confirmed-alarm").assertDoesNotExist()
    }

    @Test fun switchingFeedSnapshotCollapsesOldSequenceAndShowsFreshStops() {
        val snapshot = androidx.compose.runtime.mutableStateOf(detail)
        compose.setContent {
            GtfsTripStopSequencePanel(snapshot.value, snapshot.value.stops.first())
        }
        compose.onNodeWithTag("g240-trip-stop-toggle").performClick()
        compose.onNodeWithTag("g240-trip-stop-name-30").assertExists()
        compose.runOnUiThread {
            snapshot.value = detail.copy(
                stops = detail.stops.dropLast(1) +
                    detail.stops.last().copy(name = "Revised stop 30", platformCode = "4"),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("g240-trip-stop-name-30").assertDoesNotExist()
        compose.onNodeWithTag("g240-trip-stop-toggle").performClick()
        compose.onNodeWithTag("g240-trip-stop-name-30")
            .assertTextContains("Revised stop 30", substring = true)
    }
}
