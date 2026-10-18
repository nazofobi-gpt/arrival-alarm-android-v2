package com.nazofobi.arrivalalarm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
