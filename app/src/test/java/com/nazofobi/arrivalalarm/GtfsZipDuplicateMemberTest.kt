package com.nazofobi.arrivalalarm

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

    private fun expectDuplicate(seen: MutableSet<String>, member: String, table: String) {
        try {
            rejectDuplicateGtfsZipMember(member, seen)
            fail("Expected duplicate GTFS ZIP table to be rejected")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("Duplicate GTFS ZIP table: $table"))
        }
    }
}
