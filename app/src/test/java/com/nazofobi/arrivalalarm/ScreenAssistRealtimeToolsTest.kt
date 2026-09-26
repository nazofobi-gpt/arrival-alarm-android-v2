package com.nazofobi.arrivalalarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistRealtimeToolsTest {
    private class FakeGateway : ScreenAssistContextReader, ScreenAssistCommandGateway {
        val applied = mutableListOf<ScreenAssistCommand>()

        override fun read() = ScreenAssistAppContext(
            location = ScreenAssistLocation(52.666, 8.238, 1234L, "device"),
            journey = ScreenAssistJourneyContext(
                journeyId = "j-1",
                line = "RE 9",
                tripId = "trip-9",
                boardingStop = ScreenAssistStop("loh", "Lohne", 52.666, 8.238),
                targetStop = ScreenAssistStop("bre", "Bremen Hbf", 53.083, 8.813),
                etaEpochMs = 9999L,
                realtime = true,
                serviceAlert = null,
                freshnessEpochMs = 1234L,
                source = "gtfs+rt",
            ),
        )

        override fun validate(command: ScreenAssistCommand): Boolean = when (command) {
            is ScreenAssistCommand.FillSearch -> command.query.length >= 2
            else -> true
        }

        override fun apply(command: ScreenAssistCommand): ScreenAssistCommandResult {
            applied += command
            return ScreenAssistCommandResult(true, false, "applied")
        }
    }

    @Test fun sessionUpdateDeclaresOnlyAppOwnedFunctionTools() {
        val gateway = FakeGateway()
        val root = JSONObject(ScreenAssistRealtimeToolHandler(gateway, gateway).sessionUpdateEvent())
        assertEquals("session.update", root.getString("type"))
        val session = root.getJSONObject("session")
        assertEquals("realtime", session.getString("type"))
        assertEquals("auto", session.getString("tool_choice"))
        val tools = session.getJSONArray("tools")
        assertEquals(2, tools.length())
        assertEquals("read_app_context", tools.getJSONObject(0).getString("name"))
        assertEquals("apply_app_command", tools.getJSONObject(1).getString("name"))
    }

    @Test fun readContextReturnsTypedCurrentState() {
        val gateway = FakeGateway()
        val handler = ScreenAssistRealtimeToolHandler(gateway, gateway)
        val event = responseDone("read_app_context", "call-1", "{}")
        val outcome = handler.handleServerEvent(event).single() as ScreenAssistToolOutcome.Immediate
        val output = JSONObject(outcome.outputJson)
        assertEquals("device", output.getJSONObject("location").getString("source"))
        assertEquals("Bremen Hbf", output.getJSONObject("journey").getJSONObject("target_stop").getString("name"))
        assertEquals("call-1", outcome.callId)
    }

    @Test fun reversibleCommandExecutesWithoutHumanConfirmation() {
        val gateway = FakeGateway()
        val handler = ScreenAssistRealtimeToolHandler(gateway, gateway)
        val args = JSONObject().put("command", "fill_search").put("query", "Bremen").toString()
        val outcome = handler.handleServerEvent(responseDone("apply_app_command", "call-2", args)).single()
            as ScreenAssistToolOutcome.Immediate
        assertTrue(JSONObject(outcome.outputJson).getBoolean("applied"))
        assertEquals(ScreenAssistCommand.FillSearch("Bremen"), gateway.applied.single())
    }

    @Test fun alarmTargetIsHeldUntilHumanConfirms() {
        val gateway = FakeGateway()
        val handler = ScreenAssistRealtimeToolHandler(gateway, gateway)
        val args = JSONObject().put("command", "set_alarm_target").put("stop_id", "bre").toString()
        val pending = (
            handler.handleServerEvent(responseDone("apply_app_command", "call-3", args)).single()
                as ScreenAssistToolOutcome.AwaitConfirmation
            ).pending

        assertTrue(gateway.applied.isEmpty())
        val rejected = handler.resolvePending(pending, false)
        assertFalse(JSONObject(rejected.outputJson).getBoolean("applied"))
        assertEquals("user_rejected", JSONObject(rejected.outputJson).getString("message"))
        assertTrue(gateway.applied.isEmpty())

        val approved = handler.resolvePending(pending, true)
        assertTrue(JSONObject(approved.outputJson).getBoolean("applied"))
        assertEquals(ScreenAssistCommand.SetAlarmTarget("bre"), gateway.applied.single())
    }

    @Test fun invalidCommandFailsClosed() {
        val gateway = FakeGateway()
        val handler = ScreenAssistRealtimeToolHandler(gateway, gateway)
        val args = JSONObject().put("command", "set_destination").toString()
        val outcome = handler.handleServerEvent(responseDone("apply_app_command", "call-4", args)).single()
            as ScreenAssistToolOutcome.Immediate
        assertFalse(JSONObject(outcome.outputJson).getBoolean("applied"))
        assertEquals("invalid_command", JSONObject(outcome.outputJson).getString("message"))
        assertTrue(gateway.applied.isEmpty())
    }

    private fun responseDone(name: String, callId: String, arguments: String): String =
        JSONObject()
            .put("type", "response.done")
            .put(
                "response",
                JSONObject().put(
                    "output",
                    org.json.JSONArray().put(
                        JSONObject()
                            .put("type", "function_call")
                            .put("name", name)
                            .put("call_id", callId)
                            .put("arguments", arguments),
                    ),
                ),
            )
            .toString()
}
