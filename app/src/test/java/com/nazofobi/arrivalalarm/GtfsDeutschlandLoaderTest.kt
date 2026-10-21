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
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\\ns1,\\\"Lohne\\nZOB\\\",52.66,8.23\\ns2,\\\"Bremen \\\"\\\"Hbf\\\"\\\"\\\",53.08,8.80\\n",
            "routes.txt" to "route_id,route_short_name\\nr1,RE\\n",
            "trips.txt" to "route_id,trip_id\\nr1,t1\\n",
            "stop_times.txt" to "trip_id,departure_time,stop_id,stop_sequence\\nt1,10:00:00,s1,1\\nt1,11:00:00,s2,2\\n",
        )
        val snapshot = GtfsDeutschlandLoader.parse(zip, "fixture", "2026-10-21")
        assertEquals(2, snapshot.stops.size)
        assertEquals("Lohne\\nZOB", snapshot.stops.single { it.id == "s1" }.name)
        assertEquals("Bremen \\\"Hbf\\\"", snapshot.stops.single { it.id == "s2" }.name)
        assertEquals(listOf("s1", "s2"), snapshot.routes.single().stopIds)
    }

    @Test fun unterminatedQuotedGtfsRecordRejectsMalformedFeed() {
        val zip = zipOf(
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\\ns1,\\\"unterminated\\n",
            "routes.txt" to "route_id,route_short_name\\nr1,RE\\n",
            "trips.txt" to "route_id,trip_id\\nr1,t1\\n",
            "stop_times.txt" to "trip_id,stop_id,stop_sequence\\nt1,s1,1\\n",
        )
        val failure = runCatching {
            GtfsDeutschlandLoader.parse(zip, "malformed", "2026-10-21")
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("unterminated"))
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> files.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() } }
        return bytes.toByteArray()
    }
}
