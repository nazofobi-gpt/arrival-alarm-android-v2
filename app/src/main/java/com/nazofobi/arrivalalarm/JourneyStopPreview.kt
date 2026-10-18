package com.nazofobi.arrivalalarm

/**
 * In long journeys the collapsed stop list must retain the destination.
 * Indices refer to the original stop sequence, never a synthetic route.
 * Expanding exposes the complete provider-backed ordered list.
 */
internal const val COLLAPSED_JOURNEY_STOP_LIMIT = 16

internal fun journeyStopPreviewIndices(stopCount: Int, expanded: Boolean): List<Int> {
    if (stopCount <= 0) return emptyList()
    if (expanded || stopCount <= COLLAPSED_JOURNEY_STOP_LIMIT) {
        return (0 until stopCount).toList()
    }
    return (0 until COLLAPSED_JOURNEY_STOP_LIMIT - 1).toList() + (stopCount - 1)
}
