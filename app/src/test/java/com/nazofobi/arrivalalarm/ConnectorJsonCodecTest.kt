package com.nazofobi.arrivalalarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorJsonCodecTest {
    @Test fun snapshotJsonCarriesOnlyTypedJourneyState() {
        val json = ConnectorJsonCodec.encodeSnapshot(
            ArrivalAlarmConnectorSnapshot(
                stateVersion = 12,
                capturedAtEpochSeconds = 1_000,
                sourceUpdatedAtEpochSeconds = 999,
                source = "arrival-alarm-domain",
                journeyPhase = JourneyPhase.ARMED,
                activeTrip = ConnectorTripState(
                    tripId = "trip-1",
                    routeId = "route-1",
                    line = "RE 9",
                    direction = "Bremen Hbf",
                    previousStop = ConnectorStopState(id = "a", name = "Diepholz"),
                    currentStop = ConnectorStopState(id = "b", name = "Barnstorf"),
                    nextStop = ConnectorStopState(id = "c", name = "Twistringen"),
                    timingBasis = "REALTIME",
                    progressSource = "transport.rest-stopovers",
                    progressUpdatedAtEpochSeconds = 998,
                    scheduledArrivalEpochSeconds = 1_100,
                    estimatedArrivalEpochSeconds = 1_120,
                ),
                distanceToDestinationMeters = 4_500.0,
                alarmArmed = true,
            )
        )
        val root = JSONObject(json)

        assertEquals(1, root.getInt("schema_version"))
        assertEquals(12, root.getLong("state_version"))
        assertEquals("ARMED", root.getString("journey_phase"))
        assertTrue(root.getBoolean("alarm_armed"))
        val trip = root.getJSONObject("active_trip")
        assertEquals("Twistringen", trip.getJSONObject("next_stop").getString("name"))
        assertEquals("REALTIME", trip.getString("timing_basis"))
        assertEquals("transport.rest-stopovers", trip.getString("progress_source"))
        assertEquals(998, trip.getLong("progress_updated_at_epoch_seconds"))
        assertEquals(1_100, trip.getLong("scheduled_arrival_epoch_seconds"))
        assertEquals(1_120, trip.getLong("estimated_arrival_epoch_seconds"))
        assertFalse(root.has("prompt"))
        assertFalse(root.has("chat"))
        assertFalse(root.has("token"))
    }

    @Test fun decoderBuildsAllowListedDestinationCommand() {
        val command = ConnectorJsonCodec.decodeCommand(
            """
            {
              "type":"set_destination",
              "idempotency_key":"cmd-42",
              "point":{"latitude":53.0834,"longitude":8.8137,"label":"Bremen Hbf"}
            }
            """.trimIndent()
        )

        val destination = command as ArrivalAlarmConnectorCommand.SetDestination
        assertEquals("cmd-42", destination.idempotencyKey)
        assertEquals("Bremen Hbf", destination.point.label)
        assertEquals(53.0834, destination.point.latitude, 0.0001)
    }

    @Test fun decoderRejectsUnknownCommandType() {
        val error = runCatching {
            ConnectorJsonCodec.decodeCommand(
                """{"type":"raw_sql","idempotency_key":"bad"}"""
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test fun receiptJsonPreservesIdempotencyEvidence() {
        val root = JSONObject(
            ConnectorJsonCodec.encodeReceipt(
                ConnectorCommandReceipt(
                    idempotencyKey = "cmd-arm",
                    commandType = "ArmArrivalAlarm",
                    status = ConnectorCommandStatus.APPLIED,
                    message = "Varış alarmı kuruldu",
                    stateVersionBefore = 5,
                    stateVersionAfter = 6,
                    actionId = "action-1",
                    duplicate = true,
                )
            )
        )

        assertEquals("cmd-arm", root.getString("idempotency_key"))
        assertEquals("APPLIED", root.getString("status"))
        assertTrue(root.getBoolean("duplicate"))
        assertEquals(6, root.getLong("state_version_after"))
    }
}
