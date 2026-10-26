package com.nazofobi.arrivalalarm

/**
 * Deduplicates only consecutive automatic searches. Explicit submission can
 * still retry the same query, and clearing the field or replacing the local
 * catalog permits the same query to run again.
 *
 * Owned by the current dataState in the UI; constructing a new gate when the
 * catalog changes prevents results from an earlier feed from being reused.
 */
internal fun normalizedSearchQuery(rawQuery: String): String = rawQuery.trim()

// Bound queries before sending them to search providers or saving recents.
internal const val MAX_SEARCH_QUERY_LENGTH = 256
internal fun isSupportedSearchQuery(rawQuery: String): Boolean =
    normalizedSearchQuery(rawQuery).length in 2..MAX_SEARCH_QUERY_LENGTH

internal class SearchQueryRequestGate {
    private var lastRequestedQuery: String? = null

    fun markRequested(rawQuery: String) {
        val query = normalizedSearchQuery(rawQuery)
        if (isSupportedSearchQuery(query)) lastRequestedQuery = query
    }

    // An intervening edit must invalidate the old deduplication key even if
    // that intermediate query is abandoned before the debounce timer fires.
    fun observeQuery(rawQuery: String) {
        val query = normalizedSearchQuery(rawQuery)
        if (lastRequestedQuery != null && query != lastRequestedQuery) reset()
    }

    fun shouldAutoRequest(rawQuery: String): Boolean {
        val query = normalizedSearchQuery(rawQuery)
        if (!isSupportedSearchQuery(query)) {
            reset()
            return false
        }
        return query != lastRequestedQuery
    }

    fun reset() {
        lastRequestedQuery = null
    }
}
