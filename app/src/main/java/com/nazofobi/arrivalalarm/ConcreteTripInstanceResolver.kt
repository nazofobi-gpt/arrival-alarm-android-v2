package com.nazofobi.arrivalalarm

data class ConcreteTripStop(
    val stop: GtfsScheduleStop,
    val sequence: Int,
    val boardingAllowed: Boolean,
    val alightingAllowed: Boolean,
)

data class ConcreteTripStopSequence(
    val tripId: String,
    val routeId: String,
    val direction: String,
    val boardingSequence: Int,
    val stops: List<ConcreteTripStop>,
)

object ConcreteTripStopSequenceResolver {
    fun resolve(
        trip: GtfsScheduleTrip,
        rows: List<GtfsScheduleStopTime>,
        boardingStopId: String,
        stopById: (String) -> GtfsScheduleStop?,
    ): ConcreteTripStopSequence? {
        if (rows.size < 2 || rows.size >= 512) return null
        if (rows.zipWithNext().any { (a, b) -> a.stopSequence >= b.stopSequence }) return null
        val board = rows.firstOrNull { it.stopId == boardingStopId } ?: return null
        if (board.pickupType == 1) return null
        val stops = rows.map { row ->
            ConcreteTripStop(
                stopById(row.stopId) ?: return null,
                row.stopSequence,
                row.pickupType != 1,
                row.dropOffType != 1,
            )
        }
        return ConcreteTripStopSequence(
            trip.id, trip.routeId, trip.headsign.orEmpty(), board.stopSequence, stops,
        )
    }
}
