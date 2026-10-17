package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitRealtimeTest {
    private val staticRepo = TestFixtureStaticTransitRepository()
    private val now = 2_000_000L

    private fun repo(snapshot: RealtimeSnapshot?, throws: Boolean = false) = OverlayTransitRepository(
        staticRepository = staticRepo,
        realtimeSource = object : RealtimeTransitSource {
            override fun fetch(): RealtimeSnapshot? { if (throws) error("timeout"); return snapshot }
        }, clock = EpochClock { now }, staleAfterSeconds = 180,
    )

    @Test fun vbnJsonAdapterMapsDocumentedHeaderTripAndDelay() {
        val json = """{"Header":{"Timestamp":2000000},"Entity":[{"TripUpdate":{"Trip":{"TripId":"trip-42","ScheduleRelationship":"Scheduled"},"StopTimeUpdate":[{"Departure":{"Delay":300}}]}}]}"""
        val snapshot = VbnJsonRealtimeSource(loader = { json }).fetch()
        assertNotNull(snapshot)
        assertEquals(now, snapshot!!.fetchedAtEpochSeconds)
        assertEquals("trip-42", snapshot.updates.single().tripId)
        assertEquals(300, snapshot.updates.single().delaySeconds)
        assertNull(snapshot.updates.single().platform)
    }

    @Test fun malformedVbnHeaderTimestampDoesNotClaimFreshness() {
        val invalid = listOf("\"2000000\"", "2000000.5", "2000000.0", "true", "null", "-1", "0")
        for (raw in invalid) {
            val json = """{"Header":{"Timestamp":$raw},"Entity":[]}"""
            assertNull(
                "Non-integral or invalid provider timestamp must be rejected: $raw",
                VbnJsonRealtimeSource(loader = { json }).fetch(),
            )
        }
    }

    @Test fun validLongVbnHeaderTimestampRemainsSupported() {
        val timestamp = Int.MAX_VALUE.toLong() + 42L
        val json = """{"Header":{"Timestamp":$timestamp},"Entity":[]}"""
        val snapshot = VbnJsonRealtimeSource(loader = { json }).fetch()
        assertNotNull(snapshot)
        assertEquals(timestamp, snapshot!!.fetchedAtEpochSeconds)
    }

    @Test fun malformedVbnDelayNeverClaimsZeroMinuteRealtimeEvidence() {
        val invalid = listOf("\"not-a-delay\"", "true", "12.5", "2147483648", "null")
        for (raw in invalid) {
            val json = """{"Header":{"Timestamp":2000000},"Entity":[{"TripUpdate":{"Trip":{"TripId":"trip-42"},"StopTimeUpdate":[{"Departure":{"Delay":$raw}}]}}]}"""
            val snapshot = VbnJsonRealtimeSource(loader = { json }).fetch()
            assertNotNull("Malformed delay should not invalidate the snapshot", snapshot)
            assertNull("Malformed delay must not be treated as zero: $raw", snapshot!!.updates.single().delaySeconds)
        }
    }

    @Test fun validSignedDelayAndArrivalFallbackRemainSupported() {
        for ((raw, expected) in listOf("300" to 300, "-120" to -120)) {
            val json = """{"Header":{"Timestamp":2000000},"Entity":[{"TripUpdate":{"Trip":{"TripId":"trip-42"},"StopTimeUpdate":[{"Departure":{"Delay":$raw}}]}}]}"""
            assertEquals(expected, VbnJsonRealtimeSource(loader = { json }).fetch()!!.updates.single().delaySeconds)
        }
        val fallback = """{"Header":{"Timestamp":2000000},"Entity":[{"TripUpdate":{"Trip":{"TripId":"trip-42"},"StopTimeUpdate":[{"Departure":{"Delay":"invalid"},"Arrival":{"Delay":90}}]}}]}"""
        assertEquals(90, VbnJsonRealtimeSource(loader = { fallback }).fetch()!!.updates.single().delaySeconds)
    }

    @Test fun malformedVbnPayloadFailsClosed() {
        assertNull(VbnJsonRealtimeSource(loader = { "not-json" }).fetch())
        assertNull(VbnJsonRealtimeSource(loader = { "{}" }).fetch())
    }

    @Test fun freshSnapshotNeverReplacesStaticTruthWithoutExactTripId() {
        val result = repo(RealtimeSnapshot(now - 30, listOf(RealtimeUpdate("unrelated", delaySeconds = 420)))).plan("fixture-bremen-hbf", "fixture-airport")
        assertNotNull(result); assertEquals(RealtimeFreshness.FRESH, result!!.realtime.freshness)
        assertNull(result.realtime.delaySeconds); assertEquals("Fixture 6", result.journey.legs.single().line)
        assertTrue(result.realtime.status.contains("eşleşen sefer yok"))
    }

    @Test fun futureDatedSnapshotCannotBePresentedAsFresh() {
        val future = RealtimeSnapshot(
            now + MAX_PROVIDER_FUTURE_SKEW_SECONDS + 1L,
            listOf(RealtimeUpdate("trip-42", delaySeconds = 600, cancelled = true)),
        )
        val result = repo(future).plan("fixture-bremen-hbf", "fixture-airport")!!
        assertEquals(RealtimeFreshness.UNAVAILABLE, result.realtime.freshness)
        assertNull(result.realtime.delaySeconds)
        assertFalse(result.realtime.cancelled)
        assertEquals("Fixture 6", result.journey.legs.single().line)
    }

    @Test fun smallProviderClockSkewRemainsAllowed() {
        for (skew in listOf(1L, MAX_PROVIDER_FUTURE_SKEW_SECONDS)) {
            val snapshot = RealtimeSnapshot(now + skew, emptyList())
            val result = repo(snapshot).plan("fixture-bremen-hbf", "fixture-airport")!!
            assertEquals(RealtimeFreshness.FRESH, result.realtime.freshness)
        }
    }

    @Test fun invalidSnapshotEpochNeverBecomesFresh() {
        for (epoch in listOf(-1L, 0L, Long.MAX_VALUE)) {
            val result = repo(RealtimeSnapshot(epoch, emptyList()))
                .plan("fixture-bremen-hbf", "fixture-airport")!!
            assertEquals(RealtimeFreshness.UNAVAILABLE, result.realtime.freshness)
            assertEquals("Fixture 6", result.journey.legs.single().line)
        }
    }

    @Test fun staleSnapshotFallsBackToStaticJourney() {
        val result = repo(RealtimeSnapshot(now - 181, listOf(RealtimeUpdate("anything", delaySeconds = 600)))).plan("fixture-bremen-hbf", "fixture-airport")!!
        assertEquals(RealtimeFreshness.STALE, result.realtime.freshness); assertNull(result.realtime.delaySeconds)
        assertEquals(listOf("Bremen Hbf", "Am Brill", "Flughafen Bremen"), result.journey.legs.single().stops.map { it.name })
    }

    @Test fun unavailableAndTimeoutBothPreserveStaticJourney() {
        val unavailable = repo(null).plan("fixture-bremen-hbf", "fixture-airport")!!
        val timeout = repo(null, throws = true).plan("fixture-bremen-hbf", "fixture-airport")!!
        assertEquals(RealtimeFreshness.UNAVAILABLE, unavailable.realtime.freshness)
        assertEquals(RealtimeFreshness.UNAVAILABLE, timeout.realtime.freshness); assertEquals(unavailable.journey, timeout.journey)
    }

    @Test fun supportedRealtimeSemanticsAreExplicitAndDoNotInventPlatform() {
        val update = RealtimeUpdate("trip-42", delaySeconds = 300, cancelled = true)
        assertEquals(300, update.delaySeconds); assertTrue(update.cancelled); assertNull(update.platform)
        val platformUpdate = update.copy(cancelled = false, platform = "4")
        assertEquals("4", platformUpdate.platform); assertFalse(platformUpdate.cancelled)
    }

    @Test fun invalidStaticJourneyStillFailsClosed() {
        assertNull(repo(RealtimeSnapshot(now, emptyList())).plan("missing", "fixture-airport"))
    }
}
