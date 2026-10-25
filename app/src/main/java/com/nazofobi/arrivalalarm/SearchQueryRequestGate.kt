package com.nazofobi.arrivalalarm

/**
 * Deduplicates only consecutive automatic searches. Explicit submission can
 * still retry the same query, and clearing the field or replacing the local
 * catalog permits the same query to run again.
 *
 * Owned by the current dataState in the UI; constructing a new gate when the
 * catalog changes prevents results from an earlier feed from being reused.
 */
internal class SearchQueryRequestGate {
    private var lastRequestedQuery: String? = null

    fun markRequested(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.length >= 2) lastRequestedQuery = query
    }

    fun shouldAutoRequest(rawQuery: String): Boolean {
        val query = rawQuery.trim()
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
