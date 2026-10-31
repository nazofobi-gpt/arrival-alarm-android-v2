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
    fun persistedControlCharactersCannotBecomeHttpRequestHeaders() {
        val safeDate = "Sun, 11 Oct 2026 12:00:00 GMT"
        val safeEtag = "W/\"feed-42\""
        for (unsafe in listOf("\"old\"\r\nX-Injected: true", "v1\u0000bad", "v1\tbad", "v1\u007fbad")) {
            assertEquals(
                mapOf("If-Modified-Since" to safeDate),
                gtfsConditionalHeaders(true, true, unsafe, safeDate),
            )
            assertEquals(
                mapOf("If-None-Match" to safeEtag),
                gtfsConditionalHeaders(true, true, safeEtag, unsafe),
            )
        }
        assertEquals(
            mapOf("If-None-Match" to safeEtag, "If-Modified-Since" to safeDate),
            gtfsConditionalHeaders(true, true, safeEtag, safeDate),
        )
    }

    @Test
    fun oversizedPersistedHeadersCannotBreakFutureConditionalRevalidation() {
        val safeEtag = "\"germany-feed-42\""
        val safeDate = "Sun, 11 Oct 2026 12:00:00 GMT"
        val oversized = "X".repeat(1025)
        assertEquals(
            mapOf("If-Modified-Since" to safeDate),
            gtfsConditionalHeaders(true, true, oversized, safeDate),
        )
        assertEquals(
            mapOf("If-None-Match" to safeEtag),
            gtfsConditionalHeaders(true, true, safeEtag, oversized),
        )
        assertTrue(gtfsConditionalHeaders(true, true, oversized, oversized).isEmpty())
    }

    @Test
    fun nonLatin1ValidatorsAreNotReplayedAsInvalidHttpFields() {
        val safeEtag = "\"germany-feed-42\""
        val safeDate = "Sun, 11 Oct 2026 12:00:00 GMT"
        for (unsafe in listOf("\"bad\u2028line\"", "\"euro\u20ac\"", "\"train\ud83d\ude86\"")) {
            assertEquals(
                mapOf("If-Modified-Since" to safeDate),
                gtfsConditionalHeaders(true, true, unsafe, safeDate),
            )
            assertEquals(
                mapOf("If-None-Match" to safeEtag),
                gtfsConditionalHeaders(true, true, safeEtag, unsafe),
            )
            assertTrue(gtfsConditionalHeaders(true, true, unsafe, unsafe).isEmpty())
        }
        // RFC-compatible one-byte obs-text remains eligible for provider ETags.
        assertEquals(
            mapOf("If-None-Match" to "\"München\""),
            gtfsConditionalHeaders(true, true, "\"München\"", null),
        )
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
