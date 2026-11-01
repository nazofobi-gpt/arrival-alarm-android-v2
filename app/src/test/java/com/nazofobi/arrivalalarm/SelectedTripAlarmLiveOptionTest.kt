package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class SelectedTripAlarmLiveOptionTest {
    private val identity = GtfsTripInstanceIdentity(
        tripId = "trip-7", serviceDate = GtfsServiceDate(2026, 10, 20),
        serviceId = "service-1", routeId = "route-2", directionId = 1,
    )
    private fun stop(seq: Int, name: String, time: String) = GtfsTripDetailStop(
        stopSequence = seq, stopId = name, name = name,
        parentStationId = null, platformCode = null, wheelchairBoarding = null,
        scheduledArrival = time, scheduledDeparture = time,
        latitude = 52.0 + seq * 0.001, longitude = 8.0 + seq * 0.001,
    )
    private val detail = GtfsTripDetail(
        identity = identity, headsign = "Achim", stops = listOf(
            stop(2, "Lohne", "08:00:00"),
            stop(7, "Intermediate", "08:10:00"),
            stop(13, "Achim", "08:20:00"),
            stop(15, "Beyond", "08:30:00"),
        ),
    )
    private val selection = GtfsSelectedTrip(
        identity = identity,
        boarding = GtfsSelectedStopRef(2, "Lohne"),
        destination = GtfsSelectedStopRef(13, "Achim"),
        alarmTarget = GtfsSelectedStopRef(7, "Intermediate"),
    )

    @Test fun showsOnlyConfirmedAlarmSegmentWithStaticSchedule() {
        val option = verifiedSelectedTripLiveOption(
            GtfsSelectionRestore.Verified(selection, detail)
        )
        assertNotNull(option)
        assertEquals(listOf("Lohne", "Intermediate"), option!!.stops.map { it.name })
        assertEquals("Intermediate", option.destination.label)
        assertEquals(listOf("trip-7"), option.tripIds)
        assertEquals("2026-10-20T08:10:00+02:00", option.stops.last().plannedArrival)
        assertNull(option.realtime)
        assertNull(option.sourceUpdatedAtEpochSeconds)
        assertFalse(option.stops.any { it.name == "Beyond" })
    }

    @Test fun invalidTargetOrChangedTripCannotProduceVerifiedPreview() {
        val invalid = selection.copy(alarmTarget = GtfsSelectedStopRef(15, "Beyond"))
        assertNull(verifiedSelectedTripLiveOption(
            GtfsSelectionRestore.Verified(invalid, detail)
        ))
        assertNull(verifiedSelectedTripLiveOption(
            GtfsSelectionRestore.Verified(selection, detail.copy(identity = identity.copy(routeId = "changed")))
        ))
    }

    @Test fun activeAlarmDisplaysPreviewAndKeepsCancelControlsButCancelledOneDoesNot() {
        val preview = verifiedSelectedTripLiveOption(
            GtfsSelectionRestore.Verified(selection, detail)
        )!!
        assertEquals(preview, liveAlarmDisplayRoute(emptyList(), null, preview,
            JourneyUiState(phase = JourneyPhase.ARMED)))
        assertEquals(preview, liveAlarmDisplayRoute(emptyList(), null, preview,
            JourneyUiState(phase = JourneyPhase.ARRIVED)))
        assertNull(liveAlarmDisplayRoute(emptyList(), null, preview,
            JourneyUiState(phase = JourneyPhase.DESTINATION_SELECTED)))
        assertNull(liveAlarmDisplayRoute(emptyList(), "unrelated", preview,
            JourneyUiState(phase = JourneyPhase.ARMED)))
        val manual = preview.copy(id = "manual")
        assertEquals(manual, liveAlarmDisplayRoute(listOf(manual), "manual", preview,
            JourneyUiState(phase = JourneyPhase.ARMED)))
    }
}
