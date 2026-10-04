package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G168RealFeedSearchInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanup() { context.deleteDatabase("nationwide_transit.db") }

    @Test
    fun germanyAcceptanceStationsAreSearchableLocally() {
        val archivePath = InstrumentationRegistry.getArguments().getString("germanyFullPath").orEmpty()
        if (archivePath.isBlank()) return
        val archive = File(archivePath)
        assertTrue(archive.isFile && archive.length() > 0L)

        context.deleteDatabase("nationwide_transit.db")
        val index = NationwideTransitIndex(context)
        index.importFeed(archive.toURI().toString()) { }

        val cases = ZipFile(archive).use { zip ->
            val entry = requireNotNull(zip.getEntry("g175_acceptance.json"))
            zip.getInputStream(entry).bufferedReader().use { reader ->
                JSONObject(reader.readText()).getJSONObject("cases")
            }
        }

        listOf("lohne-achim", "berlin", "munich", "cross-region").forEach { name ->
            val definition = cases.getJSONObject(name)
            listOf("origin", "destination").forEach { key ->
                val label = definition.getJSONObject(key).getString("name")
                assertTrue(index.search(label, 5).isNotEmpty())
            }
        }
        index.close()
    }
}
