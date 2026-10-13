package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapCoordinateInputTest {
    @Test fun acceptsDecimalCommaAndDotAsExplicitMapPoint() {
        val berlin = MapCoordinateInput.parse(" 52,5200 ", "13.4050", "Map point")
        assertEquals(52.52, berlin!!.latitude, 0.0000001)
        assertEquals(13.405, berlin.longitude, 0.0000001)
        assertEquals("Map point", berlin.label)
    }

    @Test fun acceptsSignedWorldBoundaries() {
        assertEquals(-90.0, MapCoordinateInput.parse("-90", "+180", "South")!!.latitude, 0.0)
        assertEquals(180.0, MapCoordinateInput.parse("-90", "+180", "South")!!.longitude, 0.0)
    }

    @Test fun rejectsCoordinatesOutsideEarthOrNonFinite() {
        assertNull(MapCoordinateInput.parse("90.0001", "13", "Invalid"))
        assertNull(MapCoordinateInput.parse("52", "-180.001", "Invalid"))
        assertNull(MapCoordinateInput.parse("NaN", "13", "Invalid"))
        assertNull(MapCoordinateInput.parse("Infinity", "13", "Invalid"))
    }

    @Test fun rejectsPartialMalformedAndUnboundedInput() {
        assertNull(MapCoordinateInput.parse("", "13", "Invalid"))
        assertNull(MapCoordinateInput.parse("52", "", "Invalid"))
        assertNull(MapCoordinateInput.parse("52.3.4", "13", "Invalid"))
        assertNull(MapCoordinateInput.parse("52,3.4", "13", "Invalid"))
        assertNull(MapCoordinateInput.parse("52N", "13E", "Invalid"))
        assertNull(MapCoordinateInput.parse("1".repeat(33), "13", "Invalid"))
    }
}
