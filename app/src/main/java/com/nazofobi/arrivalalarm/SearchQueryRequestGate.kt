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

internal class SearchQueryRequestGate {
    private var lastRequestedQuery: String? = null

    fun markRequested(rawQuery: String) {
        val query = normalizedSearchQuery(rawQuery)
        if (query.length >= 2) lastRequestedQuery = query
    }

    // An intervening edit must invalidate the old deduplication key even if
    // that intermediate query is abandoned before the debounce timer fires.
    fun observeQuery(rawQuery: String) {
        val query = normalizedSearchQuery(rawQuery)
        if (lastRequestedQuery != null && query != lastRequestedQuery) reset()
    }

    fun shouldAutoRequest(rawQuery: String): Boolean {
        val query = normalizedSearchQuery(rawQuery)
        if (query.length < 2) {
            reset()
            return false
        }
        return query != lastRequestedQuery
    }

    fun reset() {
        lastRequestedQuery = null
    }
}
