package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTargetsTest {
    private val germany = MapPoint(51.1657, 10.4515, "Germany")
    private val bremen = MapPoint(53.0793, 8.8017, "Bremen Hbf")
    private val munich = MapPoint(48.1402, 11.5586, "München Hbf")

    @Test fun bothSelectedEndpointsStayInMapCameraFrame() {
        assertEquals(
            listOf(bremen, munich),
            selectionMapCameraTargets(germany, bremen, munich),
        )
    }

    @Test fun aSingleSelectedEndpointTakesPriorityOverCountryCenter() {
        assertEquals(
            listOf(munich),
            selectionMapCameraTargets(germany, null, munich),
        )
    }

    @Test fun duplicateCoordinateDoesNotProduceDegenerateBounds() {
        val sameStop = MapPoint(bremen.latitude, bremen.longitude, "Platform 2")
        assertEquals(
            listOf(bremen),
            selectionMapCameraTargets(germany, bremen, sameStop),
        )
    }

    @Test fun oppositeAntimeridianLongitudesAreTheSameCameraTarget() {
        val east = MapPoint(0.0, 180.0, "East")
        val west = MapPoint(0.0, -180.0, "West")
        assertEquals(
            listOf(east),
            selectionMapCameraTargets(germany, east, west),
        )
    }

    @Test fun signedZeroAliasesDoNotGenerateDuplicateMapBounds() {
        val first = MapPoint(0.0, -0.0, "Origin")
        val second = MapPoint(-0.0, 0.0, "Destination")
        assertEquals(
            listOf(first),
            selectionMapCameraTargets(germany, first, second),
        )
    }

    @Test fun invalidEndpointsAreNotPassedToMapLibreCamera() {
        val impossibleLatitude = MapPoint(90.0, 8.0, "Pole")
        val notANumber = MapPoint(Double.NaN, 12.0, "Invalid")
        val invalidLongitude = MapPoint(53.0, 181.0, "Invalid")
        assertEquals(
            listOf(germany),
            selectionMapCameraTargets(germany, impossibleLatitude, invalidLongitude),
        )
        assertEquals(
            listOf(munich),
            selectionMapCameraTargets(germany, notANumber, munich),
        )
        assertTrue(
            selectionMapCameraTargets(notANumber, impossibleLatitude, invalidLongitude).isEmpty(),
        )
    }

    @Test fun noSelectionFallsBackToValidMapCenter() {
        assertEquals(listOf(germany), selectionMapCameraTargets(germany, null, null))
    }
}
