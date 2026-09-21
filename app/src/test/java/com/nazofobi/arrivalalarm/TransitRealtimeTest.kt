package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitRealtimeTest {
    private val staticRepo = FixtureStaticTransitRepository()
    private val now = 2_000_000L

    private fun repo(snapshot: RealtimeSnapshot?, throws: Boolean = false) = OverlayTransitRepository(
        staticRepository = staticRepo,
        realtimeSource = object : RealtimeTransitSource {
            override fun fetch(): RealtimeSnapshot? {
                if (throws) error("timeout")
                return snapshot
            }
        },
        clock = EpochClock { now },
        staleAfterSeconds = 180,
    )

    @Test fun freshSnapshotNeverReplacesStaticTruthWithoutExactTripId() {
        val result = repo(
            RealtimeSnapshot(now - 30, listOf(RealtimeUpdate("unrelated", delaySeconds = 420)))
        ).plan("fixture-bremen-hbf", "fixture-airport")
        assertNotNull(result)
        assertEquals(RealtimeFreshness.FRESH, result!!.realtime.freshness)
        assertNull(result.realtime.delaySeconds)
        assertEquals("Fixture 6", result.journey.legs.single().line)
        assertTrue(result.realtime.status.contains("eşleşen sefer yok"))
    }

    @Test fun staleSnapshotFallsBackToStaticJourney() {
        val result = repo(
            RealtimeSnapshot(now - 181, listOf(RealtimeUpdate("anything", delaySeconds = 600)))
        ).plan("fixture-bremen-hbf", "fixture-airport")!!
        assertEquals(RealtimeFreshness.STALE, result.realtime.freshness)
        assertNull(result.realtime.delaySeconds)
        assertEquals(listOf("Bremen Hbf", "Am Brill", "Flughafen Bremen"), result.journey.legs.single().stops.map { it.name })
    }

    @Test fun unavailableAndTimeoutBothPreserveStaticJourney() {
        val unavailable = repo(null).plan("fixture-bremen-hbf", "fixture-airport")!!
        val timeout = repo(null, throws = true).plan("fixture-bremen-hbf", "fixture-airport")!!
        assertEquals(RealtimeFreshness.UNAVAILABLE, unavailable.realtime.freshness)
        assertEquals(RealtimeFreshness.UNAVAILABLE, timeout.realtime.freshness)
        assertEquals(unavailable.journey, timeout.journey)
    }

    @Test fun supportedRealtimeSemanticsAreExplicitAndDoNotInventPlatform() {
        val update = RealtimeUpdate("trip-42", delaySeconds = 300, cancelled = true)
        assertEquals(300, update.delaySeconds)
        assertTrue(update.cancelled)
        assertNull(update.platform)
        val platformUpdate = update.copy(cancelled = false, platform = "4")
        assertEquals("4", platformUpdate.platform)
        assertFalse(platformUpdate.cancelled)
    }

    @Test fun invalidStaticJourneyStillFailsClosed() {
        val result = repo(RealtimeSnapshot(now, emptyList())).plan("missing", "fixture-airport")
        assertNull(result)
    }
}
