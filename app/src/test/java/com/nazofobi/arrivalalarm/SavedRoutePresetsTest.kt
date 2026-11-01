package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
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
    }

    @Test
    fun invalidCoordinatesAndBlankOrOversizedLabelsAreNotPersisted() {
        val invalid = listOf(
            preset().copy(originLatitude = Double.NaN),
            preset().copy(destinationLongitude = 181.0),
            preset().copy(name = "  "),
            preset().copy(originLabel = "x".repeat(121)),
            preset().copy(id = "bad id"),
        )

        assertTrue(SavedRoutePresetsCodec.encode(invalid).isEmpty())
    }

    @Test
    fun malformedRecordDoesNotDiscardValidSibling() {
        val valid = preset()
        val encoded = SavedRoutePresetsCodec.encode(listOf(valid)) + "\nnot-a-valid-record"

        assertEquals(listOf(valid), SavedRoutePresetsCodec.decode(encoded))
    }

    @Test
    fun duplicateIdsAndExcessPresetsAreBoundedWithoutReordering() {
        val first = preset(id = "route-0", name = "Route 0")
        val presets = listOf(
            first,
            first.copy(name = "Duplicate must not replace first"),
        ) + (1..10).map { index ->
            preset(id = "route-$index", name = "Route $index")
        }

        val restored = SavedRoutePresetsCodec.decode(SavedRoutePresetsCodec.encode(presets))

        assertEquals(SavedRoutePresetsCodec.MAX_PRESETS, restored.size)
        assertEquals("route-0", restored.first().id)
        assertEquals("Route 0", restored.first().name)
        assertEquals("route-9", restored.last().id)
        assertEquals(restored.size, restored.map { it.id }.distinct().size)

        val duplicateRaw = SavedRoutePresetsCodec.encode(
            listOf(first, preset(id = "route-1", name = "Route 1"))
        ).replace("route-1", "route-0")
        assertEquals(listOf(first), SavedRoutePresetsCodec.decode(duplicateRaw))
    }

    @Test
    fun unknownVersionOversizedPayloadAndInvalidBooleanFailClosed() {
        val encoded = SavedRoutePresetsCodec.encode(listOf(preset()))
        val invalidBoolean = encoded.replace(
            "\u001f0\u001f1\u001f0\u001f1\u001f",
            "\u001fmaybe\u001f1\u001f0\u001f1\u001f",
        )

        assertTrue(SavedRoutePresetsCodec.decode("SAVED_ROUTE_PRESETS_V2\n$encoded").isEmpty())
        assertTrue(SavedRoutePresetsCodec.decode("x".repeat(32_769)).isEmpty())
        assertTrue(SavedRoutePresetsCodec.decode(invalidBoolean).isEmpty())
    }
}