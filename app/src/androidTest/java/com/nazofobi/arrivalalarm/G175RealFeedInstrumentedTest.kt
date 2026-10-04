package com.nazofobi.arrivalalarm

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar
import java.util.zip.ZipFile
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class G175RealFeedInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test
    fun germanyFullProviderOffRoutingMatrix() {
        val archivePath = InstrumentationRegistry.getArguments().getString("germanyFullPath").orEmpty()
        assumeTrue("germanyFullPath instrumentation argument is required", archivePath.isNotBlank())
        val archive = File(archivePath)
        assertTrue("Germany Full archive missing: $archivePath", archive.isFile && archive.length() > 0L)

        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        val runtime = Runtime.getRuntime()
        val importMemoryBefore = usedMemory(runtime)
        val importStarted = System.nanoTime()
        index.importFeed(archive.toURI().toString()) { }
        val importMillis = elapsedMillis(importStarted)
        val importMemoryDelta = (usedMemory(runtime) - importMemoryBefore).coerceAtLeast(0L)

        assertTrue(index.isReady())
        assertTrue("expected Germany Full-derived nationwide threshold", index.stopCount() >= 10_000)

        val acceptanceCases = ZipFile(archive).use { zip ->
            val entry = zip.getEntry("g175_acceptance.json")
                ?: error("g175_acceptance.json missing from reduced real-feed fixture")
            zip.getInputStream(entry).bufferedReader().use { reader ->
                JSONObject(reader.readText()).getJSONObject("cases")
            }
        }

        val departureMillis = nextWeekdayAtEight()
        val planner = ProductionStaticJourneyPlanner(index, nowMillis = { departureMillis })
        val cases = listOf(
            "lohne-achim",
            "berlin",
            "munich",
            "cross-region",
        ).map { name ->
            val definition = acceptanceCases.getJSONObject(name)
            name to (
                acceptancePoint(definition, "origin") to
                    acceptancePoint(definition, "destination")
            )
        }
        val results = JSONArray()
        var totalOptions = 0
        cases.forEach { (name, pair) ->
            val memoryBefore = usedMemory(runtime)
            val started = System.nanoTime()
            val origin = pair.first
            val destination = pair.second
            val first = planner.plan(origin, destination, limit = 3)
            val latencyMillis = elapsedMillis(started)
            val memoryDelta = (usedMemory(runtime) - memoryBefore).coerceAtLeast(0L)
            val options = (first as? LocalStaticJourneyOutcome.Results)?.options
                ?: error("$name did not resolve locally: $first")
            val second = planner.plan(origin, destination, limit = 3)
            val secondOptions = (second as? LocalStaticJourneyOutcome.Results)?.options
                ?: error("$name repeat did not resolve locally: $second")
            assertEquals("$name must be deterministic", options.map { it.id }, secondOptions.map { it.id })
            assertTrue("$name latency exceeded 120 seconds", latencyMillis < 120_000L)
            assertTrue("$name memory delta exceeded 768 MiB", memoryDelta < 768L * 1024L * 1024L)
            totalOptions += options.size
            results.put(
                JSONObject()
                    .put("case", name)
                    .put("origin", pair.first.label)
                    .put("destination", pair.second.label)
                    .put("outcome", "LOCAL_STATIC")
                    .put("optionCount", options.size)
                    .put("optionIds", JSONArray(options.map { it.id }))
                    .put("latencyMillis", latencyMillis)
                    .put("memoryDeltaBytes", memoryDelta)
            )
        }
        assertTrue("matrix should expose multiple graph-backed choices", totalOptions > cases.size)

        val noPath = planner.plan(
            MapPoint(0.0, 0.0, "Atlantic control origin"),
            MapPoint(0.25, 0.25, "Atlantic control destination"),
            limit = 3,
        )
        assertEquals(LocalStaticJourneyOutcome.NoPath, noPath)

        val report = JSONObject()
            .put("taskId", "G-175-REAL-FEED-VERIFY-001")
            .put("source", archive.name)
            .put("sourceBytes", archive.length())
            .put("sourceVersion", index.sourceVersion())
            .put("stopCount", index.stopCount())
            .put("providerCalls", 0)
            .put("providerMode", "OFF_LOCAL_STATIC_ONLY")
            .put("departureEpochMillis", departureMillis)
            .put("importMillis", importMillis)
            .put("importMemoryDeltaBytes", importMemoryDelta)
            .put("noPathOutcome", noPath.toString())
            .put("cases", results)
        val reportFile = File(context.getExternalFilesDir(null), "g175-real-feed-report.json")
        reportFile.writeText(report.toString(2))
        persistReportForCi(reportFile)
        index.close()
    }

    private fun persistReportForCi(reportFile: File) {
        val persistentPath = "/sdcard/Download/g175-real-feed-report.json"
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
                // Drain stdout so the command completes before the test process exits.
            }
        }
    }

    private fun acceptancePoint(definition: JSONObject, key: String): MapPoint {
        val point = definition.getJSONObject(key)
        return MapPoint(
            latitude = point.getDouble("lat"),
            longitude = point.getDouble("lon"),
            label = point.getString("name"),
        )
    }

    private fun nextWeekdayAtEight(): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin")).apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun usedMemory(runtime: Runtime): Long = runtime.totalMemory() - runtime.freeMemory()
    private fun elapsedMillis(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000L
}
