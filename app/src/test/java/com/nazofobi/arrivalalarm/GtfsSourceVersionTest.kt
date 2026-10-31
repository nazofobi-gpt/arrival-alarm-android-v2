package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GtfsSourceVersionTest {
    @Test
    fun etagTakesPrecedenceAndIsScopedToSourceUrl() {
        assertEquals(
            "https://example.test/feed.zip|etag:\"v1\"",
            resolveGtfsSourceVersion(
                sourceUrl = "https://example.test/feed.zip",
                etag = "\"v1\"",
                lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
                connectionLastModified = 1234L,
                importedAtMillis = 1000L,
            ),
        )
    }

    @Test
    fun lastModifiedIsUsedWhenEtagIsMissing() {
        assertEquals(
            "https://example.test/feed.zip|last-modified:Wed, 21 Oct 2015 07:28:00 GMT",
            resolveGtfsSourceVersion(
                sourceUrl = "https://example.test/feed.zip",
                etag = null,
                lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
                connectionLastModified = 1234L,
                importedAtMillis = 1000L,
            ),
        )
    }

    @Test
    fun connectionLastModifiedIsUsedBeforeImportGeneration() {
        assertEquals(
            "https://example.test/feed.zip|connection-last-modified:1234",
            resolveGtfsSourceVersion(
                sourceUrl = "https://example.test/feed.zip",
                etag = null,
                lastModified = null,
                connectionLastModified = 1234L,
                importedAtMillis = 1000L,
            ),
        )
    }

    @Test
    fun missingValidatorsUseImportGenerationInsteadOfStaticUrl() {
        val first = resolveGtfsSourceVersion(
            sourceUrl = "https://example.test/feed.zip",
            etag = null,
            lastModified = null,
            connectionLastModified = 0L,
            importedAtMillis = 1000L,
        )
        val second = resolveGtfsSourceVersion(
            sourceUrl = "https://example.test/feed.zip",
            etag = null,
            lastModified = null,
            connectionLastModified = 0L,
            importedAtMillis = 2000L,
        )

        assertEquals("https://example.test/feed.zip|imported-at:1000", first)
        assertNotEquals(first, second)
    }

    @Test
    fun identicalValidatorsFromDifferentSourcesDoNotCollide() {
        val first = resolveGtfsSourceVersion(
            sourceUrl = "https://one.example/feed.zip",
            etag = "\"same\"",
            lastModified = null,
            connectionLastModified = 0L,
            importedAtMillis = 1000L,
        )
        val second = resolveGtfsSourceVersion(
            sourceUrl = "https://two.example/feed.zip",
            etag = "\"same\"",
            lastModified = null,
            connectionLastModified = 0L,
            importedAtMillis = 1000L,
        )

        assertNotEquals(first, second)
    }
}
