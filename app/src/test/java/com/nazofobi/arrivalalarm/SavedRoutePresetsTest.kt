package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRoutePresetsTest {
    private fun preset(
        id: String = "home-work",
        name: String = "Home to work",
        directOnlyRoutes: Boolean = false,
        fewerTransfersFirst: Boolean = true,
        shorterWalkingFirst: Boolean = false,
        atMostOneTransfer: Boolean = true,
    ) = SavedRoutePreset(
        id = id,
        name = name,
        originLatitude = 52.665,
        originLongitude = 8.237,
        originLabel = "Lohne Hbf + Bus",
        destinationLatitude = 53.083,
        destinationLongitude = 8.813,
        destinationLabel = "Bremen Hbf / City",
        directOnlyRoutes = directOnlyRoutes,
        fewerTransfersFirst = fewerTransfersFirst,
        shorterWalkingFirst = shorterWalkingFirst,
        atMostOneTransfer = atMostOneTransfer,
        savedAtEpochMillis = 1_798_800_000_000L,
    )

    @Test
    fun roundTripsEndpointsLabelsAndEveryRoutePreference() {
        val original = preset(
            name = "Lohne → Bremen | Hauptbahnhof",
            directOnlyRoutes = true,
            fewerTransfersFirst = true,
            shorterWalkingFirst = true,
            atMostOneTransfer = false,
        )
        val encoded = SavedRoutePresetsCodec.encode(listOf(original))

        assertEquals(listOf(original), SavedRoutePresetsCodec.decode(encoded))
        assertTrue(SavedRoutePresetsCodec.decodeWithStatus(encoded).isSourceSafeToReplace)
    }

    @Test
    fun invalidCoordinatesAndBlankOrOversizedLabelsAreRejectedWithoutDroppingRecords() {
        val invalid = listOf(
            preset().copy(originLatitude = Double.NaN),
            preset().copy(destinationLongitude = 181.0),
            preset().copy(name = "  "),
            preset().copy(originLabel = "x".repeat(121)),
            preset().copy(id = "bad id"),
        )

        invalid.forEach { assertNull(SavedRoutePresetsCodec.encode(listOf(it))) }
    }

    @Test
    fun malformedRecordKeepsValidSiblingButMarksSourceUnsafeToReplace() {
        val valid = preset()
        val encoded = SavedRoutePresetsCodec.encode(listOf(valid))!! + "\nnot-a-valid-record"
        val decoded = SavedRoutePresetsCodec.decodeWithStatus(encoded)

        assertEquals(listOf(valid), decoded.presets)
        assertFalse(decoded.isSourceSafeToReplace)
        assertTrue("corrupt-record" in decoded.issues)
    }

    @Test
    fun duplicateIdsAndOverCapacityAreRejectedWithoutSilentEviction() {
        val first = preset(id = "route-0", name = "Route 0")
        assertNull(SavedRoutePresetsCodec.encode(listOf(first, first.copy(name = "Duplicate"))))

        val twelve = (0 until SavedRoutePresetsCodec.MAX_PRESETS).map { index ->
            preset(id = "route-$index", name = "Route $index")
        }
        val encodedTwelve = SavedRoutePresetsCodec.encode(twelve)!!
        assertEquals(SavedRoutePresetsCodec.MAX_PRESETS, SavedRoutePresetsCodec.decode(encodedTwelve).size)
        assertNull(SavedRoutePresetsCodec.encode(twelve + preset(id = "route-12", name = "Route 12")))

        val duplicateRaw = SavedRoutePresetsCodec.encode(
            listOf(first, preset(id = "route-1", name = "Route 1"))
        )!!.replace("route-1", "route-0")
        val duplicateDecoded = SavedRoutePresetsCodec.decodeWithStatus(duplicateRaw)
        assertEquals(listOf(first), duplicateDecoded.presets)
        assertFalse(duplicateDecoded.isSourceSafeToReplace)
        assertTrue("duplicate-id" in duplicateDecoded.issues)

        val extraRecord = SavedRoutePresetsCodec.encode(listOf(preset(id = "route-12", name = "Route 12")))!!
            .substringAfter('\n')
        val overCapRaw = "$encodedTwelve\n$extraRecord"
        val overCapDecoded = SavedRoutePresetsCodec.decodeWithStatus(overCapRaw)
        assertEquals(SavedRoutePresetsCodec.MAX_PRESETS, overCapDecoded.presets.size)
        assertFalse(overCapDecoded.isSourceSafeToReplace)
        assertTrue("too-many-presets" in overCapDecoded.issues)
    }

    @Test
    fun unknownVersionOversizedPayloadAndInvalidBooleanFailClosed() {
        val encoded = SavedRoutePresetsCodec.encode(listOf(preset()))!!
        val invalidBoolean = encoded.replace(
            "\u001f0\u001f1\u001f0\u001f1\u001f",
            "\u001fmaybe\u001f1\u001f0\u001f1\u001f",
        )

        assertFalse(SavedRoutePresetsCodec.decodeWithStatus("SAVED_ROUTE_PRESETS_V2\n$encoded").isSourceSafeToReplace)
        assertFalse(SavedRoutePresetsCodec.decodeWithStatus("x".repeat(32_769)).isSourceSafeToReplace)
        assertFalse(SavedRoutePresetsCodec.decodeWithStatus(invalidBoolean).isSourceSafeToReplace)
        assertTrue(SavedRoutePresetsCodec.decode(invalidBoolean).isEmpty())
    }

    @Test
    fun emptySnapshotIsAnExplicitSafeClear() {
        assertEquals("", SavedRoutePresetsCodec.encode(emptyList()))
        val decoded = SavedRoutePresetsCodec.decodeWithStatus("")
        assertTrue(decoded.presets.isEmpty())
        assertTrue(decoded.isSourceSafeToReplace)
    }
}