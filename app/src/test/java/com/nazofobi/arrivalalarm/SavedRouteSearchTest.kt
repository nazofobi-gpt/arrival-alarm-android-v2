package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRouteSearchTest {
    private val route = SavedRoutePreset(
        id = "route-1",
        name = "Straße nach München",
        originLatitude = 48.14,
        originLongitude = 11.58,
        originLabel = "München",
        destinationLatitude = 51.23,
        destinationLongitude = 6.78,
        destinationLabel = "Düsseldorf",
        directOnlyRoutes = false,
        fewerTransfersFirst = false,
        shorterWalkingFirst = false,
        atMostOneTransfer = false,
        savedAtEpochMillis = 1_000L,
    )

    @Test fun germanUmlautAndAsciiKeyboardSpellingMatch() {
        listOf("München", "Munchen", "Muenchen", "mUEnchen",
            "Dusseldorf", "Duesseldorf", "STRASSE", "Straße").forEach {
            assertTrue("Missing route search match for: $it", savedRouteMatchesQuery(route, it))
        }
    }

    @Test fun missingLabelsDoNotMatchAndBlankQueryRestoresAll() {
        assertFalse(savedRouteMatchesQuery(route, "Berlin"))
        assertFalse(savedRouteMatchesQuery(route, "unrelated-place"))
        assertTrue(savedRouteMatchesQuery(route, "  "))
    }
}
