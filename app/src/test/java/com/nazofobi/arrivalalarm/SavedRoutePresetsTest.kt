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
    private class MemorySavedRoutePresetStringStorage(
        var value: String? = null,
        var failRead: Boolean = false,
        var failWrite: Boolean = false,
    ) : SavedRoutePresetStringStorage {
        var writeCount: Int = 0
        var lastKey: String? = null

        override fun read(key: String): String? {
            if (failRead) error("read failed")
            lastKey = key
            return value
        }

        override fun write(key: String, value: String) {
            if (failWrite) error("write failed")
            lastKey = key
            this.value = value
            writeCount += 1
        }
    }

    @Test
    fun storePersistsAndLoadsValidPresetUsingVersionedKey() {
        val storage = MemorySavedRoutePresetStringStorage()
        val store = SavedRoutePresetsStore(storage)
        val expected = preset()

        assertEquals(
            SavedRoutePresetStoreResult.Success(listOf(expected)),
            store.save(expected),
        )
        assertEquals(SavedRoutePresetsStore.PREFERENCE_KEY, storage.lastKey)
        assertEquals(listOf(expected), store.load().presets)
        assertTrue(store.load().isSourceSafeToReplace)
        assertEquals(1, storage.writeCount)
    }

    @Test
    fun savingSameEndpointsUpdatesExistingPresetWithoutDuplicatingIt() {
        val storage = MemorySavedRoutePresetStringStorage()
        val store = SavedRoutePresetsStore(storage)
        val original = preset()
        val updated = original.copy(
            id = "replacement-id",
            name = "Updated home to work",
            savedAtEpochMillis = original.savedAtEpochMillis + 60_000L,
        )

        assertTrue(store.save(original) is SavedRoutePresetStoreResult.Success)
        val result = store.save(updated) as SavedRoutePresetStoreResult.Success

        assertEquals(1, result.presets.size)
        assertEquals(original.id, result.presets.single().id)
        assertEquals(updated.name, result.presets.single().name)
        assertEquals(updated.savedAtEpochMillis, result.presets.single().savedAtEpochMillis)
        assertEquals(2, storage.writeCount)
    }

    @Test
    fun savingNewEndpointsWithAnExistingIdRejectsWithoutRemovingUnrelatedPreset() {
        val storage = MemorySavedRoutePresetStringStorage()
        val store = SavedRoutePresetsStore(storage)
        val existing = preset(id = "stable-route", name = "Existing route")

        assertTrue(store.save(existing) is SavedRoutePresetStoreResult.Success)
        val originalSnapshot = storage.value
        val conflicting = preset(id = existing.id, name = "Different route").copy(
            originLatitude = 49.0,
            destinationLatitude = 52.0,
        )

        assertEquals(
            SavedRoutePresetStoreResult.Rejected(SavedRoutePresetStoreRejection.ID_CONFLICT),
            store.save(conflicting),
        )
        assertEquals(originalSnapshot, storage.value)
        assertEquals(listOf(existing), store.load().presets)
        assertEquals(1, storage.writeCount)
    }

    @Test
    fun storeRejectsThirteenthDistinctPresetWithoutEvictingExistingRoutes() {
        val twelve = (0 until SavedRoutePresetsCodec.MAX_PRESETS).map { index ->
            preset(
                id = "route-$index",
                name = "Route $index",
            ).copy(
                originLatitude = 50.0 + index * 0.01,
                destinationLatitude = 53.0 + index * 0.01,
            )
        }
        val encoded = SavedRoutePresetsCodec.encode(twelve)!!
        val storage = MemorySavedRoutePresetStringStorage(value = encoded)
        val store = SavedRoutePresetsStore(storage)
        val extra = preset(id = "route-new", name = "New route").copy(
            originLatitude = 49.0,
            destinationLatitude = 52.0,
        )

        assertEquals(
            SavedRoutePresetStoreResult.Rejected(SavedRoutePresetStoreRejection.LIMIT_REACHED),
            store.save(extra),
        )
        assertEquals(encoded, storage.value)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun storeNeverOverwritesPartiallyDecodedOrCorruptSource() {
        val valid = preset()
        val corruptSource = SavedRoutePresetsCodec.encode(listOf(valid))!! + "\nnot-a-valid-record"
        val storage = MemorySavedRoutePresetStringStorage(value = corruptSource)
        val store = SavedRoutePresetsStore(storage)

        assertEquals(
            SavedRoutePresetStoreResult.Rejected(
                SavedRoutePresetStoreRejection.SOURCE_UNSAFE,
                setOf("corrupt-record"),
            ),
            store.save(preset(id = "new-route", name = "New route")),
        )
        assertEquals(
            SavedRoutePresetStoreResult.Rejected(
                SavedRoutePresetStoreRejection.SOURCE_UNSAFE,
                setOf("corrupt-record"),
            ),
            store.remove(valid.id),
        )
        assertEquals(corruptSource, storage.value)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun removingLastPresetWritesAnExplicitEmptySnapshot() {
        val storage = MemorySavedRoutePresetStringStorage()
        val store = SavedRoutePresetsStore(storage)
        val original = preset()

        assertTrue(store.save(original) is SavedRoutePresetStoreResult.Success)
        assertEquals(
            SavedRoutePresetStoreResult.Success(emptyList()),
            store.remove(original.id),
        )
        assertEquals("", storage.value)
        assertTrue(store.load().isSourceSafeToReplace)
        assertTrue(store.load().presets.isEmpty())
    }

    @Test
    fun storageReadAndWriteFailuresAreReportedWithoutPretendingSuccess() {
        val readFailureStorage = MemorySavedRoutePresetStringStorage(failRead = true)
        val readFailureStore = SavedRoutePresetsStore(readFailureStorage)
        assertFalse(readFailureStore.load().isSourceSafeToReplace)
        assertEquals(
            SavedRoutePresetStoreResult.Rejected(
                SavedRoutePresetStoreRejection.SOURCE_UNSAFE,
                setOf("storage-read-failed"),
            ),
            readFailureStore.save(preset()),
        )
        assertEquals(0, readFailureStorage.writeCount)

        val writeFailureStorage = MemorySavedRoutePresetStringStorage(failWrite = true)
        val writeFailureStore = SavedRoutePresetsStore(writeFailureStorage)
        assertEquals(
            SavedRoutePresetStoreResult.Rejected(SavedRoutePresetStoreRejection.STORAGE_FAILURE),
            writeFailureStore.save(preset()),
        )
        assertEquals(0, writeFailureStorage.writeCount)
    }

}