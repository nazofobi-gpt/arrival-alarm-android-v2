package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsTripConsentDraftTest {
    private val identity = GtfsTripInstanceIdentity(
        tripId = "trip-7",
        serviceDate = GtfsServiceDate(2026, 10, 20),
        serviceId = "service-1",
        routeId = "route-2",
        directionId = 1,
    )
    private fun stop(sequence: Int, id: String) = GtfsTripDetailStop(
        stopSequence = sequence,
        stopId = id,
        name = id,
        parentStationId = null,
        platformCode = null,
        wheelchairBoarding = null,
        scheduledArrival = "08:00:00",
        scheduledDeparture = "08:01:00",
        latitude = 52.0,
        longitude = 8.0,
    )
    private val detail = GtfsTripDetail(
        identity,
        "Achim",
        listOf(stop(2, "Lohne"), stop(7, "Transfer"), stop(13, "Achim")),
    )

    @Test fun explicitOriginAndDestinationDefaultAlarmToDestination() {
        val result = buildGtfsTripConsentDraft(detail, 2, 13)
        assertTrue(result is GtfsTripConsentDraft.Ready)
        val selection = (result as GtfsTripConsentDraft.Ready).selection
        assertEquals(identity, selection.identity)
        assertEquals(GtfsSelectedStopRef(2, "Lohne"), selection.boarding)
        assertEquals(GtfsSelectedStopRef(13, "Achim"), selection.destination)
        assertEquals(selection.destination, selection.alarmTarget)
    }

    @Test fun explicitIntermediateAlarmTargetDoesNotChangeTripOrStops() {
        val result = buildGtfsTripConsentDraft(detail, 2, 13, 7)
        val selection = (result as GtfsTripConsentDraft.Ready).selection
        assertEquals(identity, selection.identity)
        assertEquals(GtfsSelectedStopRef(7, "Transfer"), selection.alarmTarget)
        assertEquals(GtfsSelectedStopRef(13, "Achim"), selection.destination)
    }

    @Test fun reversedOrOutOfSegmentRolesAreRejected() {
        for ((from, to, alarm) in listOf(
            Triple(13, 2, null),
            Triple(2, 13, 1),
            Triple(2, 13, 99),
            Triple(2, 2, null),
        )) {
            val result = buildGtfsTripConsentDraft(detail, from, to, alarm)
            assertEquals(
                GtfsTripConsentFailure.INVALID_STOP_ROLES,
                (result as GtfsTripConsentDraft.Rejected).reason,
            )
        }
    }

    @Test fun providerPickupRestrictionsAndInvalidCoordinatesFailClosed() {
        val noPickup = detail.copy(stops = listOf(
            stop(2, "Lohne").copy(pickupType = 1),
            stop(7, "Transfer"),
            stop(13, "Achim"),
        ))
        val restricted = buildGtfsTripConsentDraft(noPickup, 2, 13)
        assertEquals(
            TripStopRoleProblem.BOARDING_NOT_ALLOWED,
            (restricted as GtfsTripConsentDraft.Rejected).roleProblem,
        )
        val invalidGps = detail.copy(stops = detail.stops.mapIndexed { i, s ->
            if (i == 1) s.copy(latitude = Double.NaN) else s
        })
        val invalid = buildGtfsTripConsentDraft(invalidGps, 2, 13)
        assertEquals(
            TripStopRoleProblem.INVALID_STOP_SEQUENCE,
            (invalid as GtfsTripConsentDraft.Rejected).roleProblem,
        )
    }

    @Test fun frequencyTemplateCannotBeApprovedAsSpecificVehicle() {
        val frequency = detail.copy(
            identity = identity.copy(frequencyStartSeconds = 8 * 3600),
        )
        assertEquals(
            GtfsTripConsentFailure.FREQUENCY_INSTANCE_UNSUPPORTED,
            (buildGtfsTripConsentDraft(frequency, 2, 13) as
                GtfsTripConsentDraft.Rejected).reason,
        )
    }

    @Test fun repeatedLoopStopIdRetainsPhysicalSequenceIdentity() {
        val loop = detail.copy(stops = listOf(
            stop(2, "Lohne"), stop(7, "Lohne"), stop(13, "Achim"),
        ))
        val selection = (buildGtfsTripConsentDraft(loop, 7, 13) as
            GtfsTripConsentDraft.Ready).selection
        assertEquals(GtfsSelectedStopRef(7, "Lohne"), selection.boarding)
        assertEquals(GtfsSelectedStopRef(13, "Achim"), selection.alarmTarget)
    }
}
