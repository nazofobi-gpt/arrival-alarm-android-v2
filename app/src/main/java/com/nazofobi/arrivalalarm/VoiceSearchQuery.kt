package com.nazofobi.arrivalalarm

/**
 * Speech recognition contributes text to the existing manual search field.
 * It must never resolve a destination, start tracking, or arm an alarm.
 */
internal fun acceptedVoiceSearchQuery(candidates: List<String>?): String? =
    candidates.orEmpty()
        .asSequence()
        .map(::normalizedSearchQuery)
        .firstOrNull(::isSupportedSearchQuery)
