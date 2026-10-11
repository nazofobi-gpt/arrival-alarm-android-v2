package com.nazofobi.arrivalalarm

/** A concrete GTFS departure must retain its service date and ordered stops. */
data class ConcreteTripStop(val stop: GtfsScheduleStop, val sequence: Int)
