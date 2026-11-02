package com.nazofobi.arrivalalarm

import android.content.SharedPreferences

/**
 * Explicit deletion of one device-local search. Keep unrelated history in
 * original order; never trigger a new search or alter the current journey.
 */
internal object RecentSearchHistory {
    private const val QUERIES_KEY = "queries"
    private const val SEPARATOR = '\u001F'

    fun withoutQuery(current: List<String>, query: String): List<String> {
        if (query.isBlank()) return current
        val remaining = current.filterNot { it.equals(query, ignoreCase = true) }
        return if (remaining.size == current.size) current else remaining
    }

    /** A failed durable write must not be reported to the UI as a deletion. */
    fun removeAndPersist(
        preferences: SharedPreferences,
        current: List<String>,
        query: String,
    ): List<String> {
        val remaining = withoutQuery(current, query)
        if (remaining === current) return current
        val editor = preferences.edit()
        if (remaining.isEmpty()) editor.remove(QUERIES_KEY)
        else editor.putString(QUERIES_KEY, remaining.joinToString(SEPARATOR.toString()))
        return if (editor.commit()) remaining else current
    }
}
