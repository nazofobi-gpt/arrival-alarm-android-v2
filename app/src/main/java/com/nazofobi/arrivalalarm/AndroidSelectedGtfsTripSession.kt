package com.nazofobi.arrivalalarm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Binds explicitly confirmed GTFS trip/stop roles to the installed SQLite schedule.
 * No route label, display time or guessed service day can create a selection.
 */

/**
 * Source-verified, read-only step between choosing a station-board row and
 * explicitly confirming boarding, destination and alarm-target roles.
 */
enum class GtfsDeparturePreviewProblem {
    INCOMPLETE_OR_CANCELLED_DEPARTURE,
    TRIP_UNAVAILABLE,
    BOARDING_STOP_MISMATCH,
    NO_ALLOWED_DESTINATION,
    SESSION_CLOSED,
}

sealed interface GtfsDeparturePreview {
    data class Rejected(val problem: GtfsDeparturePreviewProblem) : GtfsDeparturePreview

    data class Available(
        val detail: GtfsTripDetail,
        val boarding: GtfsTripDetailStop,
        val destinations: List<GtfsTripDetailStop>,
    ) : GtfsDeparturePreview {
        /**
         * Called only after the user explicitly chooses the destination/target.
         * Persist through confirm(), which re-resolves live static truth.
         */
        fun selectionFor(
            destinationSequence: Int,
            alarmTargetSequence: Int = destinationSequence,
        ): GtfsSelectedTrip? {
            val result = TripStopRoleValidator.validate(
                detail,
                TripStopRoles(boarding.stopSequence, destinationSequence, alarmTargetSequence),
            ) as? TripStopRoleResult.Accepted ?: return null
            val roles = result.roles
            return GtfsSelectedTrip(
                identity = roles.identity,
                boarding = GtfsSelectedStopRef(roles.boarding.stopSequence, roles.boarding.stopId),
                destination = GtfsSelectedStopRef(roles.destination.stopSequence, roles.destination.stopId),
                alarmTarget = GtfsSelectedStopRef(roles.alarmTarget.stopSequence, roles.alarmTarget.stopId),
            )
        }
    }
}

class AndroidSelectedGtfsTripSession(context: Context) : AutoCloseable {
    private val application = context.applicationContext
    private val index = NationwideTransitIndex(application)
    private val mutex = Any()
    private var closed = false
    private val resolver = GtfsTripDetailResolver(GtfsScheduleRepository(index))
    private val store = SelectedGtfsTripStore(
        application.getSharedPreferences("selected_gtfs_trip_state", Context.MODE_PRIVATE)
    ) { identity ->
        if (!index.isReady()) {
            GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.TRIP_NOT_FOUND)
        } else {
            resolver.resolve(identity)
        }
    }


    /**
     * Never infer a service date, an origin or a destination from a board label.
     * A station-level row must match an exact GTFS stop_id + stop_sequence;
     * loop routes and frequency templates cannot silently select another stop.
     * This method is read-only. Neither trip persistence nor alarm arming occurs.
     */
    suspend fun preview(
        departure: Departure,
        boardingStopId: String,
    ): GtfsDeparturePreview = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            if (closed) return@synchronized GtfsDeparturePreview.Rejected(
                GtfsDeparturePreviewProblem.SESSION_CLOSED
            )
            val identity = departure.gtfsInstance
            val boardingSequence = departure.boardingStopSequence
            if (departure.cancelled || departure.scheduledEpochSeconds <= 0L ||
                identity == null || boardingSequence == null || boardingSequence < 0 ||
                boardingStopId.isBlank() || identity.frequencyStartSeconds != null ||
                departure.realtimeTripStartDate != null || departure.realtimeTripStartTime != null ||
                departure.tripId != identity.tripId || departure.routeId != identity.routeId
            ) return@synchronized GtfsDeparturePreview.Rejected(
                GtfsDeparturePreviewProblem.INCOMPLETE_OR_CANCELLED_DEPARTURE
            )

            val result = try {
                if (index.isReady()) resolver.resolve(identity)
                else GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.INDEX_NOT_READY)
            } catch (_: RuntimeException) {
                GtfsTripDetailResult.Unavailable(GtfsTripDetailFailure.DATA_READ_FAILED)
            }
            val detail = (result as? GtfsTripDetailResult.Available)?.detail
                ?: return@synchronized GtfsDeparturePreview.Rejected(
                    GtfsDeparturePreviewProblem.TRIP_UNAVAILABLE
                )
            if (detail.identity != identity) return@synchronized GtfsDeparturePreview.Rejected(
                GtfsDeparturePreviewProblem.TRIP_UNAVAILABLE
            )
            val boarding = detail.stops.singleOrNull {
                it.stopSequence == boardingSequence && it.stopId == boardingStopId
            } ?: return@synchronized GtfsDeparturePreview.Rejected(
                GtfsDeparturePreviewProblem.BOARDING_STOP_MISMATCH
            )
            val destinations = detail.stops.filter { destination ->
                destination.stopSequence > boardingSequence &&
                    TripStopRoleValidator.validate(
                        detail,
                        TripStopRoles(
                            boardingSequence,
                            destination.stopSequence,
                            destination.stopSequence,
                        ),
                    ) is TripStopRoleResult.Accepted
            }
            if (destinations.isEmpty()) return@synchronized GtfsDeparturePreview.Rejected(
                GtfsDeparturePreviewProblem.NO_ALLOWED_DESTINATION
            )
            GtfsDeparturePreview.Available(detail, boarding, destinations)
        }
    }

    suspend fun confirm(selection: GtfsSelectedTrip): Boolean = withContext(Dispatchers.IO) {
        synchronized(mutex) { !closed && store.save(selection) }
    }

    suspend fun restore(): GtfsSelectionRestore = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            if (closed) {
                GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.TRIP_UNAVAILABLE)
            } else {
                store.restore()
            }
        }
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        synchronized(mutex) { !closed && store.clear() }
    }

    override fun close() {
        synchronized(mutex) {
            if (!closed) {
                closed = true
                index.close()
            }
        }
    }
}
