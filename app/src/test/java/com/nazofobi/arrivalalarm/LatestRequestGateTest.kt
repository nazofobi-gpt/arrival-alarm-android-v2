package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestGateTest {
    @Test fun initialOrNonPositiveGenerationCannotPublishWithoutBegin() {
        val gate = LatestRequestGate()
        var published = false

        assertFalse(gate.isCurrent(0L))
        assertFalse(gate.isCurrent(-1L))
        assertFalse(gate.commitIfCurrent(0L) { published = true })
        assertFalse(gate.commitIfCurrent(-1L) { published = true })
        assertFalse(published)

        val validToken = gate.begin()
        assertTrue(validToken > 0L)
        assertTrue(gate.commitIfCurrent(validToken) { published = true })
        assertTrue(published)
    }

    @Test fun newerRequestRejectsOlderCallbacks() {
        val gate = LatestRequestGate()
        val first = gate.begin()
        val second = gate.begin()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test fun changingSelectionInvalidatesOutstandingCallback() {
        val gate = LatestRequestGate()
        val pending = gate.begin()

        gate.invalidate()

        assertFalse(gate.isCurrent(pending))
        assertTrue(gate.isCurrent(gate.begin()))
    }

    @Test fun multipleUpdatesForTheCurrentRequestRemainAllowed() {
        val gate = LatestRequestGate()
        val active = gate.begin()

        assertTrue(gate.isCurrent(active))
        assertTrue(gate.isCurrent(active))
        gate.begin()
        assertFalse(gate.isCurrent(active))
    }

    @Test fun outOfOrderRouteCallbackNeverOverwritesCurrentUiRegistryOrOfflineCache() {
        val gate = LatestRequestGate()
        var options = emptyList<String>()
        var registry = emptyList<String>()
        var status = "calculating"
        var busy = true
        val cache = mutableMapOf<String, List<String>>()
        fun request(key: String): (List<String>, String) -> Boolean {
            val token = gate.begin()
            return { result, message ->
                gate.commitIfCurrent(token) {
                    options = result
                    registry = result
                    status = message
                    busy = false
                    cache[key] = result
                }
            }
        }
        val old = request("Lohne->old")
        gate.invalidate()
        options = emptyList()
        registry = emptyList()
        busy = true
        val current = request("Lohne->Achim")
        assertTrue(current(listOf("current-trip"), "ready"))
        assertFalse(old(listOf("stale-trip"), "stale-provider"))
        assertEquals(listOf("current-trip"), options)
        assertEquals(listOf("current-trip"), registry)
        assertEquals("ready", status)
        assertFalse(busy)
        assertEquals(mapOf("Lohne->Achim" to listOf("current-trip")), cache)
    }

    @Test fun originChangePreventsOldNearbyAndStationCallbacksFromRepopulatingUi() {
        val nearGate = LatestRequestGate()
        val stationGate = LatestRequestGate()
        var nearby = emptyList<String>()
        var departures = emptyList<String>()
        var loading = false
        val oldNear = nearGate.begin()
        val oldStation = stationGate.begin()
        nearGate.invalidate()
        stationGate.invalidate()
        nearby = emptyList()
        departures = emptyList()
        assertFalse(nearGate.commitIfCurrent(oldNear) { nearby = listOf("old-nearby") })
        assertFalse(stationGate.commitIfCurrent(oldStation) {
            departures = listOf("old-departure")
            loading = true
        })
        assertTrue(nearby.isEmpty())
        assertTrue(departures.isEmpty())
        assertFalse(loading)
        assertTrue(nearGate.commitIfCurrent(nearGate.begin()) { nearby = listOf("new-nearby") })
        assertTrue(stationGate.commitIfCurrent(stationGate.begin()) { departures = listOf("new-departure") })
        assertEquals(listOf("new-nearby"), nearby)
        assertEquals(listOf("new-departure"), departures)
    }

}
