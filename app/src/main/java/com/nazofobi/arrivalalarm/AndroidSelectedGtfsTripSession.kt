package com.nazofobi.arrivalalarm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A departure-board tap may preview a concrete GTFS trip, but it is not consent
 * to save stop roles or arm an arrival alarm.
 */
enum class GtfsDepartureInspectionFailure {
    SESSION_CLOSED,
    CANCELLED_DEPARTURE,
    SOURCE_IDENTITY_MISSING,
    SOURCE_EVIDENCE_MISMATCH,
    TRIP_UNAVAILABLE,
    BOARDING_NOT_ALLOWED,
    NO_DOWNSTREAM_ALIGHTING_STOP,
}

sealed interface GtfsDepartureInspection {
    data class Ready(
        val detail: GtfsTripDetail,
        val boarding: GtfsTripDetailStop,
    ) : GtfsDepartureInspection

    data class Rejected(val reason: GtfsDepartureInspectionFailure) : GtfsDepartureInspection
}

/**
 * Binds explicitly confirmed GTFS trip/stop roles to the installed SQLite schedule.
 * No route label, display time or guessed service day can create a selection.
 */
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
     * Preview only the exact provider-backed departure at the explicitly selected stop_id.
     * Synthetic/frequency departures and stale/mismatched evidence fail closed.
     * This method has no persistence or journey/alarm side effects; the UI must
     * collect explicit destination and alarm-target consent before confirm().
     */
    suspend fun inspect(departure: Departure, expectedBoardingStopId: String): GtfsDepartureInspection =
        withContext(Dispatchers.IO) {
            synchronized(mutex) {
                fun reject(reason: GtfsDepartureInspectionFailure) =
                    GtfsDepartureInspection.Rejected(reason)

                if (closed) return@synchronized reject(GtfsDepartureInspectionFailure.SESSION_CLOSED)
                if (departure.cancelled) {
                    return@synchronized reject(GtfsDepartureInspectionFailure.CANCELLED_DEPARTURE)
                }

                val identity = departure.gtfsInstance
                    ?: return@synchronized reject(
                        GtfsDepartureInspectionFailure.SOURCE_IDENTITY_MISSING,
                    )
                val boardingSequence = departure.boardingStopSequence
                    ?: return@synchronized reject(
                        GtfsDepartureInspectionFailure.SOURCE_IDENTITY_MISSING,
                    )
                if (identity.frequencyStartSeconds != null ||
                    departure.tripId != identity.tripId ||
                    departure.routeId != identity.routeId ||
                    departure.scheduledEpochSeconds <= 0L ||
                    boardingSequence < 0 || expectedBoardingStopId.isBlank()
                ) {
                    return@synchronized reject(
                        GtfsDepartureInspectionFailure.SOURCE_EVIDENCE_MISMATCH,
                    )
                }

                val resolved = try {
                    if (index.isReady()) resolver.resolve(identity) else null
                } catch (_: RuntimeException) {
                    null
                }
                val detail = (resolved as? GtfsTripDetailResult.Available)?.detail
                    ?: return@synchronized reject(GtfsDepartureInspectionFailure.TRIP_UNAVAILABLE)
                if (detail.identity != identity) {
                    return@synchronized reject(
                        GtfsDepartureInspectionFailure.SOURCE_EVIDENCE_MISMATCH,
                    )
                }
                val boarding = detail.stops.singleOrNull {
                    it.stopSequence == boardingSequence && it.stopId == expectedBoardingStopId
                } ?: return@synchronized reject(
                    GtfsDepartureInspectionFailure.SOURCE_EVIDENCE_MISMATCH,
                )
                if (boarding.pickupType != null && boarding.pickupType != 0) {
                    return@synchronized reject(
                        GtfsDepartureInspectionFailure.BOARDING_NOT_ALLOWED,
                    )
                }
                // A boarding position is actionable only when the same verified
                // trip has a later stop where normal alighting is permitted.
                // This also rejects terminal/loop-end positions without
                // inventing a destination from a repeated stop name or ID.
                if (detail.stops.none { stop ->
                        stop.stopSequence > boardingSequence &&
                            (stop.dropOffType == null || stop.dropOffType == 0)
                    }) {
                    return@synchronized reject(
                        GtfsDepartureInspectionFailure.NO_DOWNSTREAM_ALIGHTING_STOP,
                    )
                }

                GtfsDepartureInspection.Ready(detail, boarding)
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
