package com.nazofobi.arrivalalarm

import com.google.transit.realtime.GtfsRealtime
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanyGtfsRealtimeClientTest {
    @Test
    fun parsesTripUpdatesAlertsAndProvenance() {
        val trip = GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("trip-1")
            .setRouteId("route-1")
            .setStartDate("20261002")
            .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED)
            .build()
        val stop = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopSequence(7)
            .setStopId("stop-7")
            .setArrival(
                GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                    .setDelay(120)
                    .setTime(1_800_000_000L),
            )
            .setDeparture(
                GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                    .setDelay(180)
                    .setTime(1_800_000_060L),
            )
            .build()
        val tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
            .setTrip(trip)
            .setTimestamp(1_800_000_005L)
            .addStopTimeUpdate(stop)
            .build()

        val translatedHeader = GtfsRealtime.TranslatedString.newBuilder()
            .addTranslation(
                GtfsRealtime.TranslatedString.Translation.newBuilder()
                    .setLanguage("de")
                    .setText("Störung"),
            )
            .addTranslation(
                GtfsRealtime.TranslatedString.Translation.newBuilder()
                    .setLanguage("en")
                    .setText("Disruption"),
            )
            .build()
        val selector = GtfsRealtime.EntitySelector.newBuilder()
            .setRouteId("route-1")
            .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip-1"))
            .setStopId("stop-7")
            .build()
        val alert = GtfsRealtime.Alert.newBuilder()
            .setHeaderText(translatedHeader)
            .addInformedEntity(selector)
            .addActivePeriod(
                GtfsRealtime.TimeRange.newBuilder()
                    .setStart(1_799_999_000L)
                    .setEnd(1_800_001_000L),
            )
            .build()

        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
                    .setIncrementality(GtfsRealtime.FeedHeader.Incrementality.FULL_DATASET)
                    .setFeedVersion("feed-20261004")
                    .setTimestamp(1_800_000_000L),
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("trip-update-1")
                    .setTripUpdate(tripUpdate),
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("alert-1")
                    .setAlert(alert),
            )
            .build()

        val client = GermanyGtfsRealtimeClient(
            clock = EpochClock { 1_800_000_010L },
            loader = { feed.toByteArray() },
        )

        val result = client.fetch()
        assertTrue(result is GermanyRealtimeFetchResult.Available)
        val snapshot = (result as GermanyRealtimeFetchResult.Available).snapshot
        assertEquals(GermanyGtfsRealtimeProvenance.PROVIDER, snapshot.source)
        assertEquals(GermanyGtfsRealtimeProvenance.LICENSE, snapshot.license)
        assertEquals(1_800_000_000L, snapshot.feedTimestampEpochSeconds)
        assertEquals(1_800_000_010L, snapshot.fetchedAtEpochSeconds)
        assertEquals("2.0", snapshot.gtfsRealtimeVersion)
        assertEquals("FULL_DATASET", snapshot.incrementality)
        assertEquals("feed-20261004", snapshot.feedVersion)

        val update = snapshot.tripUpdates.single()
        assertEquals("trip-1", update.tripId)
        assertEquals("route-1", update.routeId)
        assertEquals("20261002", update.startDate)
        assertEquals(1_800_000_005L, update.updateTimestampEpochSeconds)
        assertTrue(update.cancelled)
        assertEquals(120, update.stops.single().arrivalDelaySeconds)
        assertEquals(180, update.stops.single().departureDelaySeconds)

        val parsedAlert = snapshot.serviceAlerts.single()
        assertEquals("Störung", parsedAlert.header)
        assertEquals(setOf("route-1"), parsedAlert.routeIds)
        assertEquals(setOf("trip-1"), parsedAlert.tripIds)
        assertEquals(setOf("stop-7"), parsedAlert.stopIds)
        assertEquals(1_799_999_000L, parsedAlert.activePeriods.single().startEpochSeconds)
    }

    @Test
    fun retainsStopRelationshipsPropertiesAndInvalidAssignments() {
        val properties = GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder()
            .setAssignedStopId("platform-2")
            .setStopHeadsign("City Center")
            .setPickupType(
                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.NONE,
            )
            .setDropOffType(
                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.PHONE_AGENCY,
            )
            .build()
        val scheduled = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopSequence(1)
            .setStopId("platform-2")
            .setScheduleRelationship(
                GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED,
            )
            .setStopTimeProperties(properties)
            .setArrival(
                GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                    .setDelay(30)
                    .setTime(1_800_000_030L),
            )
            .build()
        val skipped = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopSequence(2)
            .setStopId("stop-2")
            .setScheduleRelationship(
                GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED,
            )
            .build()
        val noData = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopSequence(3)
            .setStopId("stop-3")
            .setScheduleRelationship(
                GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA,
            )
            .setArrival(
                GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                    .setDelay(999)
                    .setTime(1_900_000_000L),
            )
            .build()
        val unscheduled = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopSequence(4)
            .setStopId("stop-4")
            .setScheduleRelationship(
                GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.UNSCHEDULED,
            )
            .build()
        val missingSequence = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopTimeProperties(
                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder()
                    .setAssignedStopId("platform-x"),
            )
            .build()
        val conflictingIds = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopSequence(4)
            .setStopId("platform-old")
            .setStopTimeProperties(
                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder()
                    .setAssignedStopId("platform-new"),
            )
            .build()
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0"),
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("stop-properties")
                    .setTripUpdate(
                        GtfsRealtime.TripUpdate.newBuilder()
                            .setTrip(
                                GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip-1"),
                            )
                            .addStopTimeUpdate(scheduled)
                            .addStopTimeUpdate(skipped)
                            .addStopTimeUpdate(noData)
                            .addStopTimeUpdate(unscheduled)
                            .addStopTimeUpdate(missingSequence)
                            .addStopTimeUpdate(conflictingIds),
                    ),
            )
            .build()

        val result = GermanyGtfsRealtimeClient(
            loader = { feed.toByteArray() },
        ).fetch()
        val stops = (result as GermanyRealtimeFetchResult.Available)
            .snapshot.tripUpdates.single().stops

        assertEquals("SCHEDULED", stops[0].scheduleRelationship)
        assertEquals("platform-2", stops[0].assignedStopId)
        assertEquals("NONE", stops[0].pickupType)
        assertEquals("PHONE_AGENCY", stops[0].dropOffType)
        assertEquals("City Center", stops[0].stopHeadsign)
        assertEquals(GermanyRealtimeStopUpdateValidity.VALID, stops[0].validity)
        assertEquals(30, stops[0].arrivalDelaySeconds)

        assertEquals("SKIPPED", stops[1].scheduleRelationship)
        assertNull(stops[1].arrivalTimeEpochSeconds)
        assertNull(stops[1].departureTimeEpochSeconds)

        assertEquals("NO_DATA", stops[2].scheduleRelationship)
        assertNull(stops[2].arrivalDelaySeconds)
        assertNull(stops[2].arrivalTimeEpochSeconds)

        assertEquals("UNSCHEDULED", stops[3].scheduleRelationship)
        assertEquals(GermanyRealtimeStopUpdateValidity.VALID, stops[3].validity)

        assertEquals(
            GermanyRealtimeStopUpdateValidity.ASSIGNED_STOP_REQUIRES_SEQUENCE,
            stops[4].validity,
        )
        assertEquals("platform-x", stops[4].assignedStopId)
        assertEquals(
            GermanyRealtimeStopUpdateValidity.ASSIGNED_STOP_ID_MISMATCH,
            stops[5].validity,
        )
        assertEquals("platform-old", stops[5].stopId)
        assertEquals("platform-new", stops[5].assignedStopId)
    }

    @Test
    fun retainsTripSelectorIdentityAndFailsClosedForIncompleteSelectors() {
        fun tripEntity(
            entityId: String,
            descriptor: GtfsRealtime.TripDescriptor,
        ): GtfsRealtime.FeedEntity =
            GtfsRealtime.FeedEntity.newBuilder()
                .setId(entityId)
                .setTripUpdate(
                    GtfsRealtime.TripUpdate.newBuilder()
                        .setTrip(descriptor),
                )
                .build()

        val idBased = GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("frequency-template")
            .setRouteId("route-frequency")
            .setDirectionId(1)
            .setStartTime("25:15:00")
            .setStartDate("20261004")
            .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
            .build()
        val idlessComplete = GtfsRealtime.TripDescriptor.newBuilder()
            .setRouteId("route-idless")
            .setDirectionId(0)
            .setStartTime("08:10:00")
            .setStartDate("20261004")
            .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
            .build()
        val incomplete = GtfsRealtime.TripDescriptor.newBuilder()
            .setRouteId("route-incomplete")
            .setStartTime("09:00:00")
            .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
            .build()
        val cancelled = GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("trip-cancelled")
            .setRouteId("route-cancelled")
            .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED)
            .build()

        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0"),
            )
            .addEntity(tripEntity("id-based", idBased))
            .addEntity(tripEntity("idless-complete", idlessComplete))
            .addEntity(tripEntity("incomplete", incomplete))
            .addEntity(tripEntity("cancelled", cancelled))
            .build()

        val result = GermanyGtfsRealtimeClient(
            loader = { feed.toByteArray() },
        ).fetch()
        val updates = (result as GermanyRealtimeFetchResult.Available)
            .snapshot.tripUpdates.associateBy { it.entityId }

        val parsedIdBased = updates.getValue("id-based")
        assertEquals("frequency-template", parsedIdBased.tripId)
        assertEquals("route-frequency", parsedIdBased.routeId)
        assertEquals(1, parsedIdBased.directionId)
        assertEquals("25:15:00", parsedIdBased.startTime)
        assertEquals("20261004", parsedIdBased.startDate)
        assertEquals(
            GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
            parsedIdBased.selectorValidity,
        )
        assertTrue(parsedIdBased.hasFrequencyInstanceIdentity)

        val parsedIdless = updates.getValue("idless-complete")
        assertNull(parsedIdless.tripId)
        assertEquals("route-idless", parsedIdless.routeId)
        assertEquals(0, parsedIdless.directionId)
        assertEquals("08:10:00", parsedIdless.startTime)
        assertEquals("20261004", parsedIdless.startDate)
        assertEquals(
            GermanyRealtimeTripSelectorValidity.VALID_IDLESS_SCHEDULED,
            parsedIdless.selectorValidity,
        )

        val parsedIncomplete = updates.getValue("incomplete")
        assertNull(parsedIncomplete.tripId)
        assertEquals(
            GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE,
            parsedIncomplete.selectorValidity,
        )

        val parsedCancelled = updates.getValue("cancelled")
        assertEquals("CANCELED", parsedCancelled.scheduleRelationship)
        assertTrue(parsedCancelled.cancelled)
        assertEquals(
            GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
            parsedCancelled.selectorValidity,
        )
    }

    @Test
    fun alertSelectorsPreserveConjunctionAlternativesAndSeverity() {
        val tripInstance = GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("frequency-template")
            .setRouteId("trip-route")
            .setDirectionId(1)
            .setStartTime("25:10:00")
            .setStartDate("20261004")
            .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
            .build()
        val incompleteTrip = GtfsRealtime.TripDescriptor.newBuilder()
            .setRouteId("incomplete-trip-route")
            .setStartTime("09:00:00")
            .build()

        val alert = GtfsRealtime.Alert.newBuilder()
            .setSeverityLevel(GtfsRealtime.Alert.SeverityLevel.WARNING)
            .addInformedEntity(
                GtfsRealtime.EntitySelector.newBuilder()
                    .setAgencyId("agency-1"),
            )
            .addInformedEntity(
                GtfsRealtime.EntitySelector.newBuilder()
                    .setRouteType(3),
            )
            .addInformedEntity(
                GtfsRealtime.EntitySelector.newBuilder()
                    .setRouteId("route-direction")
                    .setDirectionId(1),
            )
            .addInformedEntity(
                GtfsRealtime.EntitySelector.newBuilder()
                    .setRouteId("route-stop")
                    .setStopId("stop-2"),
            )
            .addInformedEntity(
                GtfsRealtime.EntitySelector.newBuilder()
                    .setTrip(tripInstance),
            )
            .addInformedEntity(
                GtfsRealtime.EntitySelector.newBuilder()
                    .setStopId("stop-only"),
            )
            .addInformedEntity(GtfsRealtime.EntitySelector.newBuilder())
            .addInformedEntity(
                GtfsRealtime.EntitySelector.newBuilder()
                    .setTrip(incompleteTrip),
            )
            .build()

        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0"),
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("alert-selectors")
                    .setAlert(alert),
            )
            .build()

        val result = GermanyGtfsRealtimeClient(
            loader = { feed.toByteArray() },
        ).fetch()
        val parsed = (result as GermanyRealtimeFetchResult.Available)
            .snapshot.serviceAlerts.single()

        assertEquals("WARNING", parsed.severityLevel)
        assertEquals(8, parsed.selectors.size)

        val agency = parsed.selectors[0]
        assertEquals("agency-1", agency.agencyId)
        assertEquals(GermanyRealtimeAlertSelectorValidity.VALID, agency.validity)

        val routeType = parsed.selectors[1]
        assertEquals(3, routeType.routeType)
        assertEquals(GermanyRealtimeAlertSelectorValidity.VALID, routeType.validity)

        val routeDirection = parsed.selectors[2]
        assertEquals("route-direction", routeDirection.routeId)
        assertEquals(1, routeDirection.directionId)
        assertNull(routeDirection.stopId)

        val routeStop = parsed.selectors[3]
        assertEquals("route-stop", routeStop.routeId)
        assertEquals("stop-2", routeStop.stopId)
        assertNull(routeStop.directionId)

        val trip = parsed.selectors[4].trip!!
        assertEquals("frequency-template", trip.tripId)
        assertEquals("trip-route", trip.routeId)
        assertEquals(1, trip.directionId)
        assertEquals("25:10:00", trip.startTime)
        assertEquals("20261004", trip.startDate)
        assertTrue(trip.hasFrequencyInstanceIdentity)
        assertEquals(
            GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
            trip.validity,
        )

        assertEquals("stop-only", parsed.selectors[5].stopId)
        assertEquals(
            GermanyRealtimeAlertSelectorValidity.UNMATCHABLE_EMPTY,
            parsed.selectors[6].validity,
        )
        assertEquals(
            GermanyRealtimeAlertSelectorValidity.UNMATCHABLE_TRIP_SELECTOR,
            parsed.selectors[7].validity,
        )

        assertEquals(setOf("route-direction", "route-stop"), parsed.routeIds)
        assertEquals(setOf("frequency-template"), parsed.tripIds)
        assertEquals(setOf("stop-2", "stop-only"), parsed.stopIds)
    }

    @Test
    fun differentialFeedFailsClosedInsteadOfMasqueradingAsFullSnapshot() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
                    .setIncrementality(GtfsRealtime.FeedHeader.Incrementality.DIFFERENTIAL)
                    .setFeedVersion("delta-feed"),
            )
            .build()

        val result = GermanyGtfsRealtimeClient(
            loader = { feed.toByteArray() },
        ).fetch()

        assertTrue(result is GermanyRealtimeFetchResult.Unavailable)
        assertEquals(
            GermanyRealtimeUnavailableReason.UNSUPPORTED_INCREMENTALITY,
            (result as GermanyRealtimeFetchResult.Unavailable).reason,
        )
        assertTrue(result.detail?.contains("DIFFERENTIAL") == true)
    }

    @Test
    fun malformedPayloadReturnsTypedParseFailure() {
        val client = GermanyGtfsRealtimeClient(
            loader = { byteArrayOf(0x08) },
        )

        val result = client.fetch()
        assertTrue(result is GermanyRealtimeFetchResult.Unavailable)
        assertEquals(
            GermanyRealtimeUnavailableReason.PARSE,
            (result as GermanyRealtimeFetchResult.Unavailable).reason,
        )
    }

    @Test
    fun oversizedPayloadReturnsTypedUnavailable() {
        val client = GermanyGtfsRealtimeClient(
            maxBytes = 2,
            loader = { byteArrayOf(1, 2, 3) },
        )

        val result = client.fetch()
        assertTrue(result is GermanyRealtimeFetchResult.Unavailable)
        assertEquals(
            GermanyRealtimeUnavailableReason.TOO_LARGE,
            (result as GermanyRealtimeFetchResult.Unavailable).reason,
        )
    }

    @Test
    fun loaderFailureReturnsTypedNetworkUnavailable() {
        val client = GermanyGtfsRealtimeClient(
            loader = { throw IOException("offline") },
        )

        val result = client.fetch()
        assertTrue(result is GermanyRealtimeFetchResult.Unavailable)
        assertEquals(
            GermanyRealtimeUnavailableReason.NETWORK,
            (result as GermanyRealtimeFetchResult.Unavailable).reason,
        )
    }
}
