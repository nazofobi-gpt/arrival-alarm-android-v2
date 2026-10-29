package com.nazofobi.arrivalalarm

import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

/** Stop sequence is required: the same GTFS stop_id may occur twice on a loop. */
data class GtfsSelectedStopRef(val sequence: Int, val stopId: String)

data class GtfsSelectedTrip(
    val identity: GtfsTripInstanceIdentity,
    val boarding: GtfsSelectedStopRef,
    val destination: GtfsSelectedStopRef,
    val alarmTarget: GtfsSelectedStopRef,
)

enum class GtfsSelectionRestoreProblem {
    CORRUPT_SNAPSHOT,
    INVALID_STOP_ROLES,
    TRIP_UNAVAILABLE,
    STATIC_TRUTH_CHANGED,
    LEGACY_SNAPSHOT_REQUIRES_RESELECTION,
}

sealed interface GtfsSelectionRestore {
    object Empty : GtfsSelectionRestore
    data class Verified(val selection: GtfsSelectedTrip, val detail: GtfsTripDetail) : GtfsSelectionRestore
    data class Rejected(val reason: GtfsSelectionRestoreProblem) : GtfsSelectionRestore
}

/**
 * Persists *only* a user-confirmed concrete scheduled trip identity and its explicit stop roles.
 * On every restore the current GTFS service-day/trip and ordered stop sequence must agree.
 * A stale/corrupt snapshot remains unavailable, never silently resolves to another departure
 * and never arms an alarm. Integration with the trip-selection UI is a separate acceptance step.
 */
class SelectedGtfsTripStore(
    private val preferences: SharedPreferences,
    private val resolveTrip: (GtfsTripInstanceIdentity) -> GtfsTripDetailResult,
) {
    fun save(selection: GtfsSelectedTrip): Boolean {
        // A snapshot cannot fit if its raw identity and stop references already
        // exceed the persistence bound. Avoid resolving an impossible selection.
        val rawReferenceChars = listOf(
            selection.identity.tripId,
            selection.identity.serviceId,
            selection.identity.routeId,
            selection.boarding.stopId,
            selection.destination.stopId,
            selection.alarmTarget.stopId,
        ).sumOf { it.length.toLong() }
        if (rawReferenceChars > MAX_SNAPSHOT_CHARS) return false
        val verified = validate(selection) as? GtfsSelectionRestore.Verified ?: return false
        return try {
            // Snapshot the exact static timetable validated when confirming the trip.
            val snapshot = encode(selection, scheduleFingerprint(verified.detail)).toString()
            // A successful save must always be small enough to restore safely.
            if (snapshot.length > MAX_SNAPSHOT_CHARS) return false
            preferences.edit().putString(KEY, snapshot).commit()
        } catch (_: RuntimeException) {
            false
        }
    }

    fun restore(): GtfsSelectionRestore {
        val raw = try {
            if (!preferences.contains(KEY)) return GtfsSelectionRestore.Empty
            preferences.getString(KEY, null)
        } catch (_: RuntimeException) {
            return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT)
        } ?: return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT)
        // Reject oversized local snapshots before any JSON allocation or parsing.
        if (raw.length > MAX_SNAPSHOT_CHARS) {
            return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT)
        }
        // Earlier snapshots did not fingerprint all physical stop coordinates and
        // pickup/drop-off rules. They cannot safely inherit the user's approval.
        val version = try { JSONObject(raw).opt("version") as? Int } catch (_: JSONException) { null }
        if (version == 1 || version == 2 || version == 3) {
            return GtfsSelectionRestore.Rejected(
                GtfsSelectionRestoreProblem.LEGACY_SNAPSHOT_REQUIRES_RESELECTION
            )
        }
        val saved = decode(raw)
            ?: return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.CORRUPT_SNAPSHOT)
        val verification = validate(saved.selection)
        if (verification !is GtfsSelectionRestore.Verified) return verification
        return if (scheduleFingerprint(verification.detail) == saved.fingerprint) {
            verification
        } else {
            GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED)
        }
    }

    fun clear(): Boolean = try {
        preferences.edit().remove(KEY).commit()
    } catch (_: RuntimeException) {
        false
    }

    private fun validate(selection: GtfsSelectedTrip): GtfsSelectionRestore {
        val boarding = selection.boarding
        val destination = selection.destination
        val alarm = selection.alarmTarget
        // GTFS trip, service and route IDs are required identity components.
        // A permissive/mocked resolver must not authorize an identity-less trip.
        if (listOf(selection.identity.tripId, selection.identity.serviceId,
                selection.identity.routeId).any { it.isBlank() }
        ) return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.INVALID_STOP_ROLES)
        if (selection.identity.frequencyStartSeconds != null ||
            listOf(boarding, destination, alarm).any { it.sequence < 0 || it.stopId.isBlank() } ||
            boarding.sequence >= destination.sequence ||
            alarm.sequence < boarding.sequence || alarm.sequence > destination.sequence
        ) return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.INVALID_STOP_ROLES)

        val resolved = try {
            resolveTrip(selection.identity)
        } catch (_: RuntimeException) {
            return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.TRIP_UNAVAILABLE)
        }
        if (resolved !is GtfsTripDetailResult.Available) {
            return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.TRIP_UNAVAILABLE)
        }
        val detail = resolved.detail
        // A reused trip_id, a changed route/service/direction or another service date
        // must never inherit the previously confirmed boarding/arrival/alarm roles.
        if (detail.identity != selection.identity ||
            detail.stops.size < 2 ||
            detail.stops.any { it.stopSequence < 0 || it.stopId.isBlank() } ||
            detail.stops.zipWithNext().any { (a, b) -> a.stopSequence >= b.stopSequence }
        ) return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED)

        // The persistence boundary must enforce the same physical stop validity and
        // GTFS pickup/drop-off restrictions as the interactive role selector.
        // Otherwise a caller could save an impossible boarding or arrival stop.
        val roleCheck = TripStopRoleValidator.validate(
            detail,
            TripStopRoles(boarding.sequence, destination.sequence, alarm.sequence),
        )
        if (roleCheck !is TripStopRoleResult.Accepted) {
            return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.INVALID_STOP_ROLES)
        }

        fun matches(reference: GtfsSelectedStopRef): Boolean =
            detail.stops.singleOrNull { it.stopSequence == reference.sequence }?.stopId == reference.stopId
        if (!matches(boarding) || !matches(destination) || !matches(alarm)) {
            return GtfsSelectionRestore.Rejected(GtfsSelectionRestoreProblem.STATIC_TRUTH_CHANGED)
        }
        return GtfsSelectionRestore.Verified(selection, detail)
    }

    private data class SavedSnapshot(val selection: GtfsSelectedTrip, val fingerprint: String)

    /**
     * Length-framed SHA-256 of every ordered GTFS stop's timetable and physical
     * boarding/display metadata. Explicit nulls/lengths prevent concatenation
     * ambiguity. Identity is separately verified against the current GTFS graph.
     */
    private fun scheduleFingerprint(detail: GtfsTripDetail): String {
        val hash = MessageDigest.getInstance("SHA-256")
        fun part(value: String?) {
            val bytes = value?.toByteArray(Charsets.UTF_8)
            val size = bytes?.size ?: -1
            hash.update(byteArrayOf(
                (size ushr 24).toByte(), (size ushr 16).toByte(),
                (size ushr 8).toByte(), size.toByte(),
            ))
            if (bytes != null) hash.update(bytes)
        }
        part(detail.headsign)
        part(detail.stops.size.toString())
        for (stop in detail.stops) {
            part(stop.stopSequence.toString())
            part(stop.stopId)
            part(stop.scheduledArrival)
            part(stop.scheduledDeparture)
            part(stop.name)
            part(stop.parentStationId)
            part(stop.platformCode)
            part(stop.wheelchairBoarding?.toString())
            part(stop.latitude.toBits().toString())
            part(stop.longitude.toBits().toString())
            part(stop.pickupType?.toString())
            part(stop.dropOffType?.toString())
        }
        val hex = "0123456789abcdef"
        return buildString {
            for (byte in hash.digest()) {
                val unsigned = byte.toInt() and 0xff
                append(hex[unsigned ushr 4])
                append(hex[unsigned and 0x0f])
            }
        }
    }

    private fun encode(value: GtfsSelectedTrip, fingerprint: String): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("schedule_fingerprint", fingerprint)
        put("trip_id", value.identity.tripId)
        put("service_date", value.identity.serviceDate.toString())
        put("service_id", value.identity.serviceId)
        put("route_id", value.identity.routeId)
        put("direction_id", value.identity.directionId ?: JSONObject.NULL)
        put("boarding", encodeRef(value.boarding))
        put("destination", encodeRef(value.destination))
        put("alarm", encodeRef(value.alarmTarget))
    }

    private fun encodeRef(ref: GtfsSelectedStopRef) = JSONObject().apply {
        put("sequence", ref.sequence)
        put("stop_id", ref.stopId)
    }

    private fun decode(raw: String): SavedSnapshot? {
        return try {
        val json = JSONObject(raw)
        if (json.intStrict("version") != VERSION) return null
        val fingerprint = json.stringStrict("schedule_fingerprint") ?: return null
        if (!FINGERPRINT_REGEX.matches(fingerprint)) return null
        val direction = when (val value = json.opt("direction_id")) {
            JSONObject.NULL -> null
            is Int -> value
            else -> return null
        }
        val identity = GtfsTripInstanceIdentity(
            tripId = json.stringStrict("trip_id") ?: return null,
            serviceDate = GtfsServiceDate.parse(json.stringStrict("service_date") ?: return null),
            serviceId = json.stringStrict("service_id") ?: return null,
            routeId = json.stringStrict("route_id") ?: return null,
            directionId = direction,
        )
        SavedSnapshot(
            GtfsSelectedTrip(
                identity,
                ref(json.optJSONObject("boarding")) ?: return null,
                ref(json.optJSONObject("destination")) ?: return null,
                ref(json.optJSONObject("alarm")) ?: return null,
            ),
            fingerprint,
        )
        } catch (_: JSONException) {
            // Android's org.json.JSONException is a checked Exception, not a RuntimeException.
            // Corrupted persisted JSON must produce an explicit rejected state.
            null
        } catch (_: RuntimeException) {
            // Invalid GTFS dates and identity validation reject only this snapshot.
            null
        }
    }

    private fun ref(value: JSONObject?): GtfsSelectedStopRef? {
        val data = value ?: return null
        val sequence = data.intStrict("sequence") ?: return null
        val id = data.stringStrict("stop_id") ?: return null
        return GtfsSelectedStopRef(sequence, id)
    }

    private fun JSONObject.intStrict(key: String): Int? = opt(key) as? Int
    private fun JSONObject.stringStrict(key: String): String? =
        (opt(key) as? String)?.takeIf { it.isNotBlank() }

    companion object {
        internal const val KEY = "selected_gtfs_trip_v1"
        private const val VERSION = 4
        // Bounds malformed/corrupt SharedPreferences payloads without constraining normal IDs.
        private const val MAX_SNAPSHOT_CHARS = 65_536
        private val FINGERPRINT_REGEX = Regex("^[a-f0-9]{64}$")
    }
}
