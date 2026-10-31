package com.nazofobi.arrivalalarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VbnLiveRealtimeInstrumentedTest {
    @Test
    fun currentVbnJsonPayloadParsesWithProductionAdapter() {
        val payloadPath = InstrumentationRegistry.getArguments().getString("vbnJsonPath").orEmpty()
        assumeTrue("vbnJsonPath instrumentation argument is required", payloadPath.isNotBlank())

        val payload = File(payloadPath)
        assertTrue("VBN JSON payload missing", payload.isFile && payload.length() > 0L)
        val rawJson = payload.readText()
        val root = JSONObject(rawJson)
        val header = root.optJSONObject("header") ?: root.optJSONObject("Header")
        val rawTimestamp = header?.opt("timestamp") ?: header?.opt("Timestamp")

        val snapshot = VbnJsonRealtimeSource(loader = { rawJson }).fetch()
        assertNotNull("current VBN GTFS-RT JSON did not parse", snapshot)
        val parsed = snapshot!!
        assertTrue("VBN provider timestamp must be positive", parsed.fetchedAtEpochSeconds > 0L)
        assertTrue("current VBN feed contained no TripUpdates", parsed.updates.isNotEmpty())

        val report = JSONObject()
            .put("taskId", "G-176-REAL-SAMPLE-VERIFY-001")
            .put("acceptanceLayer", "VBN_LIVE_JSON_PROVIDER_PARSE")
            .put("endpoint", VbnRealtimeProvenance.JSON_URL)
            .put("provider", VbnRealtimeProvenance.PROVIDER)
            .put("license", VbnRealtimeProvenance.LICENSE)
            .put("payloadBytes", payload.length())
            .put("rawTimestampJsonType", rawTimestamp?.javaClass?.simpleName ?: "missing")
            .put("rawTimestamp", rawTimestamp?.toString())
            .put("fetchedAtEpochSeconds", parsed.fetchedAtEpochSeconds)
            .put("tripUpdateCount", parsed.updates.size)
            .put("delayEvidenceCount", parsed.updates.count { it.delaySeconds != null })
            .put("cancelledCount", parsed.updates.count { it.cancelled })
            .put("platformCount", parsed.updates.count { !it.platform.isNullOrBlank() })
            .put("parsed", true)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reportFile = File(context.getExternalFilesDir(null), "vbn-live-realtime-report.json")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(report.toString(2))
    }
}
