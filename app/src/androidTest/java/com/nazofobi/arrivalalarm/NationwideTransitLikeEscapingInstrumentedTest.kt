package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NationwideTransitLikeEscapingInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun routeAndTripAutocompleteTreatsLikeWildcardsLiterally() {
        context.deleteDatabase("nationwide_transit.db")
        val feed = File(context.cacheDir, "g168-like-escape.zip")
        writeFeed(feed)
        val index = NationwideTransitIndex(context)

        try {
            index.importFeed(feed.toURI().toURL().toString()) { }

            assertTrue(index.isReady())
            assertEquals(10_000, index.stopCount())

            // '%' is a literal character in a route short name, not a wildcard.
            assertEquals(
                listOf("line:route-percent", "trip:trip-percent"),
                index.searchRouteAndTripLocations("L%", 10).map { it.key },
            )

            // '_' is literal in a trip headsign; it must not match the decoy H2.
            assertEquals(
                listOf("trip:trip-headsign"),
                index.searchRouteAndTripLocations("H_", 10).map { it.key },
            )

            // '!' is the SQL LIKE escape character and must itself be escaped.
            assertEquals(
                listOf("trip:T!3"),
                index.searchRouteAndTripLocations("T!", 10).map { it.key },
            )

            // Long-name matching uses the same literal-prefix contract.
            assertEquals(
                listOf("line:route-long-name", "trip:trip-long-name"),
                index.searchRouteAndTripLocations("P_", 10).map { it.key },
            )
        } finally {
            index.close()
            feed.delete()
        }
    }

    private fun writeFeed(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            put(zip, "stops.txt", buildString {
                appendLine("stop_id,stop_name,stop_lat,stop_lon")
                appendLine("known-good,Known Good Bahnhof,52.0,8.0")
                for (i in 1 until 10_000) {
                    appendLine("fixture-$i,Fixture Stop $i,52.0,8.0")
                }
            })
            put(
                zip,
                "routes.txt",
                """
                route_id,route_short_name,route_long_name,route_type
                route-percent,L%2,Percent Route,3
                route-percent-decoy,L12,Percent Decoy Route,3
                route-headsign,H1,Headsign Route,3
                route-headsign-decoy,H2,Headsign Decoy Route,3
                route-trip-id,T1,Trip ID Route,3
                route-trip-id-decoy,T2,Trip ID Decoy Route,3
                route-long-name,P1,P_ Route,3
                route-long-name-decoy,P2,P2 Route,3
                """.trimIndent() + "\n",
            )
            put(
                zip,
                "trips.txt",
                """
                route_id,service_id,trip_id,trip_headsign,direction_id
                route-percent,service-1,trip-percent,Regular Terminal,0
                route-percent-decoy,service-1,trip-percent-decoy,Regular Decoy Terminal,0
                route-headsign,service-1,trip-headsign,H_ Terminal,0
                route-headsign-decoy,service-1,trip-headsign-decoy,H2 Terminal,0
                route-trip-id,service-1,T!3,Regular Trip,0
                route-trip-id-decoy,service-1,T13,Regular Decoy Trip,0
                route-long-name,service-1,trip-long-name,Regular Long Name Terminal,0
                route-long-name-decoy,service-1,trip-long-name-decoy,Regular Decoy Long Name Terminal,0
                """.trimIndent() + "\n",
            )
            put(
                zip,
                "stop_times.txt",
                """
                trip_id,arrival_time,departure_time,stop_id,stop_sequence
                trip-percent,08:00:00,08:00:00,known-good,1
                trip-percent,08:05:00,08:05:00,fixture-1,2
                """.trimIndent() + "\n",
            )
            put(
                zip,
                "calendar.txt",
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                    "service-1,1,1,1,1,1,1,1,20260101,20261231\n",
            )
        }
    }

    private fun put(zip: ZipOutputStream, name: String, body: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(body.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
