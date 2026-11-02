package com.nazofobi.arrivalalarm

/**
 * Reorders the Home projection only. Pinning a commute cannot change its
 * persisted route, destination, route preferences or alarm state.
 */
internal fun prioritizeSavedCommute(
    presets: List<SavedRoutePreset>,
    pinnedId: String?,
): List<SavedRoutePreset> {
    if (pinnedId.isNullOrBlank()) return presets
    val pinnedIndex = presets.indexOfFirst { it.id == pinnedId }
    if (pinnedIndex <= 0) return presets
    return listOf(presets[pinnedIndex]) + presets.filterIndexed { index, _ -> index != pinnedIndex }
}
