package com.nazofobi.arrivalalarm

/**
 * Pure local-only favorite mutation. Keep all other saved stops untouched,
 * and never persist an empty/invalid stop identifier.
 */
internal fun toggleFavoriteStopIds(current: Set<String>, stopId: String): Set<String> {
    if (stopId.isBlank()) return current
    return if (stopId in current) current - stopId else current + stopId
}
