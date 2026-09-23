package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransitStaticTest {
    private val repo = TestFixtureStaticTransitRepository()

    @Test fun searchIsBoundedAndOffline() {
        assertEquals(listOf("Bremen Hbf"), repo.searchStops("hbf").map { it.name })
        assertEquals(listOf("Flughafen Bremen"), repo.searchStops("flug").map { it.name })
        assertEquals(emptyList<TransitStop>(), repo.searchStops("unknown"))
    }

    @Test fun itineraryHasLineDirectionAndOrderedStops() {
        val journey = repo.plan("fixture-bremen-hbf", "fixture-airport")
        assertNotNull(journey)
        val leg = journey!!.legs.single()
        assertEquals("Fixture 6", leg.line)
        assertEquals("Flughafen Bremen", leg.direction)
        assertEquals(listOf("Bremen Hbf", "Am Brill", "Flughafen Bremen"), leg.stops.map { it.name })
    }

    @Test fun invalidOrSameStopFailsClosed() {
        assertNull(repo.plan("missing", "fixture-airport"))
        assertNull(repo.plan("fixture-bremen-hbf", "fixture-bremen-hbf"))
    }

    @Test fun controllerExposesEmptyAndDegradedStates() {
        val controller = TransitController(repo)
        controller.search("does-not-exist")
        assertEquals(TransitLoadState.EMPTY, controller.state.loadState)
        controller.plan("fixture-bremen-hbf", "missing")
        assertEquals(TransitLoadState.DEGRADED, controller.state.loadState)
    }
}
