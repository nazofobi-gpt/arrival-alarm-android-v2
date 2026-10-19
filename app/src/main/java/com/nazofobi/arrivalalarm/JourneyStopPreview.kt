package com.nazofobi.arrivalalarm

/**
 * In long journeys the collapsed stop list must retain the destination.
 * Indices refer to the original stop sequence, never a synthetic route.
 * Expanding exposes the complete provider-backed ordered list.
 */
internal const val COLLAPSED_JOURNEY_STOP_LIMIT = 16

/**
 * Do not allocate one boxed index per stop when a provider supplies a very
 * long route. The UI only needs random access to the original stop indices.
 */
private class JourneyStopIndexView(override val size: Int) : AbstractList<Int>() {
    override fun get(index: Int): Int {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Stop index: $index, size: $size")
        return index
    }
}

internal fun journeyStopPreviewIndices(stopCount: Int, expanded: Boolean): List<Int> {
    if (stopCount <= 0) return emptyList()
    if (expanded || stopCount <= COLLAPSED_JOURNEY_STOP_LIMIT) {
        return JourneyStopIndexView(stopCount)
    }
    return (0 until COLLAPSED_JOURNEY_STOP_LIMIT - 1).toList() + (stopCount - 1)
}
