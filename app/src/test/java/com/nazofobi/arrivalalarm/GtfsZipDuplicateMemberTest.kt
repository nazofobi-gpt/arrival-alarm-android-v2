package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GtfsZipDuplicateMemberTest {
    @Test
    fun duplicateRequiredTableInNestedCaseAliasIsRejected() {
        val seen = hashSetOf<String>()
        rejectDuplicateGtfsZipMember("stops.txt", seen)
        expectDuplicate(seen, "nested/STOPS.TXT", "stops.txt")
    }

    @Test
    fun duplicateOptionalTableIsRejected() {
        val seen = hashSetOf<String>()
        rejectDuplicateGtfsZipMember("feed_info.txt", seen)
        expectDuplicate(seen, "nested/FEED_INFO.TXT", "feed_info.txt")
    }

    @Test
    fun unrelatedZipMembersDoNotConsumeTableNames() {
        val seen = hashSetOf<String>()
        rejectDuplicateGtfsZipMember("metadata/readme.md", seen)
        rejectDuplicateGtfsZipMember("stops.txt.bak", seen)
        assertTrue(seen.isEmpty())
        rejectDuplicateGtfsZipMember("nested/stops.txt", seen)
        assertTrue("stops.txt" in seen)
    }

    @Test
    fun distinctRequiredAndOptionalTablesAreAccepted() {
        val seen = hashSetOf<String>()
        listOf("stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar.txt")
            .forEach { rejectDuplicateGtfsZipMember(it, seen) }
        assertTrue(seen.size == 5)
    }

    @Test
    fun oneEnclosingGtfsFolderIsAcceptedRegardlessOfTableCase() {
        var directory: String? = null
        for (entry in listOf("germany/STOPS.TXT", "germany/routes.txt", "germany/TRIPS.TXT")) {
            directory = enforceSingleGtfsTableDirectory(entry, directory)
        }
        assertEquals("germany", directory)
    }

    @Test
    fun unrelatedZipMembersCannotChangeAcceptedGtfsFolder() {
        var directory = enforceSingleGtfsTableDirectory("germany/stops.txt", null)
        directory = enforceSingleGtfsTableDirectory("other/readme.txt", directory)
        directory = enforceSingleGtfsTableDirectory("other/stops.txt.bak", directory)
        assertEquals("germany", directory)
        assertEquals("germany", enforceSingleGtfsTableDirectory("germany/routes.txt", directory))
    }

    @Test
    fun mixedRootAndNestedGtfsTablesAreRejected() {
        val root = enforceSingleGtfsTableDirectory("stops.txt", null)
        assertEquals("", root)
        expectDirectoryConflict(root, "nested/routes.txt")
    }

    @Test
    fun mixedSiblingGtfsFoldersAreRejected() {
        val first = enforceSingleGtfsTableDirectory("feed-a/stops.txt", null)
        expectDirectoryConflict(first, "feed-b/routes.txt")
    }

    private fun expectDirectoryConflict(directory: String?, member: String) {
        try {
            enforceSingleGtfsTableDirectory(member, directory)
            fail("Expected mixed GTFS ZIP table directories to be rejected")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("GTFS ZIP tables span multiple directories"))
        }
    }

    private fun expectDuplicate(seen: MutableSet<String>, member: String, table: String) {
        try {
            rejectDuplicateGtfsZipMember(member, seen)
            fail("Expected duplicate GTFS ZIP table to be rejected")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("Duplicate GTFS ZIP table: $table"))
        }
    }
}
