package com.nazofobi.arrivalalarm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * Preview of a specific source-backed departure for a subsequent explicit stop-role choice.
 * Reading a preview never persists a selection or starts a journey/alarm.
 */
sealed interface DepartureTripPreview {
    data class Available(val detail: GtfsTripDetail, val boarding: GtfsTripDetailStop) : DepartureTripPreview
    data class Unavailable(val reason: Reason) : DepartureTripPreview

    enum class Reason {
        SESSION_CLOSED,
        MISSING_SOURCE_IDENTITY,
        SOURCE_MISMATCH,
        INDEX_UNAVAILABLE,
        TRIP_UNAVAILABLE,
        BOARDING_UNAVAILABLE,
    }
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
     * Resolve the exact scheduled GTFS trip + service day carried by this station board row.
     * Never manufacture a service date from the UI clock or a route display label.
     * The caller must separately choose/confirm destination and alarm-stop roles.
     */
    suspend fun preview(departure: Departure): DepartureTripPreview = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            fun reject(reason: DepartureTripPreview.Reason) = DepartureTripPreview.Unavailable(reason)
            if (closed) return@synchronized reject(DepartureTripPreview.Reason.SESSION_CLOSED)
            val identity = departure.gtfsInstance
                ?: return@synchronized reject(DepartureTripPreview.Reason.MISSING_SOURCE_IDENTITY)
            val boardingSequence = departure.boardingStopSequence
                ?: return@synchronized reject(DepartureTripPreview.Reason.MISSING_SOURCE_IDENTITY)
            if (boardingSequence < 0 || identity.frequencyStartSeconds != null ||
                departure.tripId != identity.tripId ||
                (departure.routeId != null && departure.routeId != identity.routeId)
            ) return@synchronized reject(DepartureTripPreview.Reason.SOURCE_MISMATCH)
            if (!index.isReady()) {
                return@synchronized reject(DepartureTripPreview.Reason.INDEX_UNAVAILABLE)
            }
            val resolved = try {
                resolver.resolve(identity)
            } catch (_: RuntimeException) {
                return@synchronized reject(DepartureTripPreview.Reason.TRIP_UNAVAILABLE)
            }
            if (resolved !is GtfsTripDetailResult.Available ||
                resolved.detail.identity != identity
            ) return@synchronized reject(DepartureTripPreview.Reason.TRIP_UNAVAILABLE)

            val boarding = resolved.detail.stops.singleOrNull {
                it.stopSequence == boardingSequence
            } ?: return@synchronized reject(DepartureTripPreview.Reason.BOARDING_UNAVAILABLE)
            if (boarding.stopId.isBlank() || boarding.name.isBlank() ||
                (boarding.pickupType != null && boarding.pickupType != 0) ||
                !boarding.latitude.isFinite() || !boarding.longitude.isFinite() ||
                boarding.latitude !in -90.0..90.0 || boarding.longitude !in -180.0..180.0
            ) return@synchronized reject(DepartureTripPreview.Reason.BOARDING_UNAVAILABLE)

            DepartureTripPreview.Available(resolved.detail, boarding)
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
