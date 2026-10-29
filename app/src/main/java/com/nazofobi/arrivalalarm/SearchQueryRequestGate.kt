package com.nazofobi.arrivalalarm

/**
 * Deduplicates only consecutive automatic searches. Explicit submission can
 * still retry the same query, and clearing the field or replacing the local
 * catalog permits the same query to run again.
 *
 * Owned by the current dataState in the UI; constructing a new gate when the
 * catalog changes prevents results from an earlier feed from being reused.
 */
internal fun normalizedSearchQuery(rawQuery: String): String = buildString(rawQuery.length) {
    // A pasted multi-line or non-breaking-space stop name must share the same
    // provider query, search-effect key, and recent-history entry as typed text.
    var pendingSpace = false
    for (character in rawQuery) {
        if (character.isWhitespace()) {
            if (isNotEmpty()) pendingSpace = true
        } else {
            if (pendingSpace) append(' ')
            append(character)
            pendingSpace = false
        }
    }
}

// Bound queries before sending them to search providers or saving recents.
internal const val MAX_SEARCH_QUERY_LENGTH = 256
internal fun isSupportedSearchQuery(rawQuery: String): Boolean {
    val query = normalizedSearchQuery(rawQuery)
    // Reject invisible control characters from pasted text and legacy history:
    // they are not meaningful stop-name evidence for provider search.
    return query.length in 2..MAX_SEARCH_QUERY_LENGTH &&
        query.none { character ->
            java.lang.Character.isISOControl(character) ||
                character == '\u061C' || // Arabic letter mark (bidirectional formatting).
                character == '\u200B' || // Zero-width space.
                character == '\u200E' || character == '\u200F' || // Directional marks.
                character in '\u202A'..'\u202E' || // Directional embeddings/overrides.
                character == '\u2060' || // Word joiner.
                character in '\u2066'..'\u2069' || // Bidirectional isolates.
                character == '\uFEFF' // Invisible byte-order mark in pasted text.
        }
}

// Reject legacy persisted search terms that predate the query length gate.
// Normalize first so restored UI chips and subsequent searches use the same text.
internal fun restoredRecentSearchQueries(serialized: String?): List<String> =
    serialized.orEmpty().split('\u001F')
        .map(::normalizedSearchQuery)
        .filter(::isSupportedSearchQuery)
        // Match saveRecentSearch: one visible chip per query, ignoring case.
        // Deduplicate before the five-item cap so legacy repeats cannot hide valid recents.
        .distinctBy { it.lowercase() }
        .take(5)

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
