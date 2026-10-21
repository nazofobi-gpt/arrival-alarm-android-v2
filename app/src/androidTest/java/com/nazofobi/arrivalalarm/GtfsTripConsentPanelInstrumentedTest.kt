package com.nazofobi.arrivalalarm

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
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

    @Test fun refreshedTripStopsRequireNewDestinationConsent() {
        var confirmed: GtfsSelectedTrip? = null
        val previewState = mutableStateOf(
            GtfsDepartureInspection.Ready(detail, detail.stops.first())
        )
        compose.setContent {
            GtfsTripConsentPanel(
                preview = previewState.value,
                busy = false,
                onConfirm = { confirmed = it },
                onDismiss = {},
            )
        }

        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-30").performClick()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsEnabled()

        // Same service-day/trip and stop sequence, but a different physical stop.
        // An old selection must not silently confirm the replacement destination.
        val changed = detail.copy(
            stops = detail.stops.dropLast(1) +
                detail.stops.last().copy(stopId = "replacement-30", name = "Replacement 30")
        )
        compose.runOnUiThread {
            previewState.value = GtfsDepartureInspection.Ready(changed, changed.stops.first())
        }
        compose.waitForIdle()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsNotEnabled()
        assertNull(confirmed)

        // Explicitly selecting the replacement creates a new, intentional choice.
        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-30").performClick()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsEnabled().performClick()
        assertEquals(GtfsSelectedStopRef(30, "replacement-30"), confirmed?.destination)
    }

    @Test fun sameStopIdMovedToDifferentCoordinatesRequiresFreshConsent() {
        var chosen: GtfsSelectedTrip? = null
        val previewState = mutableStateOf(
            GtfsDepartureInspection.Ready(detail, detail.stops.first())
        )
        compose.setContent {
            GtfsTripConsentPanel(
                preview = previewState.value,
                busy = false,
                onConfirm = { chosen = it },
                onDismiss = {},
            )
        }

        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-30").performClick()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsEnabled()

        // GTFS feed revisions can retain stop_id while changing physical location.
        // The previously chosen destination must not carry over implicitly.
        val moved = detail.copy(
            stops = detail.stops.dropLast(1) +
                detail.stops.last().copy(latitude = 52.70, longitude = 8.30)
        )
        compose.runOnUiThread {
            previewState.value = GtfsDepartureInspection.Ready(moved, moved.stops.first())
        }
        compose.waitForIdle()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsNotEnabled()
        assertNull(chosen)

        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-30").performClick()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsEnabled().performClick()
        assertEquals(GtfsSelectedStopRef(30, "stop-30"), chosen?.destination)
    }

    @Test fun terminalBoardingStopCannotBecomeItsOwnDestinationOrAlarm() {
        var confirmations = 0
        compose.setContent {
            GtfsTripConsentPanel(
                preview = GtfsDepartureInspection.Ready(detail, detail.stops.last()),
                busy = false,
                onConfirm = { confirmations++ },
                onDismiss = {},
            )
        }

        compose.onNodeWithTag("gtfs-consent-invalid").assertExists()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("gtfs-alarm-target-picker").assertDoesNotExist()
        assertEquals(0, confirmations)
    }

    @Test fun openDestinationChoicesAreDisabledWhenConsentBecomesBusy() {
        val busy = mutableStateOf(false)
        compose.setContent {
            GtfsTripConsentPanel(
                preview = GtfsDepartureInspection.Ready(detail, detail.stops.first()),
                busy = busy.value,
                onConfirm = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-30").assertIsEnabled()
        compose.runOnUiThread { busy.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("gtfs-destination-30").assertIsNotEnabled()
    }

    @Test fun openAlarmChoicesAreDisabledWhenConsentBecomesBusy() {
        val busy = mutableStateOf(false)
        compose.setContent {
            GtfsTripConsentPanel(
                preview = GtfsDepartureInspection.Ready(detail, detail.stops.first()),
                busy = busy.value,
                onConfirm = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-30").performClick()
        compose.onNodeWithTag("gtfs-alarm-target-picker").performClick()
        compose.onNodeWithTag("gtfs-alarm-20").assertIsEnabled()
        compose.runOnUiThread { busy.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("gtfs-alarm-20").assertIsNotEnabled()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsNotEnabled()
    }

    @Test fun confirmedTripSummaryDisplaysBoardingDestinationAndExplicitAlarmTarget() {
        val selected = GtfsSelectedTrip(
            identity,
            GtfsSelectedStopRef(10, "stop-10"),
            GtfsSelectedStopRef(30, "stop-30"),
            GtfsSelectedStopRef(20, "stop-20"),
        )
        compose.setContent {
            GtfsConfirmedTripSummary(GtfsSelectionRestore.Verified(selected, detail))
        }
        compose.onNodeWithTag("gtfs-confirmed-trip-summary").assertExists()
        compose.onNodeWithTag("gtfs-confirmed-boarding")
            .assertTextContains("Stop 10", substring = true)
        compose.onNodeWithTag("gtfs-confirmed-destination")
            .assertTextContains("Stop 30", substring = true)
        compose.onNodeWithTag("gtfs-confirmed-alarm-target")
            .assertTextContains("Stop 20", substring = true)
        // This is a read-only summary; no alarm-start control is added.
        compose.onNodeWithTag("gtfs-consent-confirm").assertDoesNotExist()
    }

    @Test fun confirmedTripSummaryFailsClosedIfConfirmedStopIdentityChanges() {
        val selected = GtfsSelectedTrip(
            identity,
            GtfsSelectedStopRef(10, "stop-10"),
            GtfsSelectedStopRef(30, "stop-30"),
            GtfsSelectedStopRef(20, "stop-20"),
        )
        val changed = detail.copy(
            stops = detail.stops.map {
                if (it.stopSequence == 30) it.copy(stopId = "replacement-30") else it
            },
        )
        compose.setContent {
            GtfsConfirmedTripSummary(GtfsSelectionRestore.Verified(selected, changed))
        }
        compose.onNodeWithTag("gtfs-confirmed-trip-summary").assertDoesNotExist()
    }

    @Test fun untimedStopCannotBeSelectedAsDestinationOrAlarmTarget() {
        var confirmed: GtfsSelectedTrip? = null
        val mixed = detail.copy(stops = listOf(
            stop(10),
            stop(20).copy(scheduledArrival = null, scheduledDeparture = null),
            stop(30),
        ))
        compose.setContent {
            GtfsTripConsentPanel(
                preview = GtfsDepartureInspection.Ready(mixed, mixed.stops.first()),
                busy = false,
                onConfirm = { confirmed = it },
                onDismiss = {},
            )
        }

        compose.onNodeWithTag("gtfs-destination-picker").performClick()
        compose.onNodeWithTag("gtfs-destination-20").assertDoesNotExist()
        compose.onNodeWithTag("gtfs-destination-30").performClick()
        compose.onNodeWithTag("gtfs-alarm-target-picker").performClick()
        compose.onNodeWithTag("gtfs-alarm-20").assertDoesNotExist()
        compose.onNodeWithTag("gtfs-alarm-30").performClick()
        compose.onNodeWithTag("gtfs-consent-confirm").assertIsEnabled().performClick()
        assertEquals(GtfsSelectedStopRef(30, "stop-30"), confirmed?.alarmTarget)
    }

}
