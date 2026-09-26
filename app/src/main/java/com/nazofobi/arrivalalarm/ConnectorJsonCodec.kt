package com.nazofobi.arrivalalarm

import org.json.JSONObject

/**
 * Stable JSON wire format between the Android client and the remote connector gateway.
 * Chat text is intentionally not part of this protocol.
 */
object ConnectorJsonCodec {
    fun encodeSnapshot(value: ArrivalAlarmConnectorSnapshot): String {
        val root = JSONObject()
            .put("schema_version", value.schemaVersion)
            .put("state_version", value.stateVersion)
            .put("captured_at_epoch_seconds", value.capturedAtEpochSeconds)
            .put("source_updated_at_epoch_seconds", value.sourceUpdatedAtEpochSeconds)
            .put("stale_after_seconds", value.staleAfterSeconds)
            .put("source", value.source)
            .put("journey_phase", value.journeyPhase.name)
            .put("alarm_armed", value.alarmArmed)

        value.distanceToDestinationMeters?.let { root.put("distance_to_destination_meters", it) }
        value.activeTrip?.let { root.put("active_trip", encodeTrip(it)) }
        return root.toString()
    }

    fun encodeReceipt(value: ConnectorCommandReceipt): String =
        JSONObject()
            .put("idempotency_key", value.idempotencyKey)
            .put("command_type", value.commandType)
            .put("status", value.status.name)
            .put("message", value.message)
            .put("state_version_before", value.stateVersionBefore)
            .put("state_version_after", value.stateVersionAfter)
            .put("duplicate", value.duplicate)
            .apply { value.actionId?.let { put("action_id", it) } }
            .toString()

    fun decodeCommand(json: String): ArrivalAlarmConnectorCommand =
        decodeCommand(JSONObject(json))

    fun decodePendingCommands(json: String): List<ConnectorQueuedCommand> {
        val root = JSONObject(json)
        val values = root.getJSONArray("commands")
        return buildList(values.length()) {
            for (index in 0 until values.length()) {
                val envelope = values.getJSONObject(index)
                add(
                    ConnectorQueuedCommand(
                        commandId = envelope.requiredNonBlank("command_id"),
                        expectedStateVersion = envelope.getLong("expected_state_version"),
                        userConfirmed = envelope.optBoolean("user_confirmed", false),
                        queuedAtEpochSeconds = if (envelope.has("queued_at_epoch_seconds")) {
                            envelope.getLong("queued_at_epoch_seconds")
                        } else {
                            null
                        },
                        command = decodeCommand(envelope.getJSONObject("command")),
                    )
                )
            }
        }
    }

    private fun decodeCommand(root: JSONObject): ArrivalAlarmConnectorCommand {
        val type = root.requiredNonBlank("type")
        val key = root.requiredNonBlank("idempotency_key")
        return when (type) {
            "set_origin" -> ArrivalAlarmConnectorCommand.SetOrigin(root.requiredPoint("point"), key)
            "set_destination" -> ArrivalAlarmConnectorCommand.SetDestination(root.requiredPoint("point"), key)
            "select_journey" -> ArrivalAlarmConnectorCommand.SelectJourney(root.requiredNonBlank("route_id"), key)
            "set_boarding_stop" -> ArrivalAlarmConnectorCommand.SetBoardingStop(
                root.getJSONObject("stop").decodeStop(),
                key,
            )
            "arm_arrival_alarm" -> ArrivalAlarmConnectorCommand.ArmArrivalAlarm(key)
            "cancel_arrival_alarm" -> ArrivalAlarmConnectorCommand.CancelArrivalAlarm(key)
            else -> error("Unsupported connector command: $type")
        }
    }

    private fun encodeTrip(value: ConnectorTripState): JSONObject =
        JSONObject().apply {
            value.journeyId?.let { put("journey_id", it) }
            value.tripId?.let { put("trip_id", it) }
            value.routeId?.let { put("route_id", it) }
            value.line?.let { put("line", it) }
            value.direction?.let { put("direction", it) }
            value.origin?.let { put("origin", encodeStop(it)) }
            value.destination?.let { put("destination", encodeStop(it)) }
            value.boardingStop?.let { put("boarding_stop", encodeStop(it)) }
            value.previousStop?.let { put("previous_stop", encodeStop(it)) }
            value.currentStop?.let { put("current_stop", encodeStop(it)) }
            value.nextStop?.let { put("next_stop", encodeStop(it)) }
        }

    private fun encodeStop(value: ConnectorStopState): JSONObject =
        JSONObject().apply {
            value.id?.let { put("id", it) }
            put("name", value.name)
            value.latitude?.let { put("latitude", it) }
            value.longitude?.let { put("longitude", it) }
        }

    private fun JSONObject.requiredPoint(name: String): MapPoint {
        val point = getJSONObject(name)
        return MapPoint(
            latitude = point.getDouble("latitude"),
            longitude = point.getDouble("longitude"),
            label = point.requiredNonBlank("label"),
        )
    }

    private fun JSONObject.decodeStop(): ConnectorStopState =
        ConnectorStopState(
            id = optString("id").takeIf { it.isNotBlank() },
            name = requiredNonBlank("name"),
            latitude = if (has("latitude")) getDouble("latitude") else null,
            longitude = if (has("longitude")) getDouble("longitude") else null,
        )

    private fun JSONObject.requiredNonBlank(name: String): String =
        getString(name).trim().also { require(it.isNotEmpty()) { "$name must not be blank" } }
}
