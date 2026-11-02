package com.nazofobi.arrivalalarm

import android.content.SharedPreferences

/**
 * Explicit deletion of one device-local search. Keep unrelated history in
 * original order; never trigger a new search or alter the current journey.
 */
internal object RecentSearchHistory {
    private const val QUERIES_KEY = "queries"
    private const val HISTORY_ENABLED_KEY = "history_enabled"

    fun isEnabled(preferences: SharedPreferences): Boolean =
        preferences.getBoolean(HISTORY_ENABLED_KEY, true)

    fun visibleQueries(enabled: Boolean, stored: List<String>): List<String> =
        if (enabled) stored else emptyList()

    /** Switching history off durably removes saved queries on this device. */
    fun setEnabledAndPersist(preferences: SharedPreferences, enabled: Boolean): Boolean {
        val editor = preferences.edit().putBoolean(HISTORY_ENABLED_KEY, enabled)
        if (!enabled) editor.remove(QUERIES_KEY)
        return editor.commit()
    }
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
