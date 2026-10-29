package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GtfsDeutschlandLoaderTest {
    @Test fun parsesDynamicGtfsWithQuotedCsvAndProvenance() {
        val zip = zipOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,\"Berlin, Hbf\",52.5251,13.3694\ns2,Alexanderplatz,52.5219,13.4132\n",
            "routes.txt" to "route_id,route_short_name,route_long_name\nr1,S5,S-Bahn Berlin\n",
            "trips.txt" to "route_id,trip_id,trip_headsign\nr1,t1,Strausberg Nord\n",
            "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\nt1,12:00:00,12:00:00,s1,1\nt1,12:05:00,12:05:00,s2,2\n",
        )
        val snapshot = GtfsDeutschlandLoader.parse(zip, "etag-123", "2026-09-23T08:50:00+02:00")
        assertFalse(snapshot.stale)
        assertEquals("etag-123", snapshot.providers.single().sourceVersion)
        assertEquals("Berlin, Hbf", snapshot.stops.first().name)
        assertEquals(listOf("s1", "s2"), snapshot.routes.single().stopIds)
        assertEquals("12:00:00", snapshot.trips.single().departure)
        assertTrue(snapshot.providers.single().staticSource.contains("download.gtfs.de"))
    }


    @Test fun missingCoreTablesFailClosedInsteadOfPublishingEmptySuccessfulSnapshot() {
        val required = listOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,Lohne,52.66,8.23\n",
            "routes.txt" to "route_id,route_short_name\nr1,RE\n",
            "trips.txt" to "route_id,trip_id\nr1,t1\n",
            "stop_times.txt" to "trip_id,stop_id,stop_sequence\nt1,s1,1\n",
        )
        for ((missing, _) in required) {
            val partial = required.filterNot { it.first == missing }
            val failure = runCatching {
                GtfsDeutschlandLoader.parse(zipOf(*partial.toTypedArray()), "truncated", "2026-10-17")
            }.exceptionOrNull()
            assertTrue("Missing $missing must fail closed: $failure",
                failure is IllegalArgumentException && failure.message.orEmpty().contains(missing))
        }
    }

    @Test fun duplicateCoreZipMembersDoNotSilentlyOverwriteEarlierFeedData() {
        val zip = zipOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,Lohne,52.66,8.23\n",
            "nested/stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,Wrong,52.66,8.23\n",
            "routes.txt" to "route_id,route_short_name\nr1,RE\n",
            "trips.txt" to "route_id,trip_id\nr1,t1\n",
            "stop_times.txt" to "trip_id,stop_id,stop_sequence\nt1,s1,1\n",
        )
        val failure = runCatching {
            GtfsDeutschlandLoader.parse(zip, "duplicate", "2026-10-17")
        }.exceptionOrNull()
        assertTrue(
            "Duplicate normalized core member must fail closed: $failure",
            failure is IllegalArgumentException && failure.message.orEmpty().contains("stops.txt"),
        )
    }


    @Test fun multilineQuotedStopNamesAreNotSplitIntoPhantomRows() {
        val zip = zipOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,\"Lohne\nZOB\",52.66,8.23\ns2,\"Bremen \"\"Hbf\"\"\",53.08,8.80\n",
            "routes.txt" to "route_id,route_short_name\nr1,RE\n",
            "trips.txt" to "route_id,trip_id\nr1,t1\n",
            "stop_times.txt" to "trip_id,departure_time,stop_id,stop_sequence\nt1,10:00:00,s1,1\nt1,11:00:00,s2,2\n",
        )
        val snapshot = GtfsDeutschlandLoader.parse(zip, "fixture", "2026-10-21")
        assertEquals(2, snapshot.stops.size)
        assertEquals("Lohne\nZOB", snapshot.stops.single { it.id == "s1" }.name)
        assertEquals("Bremen \"Hbf\"", snapshot.stops.single { it.id == "s2" }.name)
        assertEquals(listOf("s1", "s2"), snapshot.routes.single().stopIds)
    }

    @Test fun unterminatedQuotedGtfsRecordRejectsMalformedFeed() {
        val zip = zipOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,\"unterminated\n",
            "routes.txt" to "route_id,route_short_name\nr1,RE\n",
            "trips.txt" to "route_id,trip_id\nr1,t1\n",
            "stop_times.txt" to "trip_id,stop_id,stop_sequence\nt1,s1,1\n",
        )
        val failure = runCatching {
            GtfsDeutschlandLoader.parse(zip, "malformed", "2026-10-21")
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("unterminated"))
    }

    @Test fun invalidStopGeometryAndMissingReferencesCannotPublishPhantomTrips() {
        val zip = zipOf(
            "stops.txt" to (
                "stop_id,stop_name,stop_lat,stop_lon\n" +
                "s1,Lohne,52.66,8.23\n" +
                "s2,Achim,53.01,9.03\n" +
                "nan,BadNaN,NaN,8.23\n" +
                "infinite,BadInfinity,52.67,Infinity\n" +
                "latitude,BadLatitude,91,8.23\n" +
                "longitude,BadLongitude,52.67,-181\n"
            ),
            "routes.txt" to "route_id,route_short_name\nr1,RE\n",
            "trips.txt" to (
                "route_id,trip_id\n" +
                "r1,good\nr1,with_nan\nr1,with_infinite\n" +
                "r1,with_latitude\nr1,with_longitude\nr1,missing\n"
            ),
            "stop_times.txt" to (
                "trip_id,departure_time,stop_id,stop_sequence\n" +
                "good,10:00:00,s1,1\ngood,11:00:00,s2,2\n" +
                "with_nan,10:00:00,s1,1\nwith_nan,11:00:00,nan,2\n" +
                "with_infinite,10:00:00,s1,1\nwith_infinite,11:00:00,infinite,2\n" +
                "with_latitude,10:00:00,s1,1\nwith_latitude,11:00:00,latitude,2\n" +
                "with_longitude,10:00:00,s1,1\nwith_longitude,11:00:00,longitude,2\n" +
                "missing,10:00:00,s1,1\nmissing,11:00:00,unknown,2\n"
            ),
        )
        val snapshot = GtfsDeutschlandLoader.parse(zip, "fixture", "2026-10-21")
        assertEquals(listOf("s1", "s2"), snapshot.stops.map { it.id })
        assertEquals(listOf("r1:good"), snapshot.routes.map { it.id })
        assertEquals(listOf("s1", "s2"), snapshot.routes.single().stopIds)
        assertEquals(1, snapshot.trips.size)
    }

    @Test fun legalGeographicBoundaryCoordinatesRemainAvailable() {
        val zip = zipOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\nsouth,South,-90,180\nnorth,North,90,-180\n",
            "routes.txt" to "route_id,route_short_name\nr1,RE\n",
            "trips.txt" to "route_id,trip_id\nr1,t1\n",
            "stop_times.txt" to "trip_id,departure_time,stop_id,stop_sequence\nt1,10:00:00,south,1\nt1,11:00:00,north,2\n",
        )
        val snapshot = GtfsDeutschlandLoader.parse(zip, "fixture", "2026-10-21")
        assertEquals(2, snapshot.stops.size)
        assertEquals(listOf("south", "north"), snapshot.routes.single().stopIds)
    }


    @Test fun malformedUtf8InAnyRequiredCoreTableFailsClosed() {
        val core = linkedMapOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,Lohne,52.66,8.23\n",
            "routes.txt" to "route_id,route_short_name\nr1,RE\n",
            "trips.txt" to "route_id,trip_id\nr1,t1\n",
            "stop_times.txt" to "trip_id,stop_id,stop_sequence\nt1,s1,1\n",
        )
        for (corruptTable in core.keys) {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                for ((name, content) in core) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    if (name == corruptTable) zip.write(byteArrayOf(0xC3.toByte(), 0x28))
                    zip.closeEntry()
                }
            }
            val failure = runCatching {
                GtfsDeutschlandLoader.parse(bytes.toByteArray(), "malformed", "2026-10-29")
            }.exceptionOrNull()
            assertTrue(
                "Malformed UTF-8 in $corruptTable must fail closed: $failure",
                failure is java.nio.charset.CharacterCodingException,
            )
        }
    }

    @Test fun malformedStopSequenceCannotPublishJourney() {
        val invalidTimes = linkedMapOf(
            "missing" to "t1,s1,\n" + "t1,s2,2\n",
            "nonnumeric" to "t1,s1,abc\n" + "t1,s2,2\n",
            "negative" to "t1,s1,-1\n" + "t1,s2,2\n",
            "duplicate" to "t1,s1,2\n" + "t1,s2,2\n",
            "overflow" to "t1,s1,2147483648\n" + "t1,s2,2\n",
        )
        for ((case, times) in invalidTimes) {
            val snapshot = GtfsDeutschlandLoader.parse(
                zipOf(
                    "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,Lohne,52.66,8.23\ns2,Bremen,53.07,8.80\n",
                    "routes.txt" to "route_id,route_short_name\nr1,RE\n",
                    "trips.txt" to "route_id,trip_id\nr1,t1\n",
                    "stop_times.txt" to ("trip_id,stop_id,stop_sequence\n" + times),
                ),
                "sequence-$case",
                "2026-10-29",
            )
            assertTrue("$case must not publish a route", snapshot.routes.isEmpty())
            assertTrue("$case must not publish a trip", snapshot.trips.isEmpty())
        }
    }

    @Test fun sparseAndZeroBasedSequencesAreOrderedNumerically() {
        val snapshot = GtfsDeutschlandLoader.parse(
            zipOf(
                "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\ns1,Lohne,52.66,8.23\ns2,Bremen,53.07,8.80\ns3,Hamburg,53.55,10.00\n",
                "routes.txt" to "route_id,route_short_name\nr1,RE\n",
                "trips.txt" to "route_id,trip_id\nr1,sparse\nr1,zero\n",
                "stop_times.txt" to (
                    "trip_id,stop_id,stop_sequence\n" +
                    "sparse,s3,40\nsparse,s1,10\nsparse,s2,20\n" +
                    "zero,s2,2\nzero,s1,0\n"
                ),
            ),
            "valid-sequence",
            "2026-10-29",
        )
        assertEquals(listOf("sparse", "zero"), snapshot.trips.map { it.id })
        assertEquals(listOf("s1", "s2", "s3"), snapshot.routes.first().stopIds)
        assertEquals(listOf("s1", "s2"), snapshot.routes.last().stopIds)
    }

    @Test fun duplicateCoreGtfsIdsFailClosedInsteadOfAliasingCatalogObjects() {
        val base = linkedMapOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\\ns1,Lohne,52.66,8.23\\ns2,Bremen,53.07,8.80\\n",
            "routes.txt" to "route_id,route_short_name\\nr1,RE\\n",
            "trips.txt" to "route_id,trip_id\\nr1,t1\\n",
            "stop_times.txt" to "trip_id,stop_id,stop_sequence\\nt1,s1,1\\nt1,s2,2\\n",
        )
        val duplicateCases = listOf(
            Triple("stops.txt", "stop_id", "s1,Conflicting Stop,50.0,10.0\\n"),
            Triple("routes.txt", "route_id", "r1,Conflicting Route\\n"),
            Triple("trips.txt", "trip_id", "r1,t1\\n"),
        )
        for ((table, key, duplicateRow) in duplicateCases) {
            val feed = base.toMutableMap()
            feed[table] = feed.getValue(table) + duplicateRow
            val failure = runCatching {
                GtfsDeutschlandLoader.parse(
                    zipOf(*feed.map { it.key to it.value }.toTypedArray()),
                    "duplicate-ids",
                    "2026-10-30",
                )
            }.exceptionOrNull()
            assertTrue(
                "$table duplicate $key must fail closed: $failure",
                failure is IllegalArgumentException &&
                    failure.message.orEmpty().contains("Duplicate $key"),
            )
        }
    }

    @Test fun blankCoreGtfsIdsFailClosedBeforePublishingAmbiguousCatalog() {
        val base = linkedMapOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\\ns1,Lohne,52.66,8.23\\ns2,Bremen,53.07,8.80\\n",
            "routes.txt" to "route_id,route_short_name\\nr1,RE\\n",
            "trips.txt" to "route_id,trip_id\\nr1,t1\\n",
            "stop_times.txt" to "trip_id,stop_id,stop_sequence\\nt1,s1,1\\nt1,s2,2\\n",
        )
        val blankCases = listOf(
            Triple("stops.txt", "stop_id", ",Blank Stop,52.66,8.23\\n"),
            Triple("routes.txt", "route_id", ",Blank Route\\n"),
            Triple("trips.txt", "trip_id", "r1,\\n"),
        )
        for ((table, key, blankRow) in blankCases) {
            val feed = base.toMutableMap()
            feed[table] = feed.getValue(table) + blankRow
            val failure = runCatching {
                GtfsDeutschlandLoader.parse(
                    zipOf(*feed.map { it.key to it.value }.toTypedArray()),
                    "blank-ids",
                    "2026-10-30",
                )
            }.exceptionOrNull()
            assertTrue(
                "$table blank $key must fail closed: $failure",
                failure is IllegalArgumentException &&
                    failure.message.orEmpty().contains("Missing $key"),
            )
        }
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> files.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() } }
        return bytes.toByteArray()
    }
}
