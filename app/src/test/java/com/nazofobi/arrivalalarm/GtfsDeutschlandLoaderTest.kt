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

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> files.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() } }
        return bytes.toByteArray()
    }
}
