package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsFeedRevalidationTest {
    @Test
    fun readySnapshotOfSameFeedSendsBothConditionalValidators() {
        assertEquals(
            mapOf(
                "If-None-Match" to "\"germany-feed-42\"",
                "If-Modified-Since" to "Sun, 11 Oct 2026 12:00:00 GMT",
            ),
            gtfsConditionalHeaders(
                ready = true,
                sameSource = true,
                etag = "\"germany-feed-42\"",
                lastModified = "Sun, 11 Oct 2026 12:00:00 GMT",
            ),
        )
    }

    @Test
    fun unreadyOrChangedFeedNeverReusesOldValidators() {
        listOf(
            gtfsConditionalHeaders(false, true, "\"old\"", "yesterday"),
            gtfsConditionalHeaders(true, false, "\"old\"", "yesterday"),
        ).forEach { assertTrue(it.isEmpty()) }
    }

    @Test
    fun missingValidatorsTriggerUnconditionalRefresh() {
        assertTrue(gtfsConditionalHeaders(true, true, "", " ").isEmpty())
        assertTrue(gtfsConditionalHeaders(true, true, null, null).isEmpty())
    }

    @Test
    fun lastModifiedOnlyFeedCanRevalidateWithoutEtag() {
        val headers = gtfsConditionalHeaders(
            ready = true,
            sameSource = true,
            etag = null,
            lastModified = "Sun, 11 Oct 2026 12:00:00 GMT",
        )
        assertFalse(headers.containsKey("If-None-Match"))
        assertEquals("Sun, 11 Oct 2026 12:00:00 GMT", headers["If-Modified-Since"])
    }
}
