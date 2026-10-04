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
                    .setTimestamp(1_800_000_000L),
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("trip-update")
                    .setTripUpdate(
                        GtfsRealtime.TripUpdate.newBuilder()
                            .setTrip(
                                GtfsRealtime.TripDescriptor.newBuilder()
                                    .setTripId("trip-1")
                                    .setRouteId("route-1"),
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
                            .addInformedEntity(
                                GtfsRealtime.EntitySelector.newBuilder()
                                    .setRouteId("route-1"),
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
        val stop = snapshot.tripUpdates.single().stops.single()
        assertEquals(90, stop.departureDelaySeconds)
        assertEquals("SCHEDULED", stop.scheduleRelationship)
        assertEquals("platform-2", stop.assignedStopId)
        assertEquals("REGULAR", stop.pickupType)
        assertEquals("NONE", stop.dropOffType)
        assertEquals("Zentrum", stop.stopHeadsign)
        assertEquals(GermanyRealtimeStopUpdateValidity.VALID, stop.validity)
        assertEquals("Hinweis", snapshot.serviceAlerts.single().header)
        assertEquals(setOf("route-1"), snapshot.serviceAlerts.single().routeIds)
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
