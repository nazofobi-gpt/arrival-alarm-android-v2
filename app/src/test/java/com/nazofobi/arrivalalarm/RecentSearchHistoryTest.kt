package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RecentSearchHistoryTest {
    @Test fun disabledHistoryNeverDisplaysPersistedQueries() {
        val stored = listOf("Bremen", "Lohne")
        assertEquals(emptyList<String>(), RecentSearchHistory.visibleQueries(false, stored))
        assertSame(stored, RecentSearchHistory.visibleQueries(true, stored))
    }

    @Test fun deletesExactlyOneRecentQueryWithoutChangingOtherOrder() {
        val source = listOf("Bremen Hbf", "Lohne", "Berlin Hbf")
        assertEquals(
            listOf("Bremen Hbf", "Berlin Hbf"),
            RecentSearchHistory.withoutQuery(source, "loHNE"),
        )
        assertEquals(listOf("Bremen Hbf", "Lohne", "Berlin Hbf"), source)
    }

    @Test fun unknownOrBlankQueryDoesNotRewriteHistory() {
        val source = listOf("Bremen Hbf", "Lohne")
        assertSame(source, RecentSearchHistory.withoutQuery(source, ""))
        assertSame(source, RecentSearchHistory.withoutQuery(source, "  "))
        assertSame(source, RecentSearchHistory.withoutQuery(source, "Munich"))
    }

    @Test fun removingLastEntryReturnsEmptyHistory() {
        assertEquals(emptyList<String>(), RecentSearchHistory.withoutQuery(listOf("Lohne"), "Lohne"))
    }
}
