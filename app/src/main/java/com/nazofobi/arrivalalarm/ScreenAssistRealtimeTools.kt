package com.nazofobi.arrivalalarm

import org.json.JSONArray
import org.json.JSONObject

data class ScreenAssistPendingToolCall(
    val callId: String,
    val command: ScreenAssistCommand,
)

sealed class ScreenAssistToolOutcome {
    data class Immediate(val callId: String, val outputJson: String) : ScreenAssistToolOutcome()
    data class AwaitConfirmation(val pending: ScreenAssistPendingToolCall) : ScreenAssistToolOutcome()
}

/**
 * Realtime function-tool adapter for app-owned context and commands.
 *
 * Reversible commands may be executed immediately. SelectJourney and
 * SetAlarmTarget are held until the human explicitly confirms them in the app.
 */
class ScreenAssistRealtimeToolHandler(
    private val contextReader: ScreenAssistContextReader,
    private val commandGateway: ScreenAssistCommandGateway,
    private val commandPolicy: ScreenAssistCommandPolicy = ScreenAssistCommandPolicy(),
) {
    fun sessionUpdateEvent(): String {
        val readTool = JSONObject()
            .put("type", "function")
            .put("name", "read_app_context")
            .put(
                "description",
                "Read the app's current device-location and transit-journey context. " +
                    "Use this instead of guessing app state from the captured screen.",
            )
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject())
                    .put("additionalProperties", false),
            )

        val applyTool = JSONObject()
            .put("type", "function")
            .put("name", "apply_app_command")
            .put(
                "description",
                "Request a typed action inside this app only. Never use this to control another app. " +
                    "select_journey and set_alarm_target require explicit human confirmation.",
            )
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put(
                                "command",
                                JSONObject()
                                    .put("type", "string")
                                    .put(
                                        "enum",
                                        JSONArray(
                                            listOf(
                                                "set_origin",
                                                "set_destination",
                                                "set_boarding_stop",
                                                "select_journey",
                                                "fill_search",
                                                "request_route",
                                                "set_alarm_target",
                                            )
                                        ),
                                    ),
                            )
                            .put("stop_id", JSONObject().put("type", "string"))
                            .put("journey_id", JSONObject().put("type", "string"))
                            .put("query", JSONObject().put("type", "string"))
                            .put("origin_stop_id", JSONObject().put("type", arrayOf("string", "null")))
                            .put("destination_stop_id", JSONObject().put("type", "string")),
                    )
                    .put("required", JSONArray(listOf("command")))
                    .put("additionalProperties", false),
            )

        return JSONObject()
            .put("type", "session.update")
            .put(
                "session",
                JSONObject()
                    .put("type", "realtime")
                    .put("tools", JSONArray(listOf(readTool, applyTool)))
                    .put("tool_choice", "auto"),
            )
            .toString()
    }

    fun handleServerEvent(raw: String): List<ScreenAssistToolOutcome> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        if (root.optString("type") != "response.done") return emptyList()

        val output = root.optJSONObject("response")?.optJSONArray("output") ?: return emptyList()
        return buildList {
            for (index in 0 until output.length()) {
                val item = output.optJSONObject(index) ?: continue
                if (item.optString("type") != "function_call") continue
                val callId = item.optString("call_id")
                val name = item.optString("name")
                val arguments = item.optString("arguments", "{}")
                if (callId.isBlank()) continue

                when (name) {
                    "read_app_context" -> add(
                        ScreenAssistToolOutcome.Immediate(
                            callId,
                            runCatching { contextJson(contextReader.read()).toString() }
                                .getOrElse { errorJson("context_unavailable").toString() },
                        ),
                    )
                    "apply_app_command" -> {
                        val command = parseCommand(arguments)
                        if (command == null) {
                            add(ScreenAssistToolOutcome.Immediate(callId, errorJson("invalid_command").toString()))
                        } else if (commandPolicy.risk(command) == ScreenAssistCommandRisk.CONFIRMATION_REQUIRED) {
                            add(
                                ScreenAssistToolOutcome.AwaitConfirmation(
                                    ScreenAssistPendingToolCall(callId, command),
                                ),
                            )
                        } else {
                            add(
                                ScreenAssistToolOutcome.Immediate(
                                    callId,
                                    commandResultJson(
                                        runCatching {
                                            commandPolicy.execute(command, false, commandGateway)
                                        }.getOrElse {
                                            ScreenAssistCommandResult(false, false, "command_failed")
                                        },
                                    ).toString(),
                                ),
                            )
                        }
                    }
                    else -> add(ScreenAssistToolOutcome.Immediate(callId, errorJson("unknown_tool").toString()))
                }
            }
        }
    }

    fun resolvePending(
        pending: ScreenAssistPendingToolCall,
        confirmed: Boolean,
    ): ScreenAssistToolOutcome.Immediate {
        val result = if (!confirmed) {
            ScreenAssistCommandResult(false, false, "user_rejected")
        } else {
            runCatching {
                commandPolicy.execute(pending.command, true, commandGateway)
            }.getOrElse {
                ScreenAssistCommandResult(false, false, "command_failed")
            }
        }
        return ScreenAssistToolOutcome.Immediate(
            pending.callId,
            commandResultJson(result).toString(),
        )
    }

    private fun parseCommand(raw: String): ScreenAssistCommand? {
        val args = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return when (args.optString("command")) {
            "set_origin" -> args.requiredString("stop_id")?.let(ScreenAssistCommand::SetOrigin)
            "set_destination" -> args.requiredString("stop_id")?.let(ScreenAssistCommand::SetDestination)
            "set_boarding_stop" -> args.requiredString("stop_id")?.let(ScreenAssistCommand::SetBoardingStop)
            "select_journey" -> args.requiredString("journey_id")?.let(ScreenAssistCommand::SelectJourney)
            "fill_search" -> args.requiredString("query")?.let(ScreenAssistCommand::FillSearch)
            "request_route" -> {
                val destination = args.requiredString("destination_stop_id") ?: return null
                val origin = args.optString("origin_stop_id").trim().takeIf { it.isNotEmpty() }
                ScreenAssistCommand.RequestRoute(origin, destination)
            }
            "set_alarm_target" -> args.requiredString("stop_id")?.let(ScreenAssistCommand::SetAlarmTarget)
            else -> null
        }
    }

    private fun JSONObject.requiredString(name: String): String? =
        optString(name).trim().takeIf { it.isNotEmpty() }

    private fun commandResultJson(result: ScreenAssistCommandResult) =
        JSONObject()
            .put("applied", result.applied)
            .put("requires_confirmation", result.requiresConfirmation)
            .put("message", result.message)

    private fun errorJson(message: String) =
        JSONObject()
            .put("applied", false)
            .put("requires_confirmation", false)
            .put("message", message)

    private fun contextJson(context: ScreenAssistAppContext): JSONObject =
        JSONObject().apply {
            put("location", context.location?.let(::locationJson) ?: JSONObject.NULL)
            put("journey", context.journey?.let(::journeyJson) ?: JSONObject.NULL)
        }

    private fun locationJson(value: ScreenAssistLocation) =
        JSONObject()
            .put("latitude", value.latitude)
            .put("longitude", value.longitude)
            .put("freshness_epoch_ms", value.freshnessEpochMs)
            .put("source", value.source)

    private fun stopJson(value: ScreenAssistStop) =
        JSONObject()
            .put("id", value.id)
            .put("name", value.name)
            .put("latitude", value.latitude)
            .put("longitude", value.longitude)

    private fun journeyJson(value: ScreenAssistJourneyContext) =
        JSONObject()
            .put("journey_id", value.journeyId ?: JSONObject.NULL)
            .put("line", value.line ?: JSONObject.NULL)
            .put("trip_id", value.tripId ?: JSONObject.NULL)
            .put("boarding_stop", value.boardingStop?.let(::stopJson) ?: JSONObject.NULL)
            .put("target_stop", value.targetStop?.let(::stopJson) ?: JSONObject.NULL)
            .put("eta_epoch_ms", value.etaEpochMs ?: JSONObject.NULL)
            .put("realtime", value.realtime)
            .put("service_alert", value.serviceAlert ?: JSONObject.NULL)
            .put("freshness_epoch_ms", value.freshnessEpochMs)
            .put("source", value.source)
}
