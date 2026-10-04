package com.nazofobi.arrivalalarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.transit.realtime.GtfsRealtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GermanyGtfsRealtimeClientInstrumentedTest {
    @Test
    fun protobufTripUpdateAndAlertDecodeOnAndroidRuntime() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
                    .setIncrementality(GtfsRealtime.FeedHeader.Incrementality.FULL_DATASET)
                    .setFeedVersion("android-feed")
                    .setTimestamp(1_800_000_000L),
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("trip-update")
                    .setTripUpdate(
                        GtfsRealtime.TripUpdate.newBuilder()
                            .setTimestamp(1_800_000_003L)
                            .setTrip(
                                GtfsRealtime.TripDescriptor.newBuilder()
                                    .setTripId("trip-1")
                                    .setRouteId("route-1")
                                    .setDirectionId(1)
                                    .setStartTime("12:34:00")
                                    .setStartDate("20261004"),
                            )
                            .addStopTimeUpdate(
                                GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                                    .setStopId("platform-2")
                                    .setStopSequence(1)
                                    .setScheduleRelationship(
                                        GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED,
                                    )
                                    .setStopTimeProperties(
                                        GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.newBuilder()
                                            .setAssignedStopId("platform-2")
                                            .setStopHeadsign("Zentrum")
                                            .setPickupType(
                                                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.REGULAR,
                                            )
                                            .setDropOffType(
                                                GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.NONE,
                                            ),
                                    )
                                    .setDeparture(
                                        GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                                            .setDelay(90),
                                    ),
                            ),
                    ),
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("alert")
                    .setAlert(
                        GtfsRealtime.Alert.newBuilder()
                            .setHeaderText(
                                GtfsRealtime.TranslatedString.newBuilder()
                                    .addTranslation(
                                        GtfsRealtime.TranslatedString.Translation.newBuilder()
                                            .setLanguage("de")
                                            .setText("Hinweis"),
                                    ),
                            )
                            .setSeverityLevel(GtfsRealtime.Alert.SeverityLevel.WARNING)
                            .addInformedEntity(
                                GtfsRealtime.EntitySelector.newBuilder()
                                    .setAgencyId("agency-1")
                                    .setRouteId("route-1")
                                    .setDirectionId(1)
                                    .setStopId("platform-2")
                                    .setTrip(
                                        GtfsRealtime.TripDescriptor.newBuilder()
                                            .setTripId("trip-1")
                                            .setRouteId("route-1")
                                            .setDirectionId(1)
                                            .setStartTime("12:34:00")
                                            .setStartDate("20261004"),
                                    ),
                            ),
                    ),
            )
            .build()

        val client = GermanyGtfsRealtimeClient(
            clock = EpochClock { 1_800_000_005L },
            loader = { feed.toByteArray() },
        )
        val result = client.fetch()

        assertTrue(result is GermanyRealtimeFetchResult.Available)
        val snapshot = (result as GermanyRealtimeFetchResult.Available).snapshot
        assertEquals(1_800_000_000L, snapshot.feedTimestampEpochSeconds)
        assertEquals("2.0", snapshot.gtfsRealtimeVersion)
        assertEquals("FULL_DATASET", snapshot.incrementality)
        assertEquals("android-feed", snapshot.feedVersion)
        val tripUpdate = snapshot.tripUpdates.single()
        assertEquals(1_800_000_003L, tripUpdate.updateTimestampEpochSeconds)
        assertEquals(1, tripUpdate.directionId)
        assertEquals("12:34:00", tripUpdate.startTime)
        assertEquals("20261004", tripUpdate.startDate)
        assertEquals(
            GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
            tripUpdate.selectorValidity,
        )
        assertTrue(tripUpdate.hasFrequencyInstanceIdentity)
        val stop = tripUpdate.stops.single()
        assertEquals(90, stop.departureDelaySeconds)
        assertEquals("SCHEDULED", stop.scheduleRelationship)
        assertEquals("platform-2", stop.assignedStopId)
        assertEquals("REGULAR", stop.pickupType)
        assertEquals("NONE", stop.dropOffType)
        assertEquals("Zentrum", stop.stopHeadsign)
        assertEquals(GermanyRealtimeStopUpdateValidity.VALID, stop.validity)
        val alert = snapshot.serviceAlerts.single()
        assertEquals("Hinweis", alert.header)
        assertEquals("WARNING", alert.severityLevel)
        assertEquals(setOf("route-1"), alert.routeIds)
        assertEquals(setOf("trip-1"), alert.tripIds)
        assertEquals(setOf("platform-2"), alert.stopIds)
        val selector = alert.informedEntities.single()
        assertEquals("agency-1", selector.agencyId)
        assertEquals("route-1", selector.routeId)
        assertEquals(1, selector.directionId)
        assertEquals("platform-2", selector.stopId)
        assertEquals(GermanyRealtimeAlertSelectorValidity.VALID, selector.validity)
        assertEquals("trip-1", selector.trip?.tripId)
        assertEquals("12:34:00", selector.trip?.startTime)
    }

    @Test
    fun malformedProtobufFailsClosedOnAndroidRuntime() {
        val result = GermanyGtfsRealtimeClient(
            loader = { byteArrayOf(0x08) },
        ).fetch()

        assertTrue(result is GermanyRealtimeFetchResult.Unavailable)
        assertEquals(
            GermanyRealtimeUnavailableReason.PARSE,
            (result as GermanyRealtimeFetchResult.Unavailable).reason,
        )
    }
}
