package com.nazofobi.arrivalalarm

import com.google.transit.realtime.GtfsRealtime
import java.io.IOException
import org.junit.Assert.assertEquals
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

        val update = snapshot.tripUpdates.single()
        assertEquals("trip-1", update.tripId)
        assertEquals("route-1", update.routeId)
        assertEquals("20261002", update.startDate)
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
