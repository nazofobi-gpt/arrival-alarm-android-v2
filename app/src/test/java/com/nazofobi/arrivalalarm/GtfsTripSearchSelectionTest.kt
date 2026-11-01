package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GtfsTripSearchSelectionTest {
    @Test
    fun selectionUsesExactTripMetadataAndExplicitServiceDate() {
        val template = GtfsTripSearchIdentityTemplate.from(
            result = tripResult(),
            sourceVersion = "germany-full-2026-10",
        )

        assertNotNull(template)
        val selected = requireNotNull(template).forServiceDate(GtfsServiceDate(2026, 10, 31))

        assertEquals(
            GtfsTripInstanceIdentity(
                tripId = "trip-1",
                serviceDate = GtfsServiceDate(2026, 10, 31),
                serviceId = "service-1",
                routeId = "route-1",
                directionId = 0,
                frequencyStartSeconds = null,
            ),
            selected.identity,
        )
        assertEquals("RE 9 → Achim", selected.displayLabel)
        assertEquals("germany-full-2026-10", selected.sourceVersion)
    }

    @Test
    fun rejectsNonTripAndIncompleteSearchResults() {
        val result = tripResult()

        assertNull(
            GtfsTripSearchIdentityTemplate.from(
                result.copy(kind = TransitLocationKind.LINE),
                "feed-v1",
            ),
        )
        assertNull(
            GtfsTripSearchIdentityTemplate.from(
                result.copy(serviceId = null),
                "feed-v1",
            ),
        )
        assertNull(
            GtfsTripSearchIdentityTemplate.from(
                result.copy(routeId = null),
                "feed-v1",
            ),
        )
        assertNull(
            GtfsTripSearchIdentityTemplate.from(
                result.copy(key = "line:route-1"),
                "feed-v1",
            ),
        )
        assertNull(GtfsTripSearchIdentityTemplate.from(result, " "))
    }

    private fun tripResult() = TransitLocationResult(
        key = "trip:trip-1",
        kind = TransitLocationKind.TRIP,
        label = "RE 9 → Achim",
        latitude = 0.0,
        longitude = 0.0,
        routeId = "route-1",
        tripId = "trip-1",
        autocompleteText = "Achim",
        serviceId = "service-1",
        directionId = 0,
    )
}

class TransitLocationResultMergeTest {
    @Test
    fun sameDisplayLabelDoesNotCollapseDifferentTrips() {
        val merged = mergeTransitLocationResults(
            local = listOf(tripResult("trip-1"), tripResult("trip-2")),
            live = emptyList(),
            limit = 10,
        )
        assertEquals(listOf("trip-1", "trip-2"), merged.mapNotNull { it.tripId })
    }

    @Test
    fun repeatedStableTripKeyIsCollapsed() {
        val first = tripResult("trip-1")
        val duplicate = first.copy(label = "A refreshed display label")
        val merged = mergeTransitLocationResults(
            local = listOf(first),
            live = listOf(duplicate),
            limit = 10,
        )
        assertEquals(1, merged.size)
        assertEquals("trip-1", merged.single().tripId)
    }

    private fun tripResult(tripId: String) = TransitLocationResult(
        key = "trip:$tripId",
        kind = TransitLocationKind.TRIP,
        label = "RE 9 → Achim",
        latitude = 0.0,
        longitude = 0.0,
        routeId = "route-1",
        tripId = tripId,
        autocompleteText = "Achim",
        serviceId = "service-1",
        directionId = 0,
    )
}
