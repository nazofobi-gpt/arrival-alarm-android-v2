package com.nazofobi.arrivalalarm

/**
 * A GTFS stop's departure board is a first-class discovery surface.
 * It must not disappear simply because journey routing timed out or found no path.
 */
internal fun shouldDisplayDepartureBoard(
    hasDestination: Boolean,
    hasSelectedStop: Boolean,
    hasRouteOptions: Boolean,
): Boolean = hasSelectedStop || (hasDestination && hasRouteOptions)
