package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsHttpResponseStatusInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sourceUrl = "https://feed.example.invalid/germany.zip"

    @Before fun reset() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @After fun cleanup() {
        context.deleteDatabase("nationwide_transit.db")
    }

    @Test fun partialContentMustNotBeImportedAsFullGtfsFeed() {
        assertRejectedWithoutReadingBody(HttpURLConnection.HTTP_PARTIAL)
    }

    @Test fun noContentMustNotBeImportedAsFullGtfsFeed() {
        assertRejectedWithoutReadingBody(HttpURLConnection.HTTP_NO_CONTENT)
    }

    @Test fun failedHttpStatusReadDisconnectsWithoutImporting() {
        val connection = object : HttpURLConnection(URL(sourceUrl)) {
            var disconnected = false
            var streamReads = 0

            override fun connect() = Unit
            override fun disconnect() { disconnected = true }
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = throw IOException("synthetic HTTP status failure")
            override fun getInputStream(): InputStream {
                streamReads++
                return ByteArrayInputStream(byteArrayOf())
            }
        }
        val index = NationwideTransitIndex(context)
        try {
            val failure = runCatching {
                index.importFeedWithConnection(sourceUrl, {}, connection)
            }.exceptionOrNull()
            assertTrue("The original network failure must be preserved", failure is IOException)
            assertEquals("synthetic HTTP status failure", failure?.message)
            assertTrue("Failed HTTP connection must be disconnected", connection.disconnected)
            assertEquals(0, connection.streamReads)
            assertFalse(index.isReady())
        } finally {
            index.close()
        }
    }

    private fun assertRejectedWithoutReadingBody(statusCode: Int) {
        val connection = object : HttpURLConnection(URL(sourceUrl)) {
            var streamReads = 0

            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = statusCode
            override fun getInputStream(): InputStream {
                streamReads++
                return ByteArrayInputStream(byteArrayOf())
            }
        }
        val index = NationwideTransitIndex(context)
        try {
            val failure = runCatching {
                index.importFeedWithConnection(sourceUrl, {}, connection)
            }.exceptionOrNull()
            assertTrue("Expected explicit HTTP status rejection", failure is IllegalStateException)
            assertEquals("GTFS indirme HTTP $statusCode", failure?.message)
            assertEquals("Invalid response body must never be consumed", 0, connection.streamReads)
            assertFalse("Invalid HTTP status must not create a ready index", index.isReady())
        } finally {
            index.close()
        }
    }
}
