package com.nazofobi.arrivalalarm

import android.os.ParcelFileDescriptor
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import com.google.transit.realtime.GtfsRealtime
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G176LiveRealtimeInstrumentedTest {
    @Test
    fun currentGermanyRealtimePayloadParsesWithProductionDefaults() {
        val payloadPath =
            InstrumentationRegistry.getArguments().getString("realtimePath").orEmpty()
        assumeTrue("realtimePath instrumentation argument is required", payloadPath.isNotBlank())

        val payload = File(payloadPath)
        assertTrue(
            "Germany realtime payload missing: $payloadPath",
            payload.isFile && payload.length() > 0L,
        )
        assertTrue(
            "live payload exceeds production default bound: ${payload.length()} bytes",
            payload.length() <= GERMANY_REALTIME_DEFAULT_MAX_BYTES.toLong(),
        )

        val fetchedAtEpochSeconds = System.currentTimeMillis() / 1_000L
        val result = GermanyGtfsRealtimeClient(
            clock = EpochClock { fetchedAtEpochSeconds },
            streamLoader = { payload.inputStream() },
        ).fetch()

        assertTrue("current Germany GTFS-RT did not parse", result is GermanyRealtimeFetchResult.Available)
        val snapshot = (result as GermanyRealtimeFetchResult.Available).snapshot

        assertEquals(GermanyGtfsRealtimeProvenance.PROVIDER, snapshot.source)
        assertEquals(GermanyGtfsRealtimeProvenance.LICENSE, snapshot.license)
        assertEquals(fetchedAtEpochSeconds, snapshot.fetchedAtEpochSeconds)
        assertTrue(!snapshot.gtfsRealtimeVersion.isNullOrBlank())
        assertEquals("FULL_DATASET", snapshot.incrementality)
        assertTrue(
            "live feed contained neither TripUpdates nor ServiceAlerts",
            snapshot.tripUpdates.isNotEmpty() || snapshot.serviceAlerts.isNotEmpty(),
        )
        snapshot.feedTimestampEpochSeconds?.let { timestamp ->
            assertTrue("feed timestamp must be positive", timestamp > 0L)
        }

        val report = JSONObject()
            .put("taskId", "G-176-REAL-SAMPLE-VERIFY-001")
            .put("acceptanceLayer", "CURRENT_LIVE_PROVENANCE_SMOKE")
            .put("endpoint", GermanyGtfsRealtimeProvenance.ENDPOINT)
            .put("source", snapshot.source)
            .put("license", snapshot.license)
            .put("payloadBytes", payload.length())
            .put("productionMaxBytes", GERMANY_REALTIME_DEFAULT_MAX_BYTES)
            .put("fetchedAtEpochSeconds", snapshot.fetchedAtEpochSeconds)
            .put("feedTimestampEpochSeconds", snapshot.feedTimestampEpochSeconds)
            .put("gtfsRealtimeVersion", snapshot.gtfsRealtimeVersion)
            .put("incrementality", snapshot.incrementality)
            .put("feedVersion", snapshot.feedVersion)
            .put("tripUpdateCount", snapshot.tripUpdates.size)
            .put("serviceAlertCount", snapshot.serviceAlerts.size)
            .put("regionalAcceptanceSatisfied", false)
            .put(
                "regionalAcceptanceNote",
                "Current-live smoke does not assert provider/agency completeness; retained regional samples remain required.",
            )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reportFile = File(context.getExternalFilesDir(null), "g176-live-realtime-report.json")
        reportFile.writeText(report.toString(2))
        persistReportForCi(reportFile)
    }

    @Test
    fun largeVehicleOnlyPayloadDoesNotRetainWholeFeedGraph() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val payload = File(context.cacheDir, "g176-large-vehicle-only.pb")
        writeLargeVehicleOnlyPayload(payload, entityCount = 180_000)
        assertTrue(
            "synthetic GTFS-RT payload was not created",
            payload.isFile && payload.length() > 0L,
        )
        assertTrue(
            "synthetic payload exceeds production bound: ${payload.length()} bytes",
            payload.length() <= GERMANY_REALTIME_DEFAULT_MAX_BYTES.toLong(),
        )

        val result = GermanyGtfsRealtimeClient(
            clock = EpochClock { 1_700_000_100L },
            streamLoader = { payload.inputStream() },
        ).fetch()

        assertTrue(
            "large vehicle-only GTFS-RT payload did not parse incrementally",
            result is GermanyRealtimeFetchResult.Available,
        )
        val snapshot = (result as GermanyRealtimeFetchResult.Available).snapshot
        assertEquals("2.0", snapshot.gtfsRealtimeVersion)
        assertEquals("FULL_DATASET", snapshot.incrementality)
        assertTrue(snapshot.tripUpdates.isEmpty())
        assertTrue(snapshot.serviceAlerts.isEmpty())
    }

    private fun writeLargeVehicleOnlyPayload(
        payload: File,
        entityCount: Int,
    ) {
        payload.outputStream().use { raw ->
            val output = CodedOutputStream.newInstance(raw)
            val header = GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("2.0")
                .setTimestamp(1_700_000_000L)
                .build()
            output.writeTag(1, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            output.writeUInt32NoTag(header.serializedSize)
            header.writeTo(output)

            repeat(entityCount) { index ->
                val vehicle = GtfsRealtime.VehiclePosition.newBuilder()
                    .setTrip(
                        GtfsRealtime.TripDescriptor.newBuilder()
                            .setTripId("trip-$index"),
                    )
                    .setVehicle(
                        GtfsRealtime.VehicleDescriptor.newBuilder()
                            .setId("vehicle-$index"),
                    )
                    .setPosition(
                        GtfsRealtime.Position.newBuilder()
                            .setLatitude(52.0f)
                            .setLongitude(8.0f),
                    )
                    .setTimestamp(1_700_000_000L + index)
                    .build()
                val entity = GtfsRealtime.FeedEntity.newBuilder()
                    .setId("vehicle-entity-$index")
                    .setVehicle(vehicle)
                    .build()
                output.writeTag(2, WireFormat.WIRETYPE_LENGTH_DELIMITED)
                output.writeUInt32NoTag(entity.serializedSize)
                entity.writeTo(output)
            }
            output.flush()
        }
    }

    private fun persistReportForCi(reportFile: File) {
        val persistentPath = "/sdcard/Download/g176-live-realtime-report.json"
        runShell("rm -f $persistentPath")
        runShell("cp ${reportFile.absolutePath} $persistentPath")
    }

    private fun runShell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            val buffer = ByteArray(4096)
            while (stream.read(buffer) != -1) {
                // Drain stdout so the command completes before instrumentation exits.
            }
        }
    }
}