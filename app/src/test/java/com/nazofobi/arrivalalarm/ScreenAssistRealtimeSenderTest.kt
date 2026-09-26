package com.nazofobi.arrivalalarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ScreenAssistRealtimeSenderTest {
    private class Socket : ScreenAssistRealtimeSocket {
        var connected = false
        val sent = mutableListOf<String>()
        private var listener: (String) -> Unit = {}

        override fun connect(ephemeralToken: String) {
            connected = ephemeralToken == "ephemeral"
        }

        override fun send(text: String) {
            sent += text
        }

        override fun close() {
            connected = false
        }

        override fun setEventListener(listener: (String) -> Unit) {
            this.listener = listener
        }

        fun emit(event: String) {
            listener(event)
        }
    }

    @Test
    fun connectsWithUsableEphemeralTokenAndRequestsGuidanceForImage() {
        val socket = Socket()
        val session = ScreenAssistRealtimeSession()
        val image = ScreenAssistRealtimeImage(base64Encoder = { Base64.getEncoder().encodeToString(it) })
        val sender = ScreenAssistRealtimeSender(session, socket, image)

        assertTrue(sender.connect(ScreenAssistEphemeralToken("ephemeral", 200L), 100L))
        assertTrue(sender.sendFrame(byteArrayOf(1, 2, 3)))
        assertTrue(socket.connected)
        assertEquals(2, socket.sent.size)
        assertTrue(socket.sent[0].contains("conversation.item.create"))
        assertTrue(socket.sent[1].contains("response.create"))
    }

    @Test
    fun responseDoneReleasesFrameBackpressure() {
        val socket = Socket()
        val session = ScreenAssistRealtimeSession()
        val image = ScreenAssistRealtimeImage(base64Encoder = { Base64.getEncoder().encodeToString(it) })
        val sender = ScreenAssistRealtimeSender(session, socket, image)
        sender.connect(ScreenAssistEphemeralToken("ephemeral", 200L), 100L)

        var firstComplete = false
        var secondComplete = false
        assertTrue(sender.sendFrame(byteArrayOf(1)) { firstComplete = true })
        assertFalse(sender.sendFrame(byteArrayOf(2)) { secondComplete = true })
        assertTrue(secondComplete)
        assertFalse(firstComplete)

        socket.emit("""{"type":"response.done"}""")
        assertTrue(firstComplete)
        assertTrue(sender.sendFrame(byteArrayOf(3)))
    }


    @Test
    fun functionToolOutputContinuesSameRealtimeTurnBeforeReleasingFrame() {
        val socket = Socket()
        val session = ScreenAssistRealtimeSession()
        val image = ScreenAssistRealtimeImage(base64Encoder = { Base64.getEncoder().encodeToString(it) })
        val gateway = object : ScreenAssistContextReader, ScreenAssistCommandGateway {
            override fun read() = ScreenAssistAppContext(null, null)
            override fun validate(command: ScreenAssistCommand) = false
            override fun apply(command: ScreenAssistCommand) =
                ScreenAssistCommandResult(false, false, "not_supported")
        }
        val sender = ScreenAssistRealtimeSender(
            session = session,
            socket = socket,
            imageBuilder = image,
            toolHandler = ScreenAssistRealtimeToolHandler(gateway, gateway),
        )

        assertTrue(sender.connect(ScreenAssistEphemeralToken("ephemeral", 200L), 100L))
        assertTrue(socket.sent.first().contains("session.update"))

        var complete = false
        assertTrue(sender.sendFrame(byteArrayOf(9)) { complete = true })

        socket.emit(
            JSONObject()
                .put("type", "response.done")
                .put(
                    "response",
                    JSONObject().put(
                        "output",
                        org.json.JSONArray().put(
                            JSONObject()
                                .put("type", "function_call")
                                .put("name", "read_app_context")
                                .put("call_id", "call-read")
                                .put("arguments", "{}"),
                        ),
                    ),
                )
                .toString(),
        )

        assertFalse(complete)
        assertTrue(socket.sent.any { it.contains("function_call_output") && it.contains("call-read") })
        assertTrue(socket.sent.last().contains("response.create"))

        socket.emit("""{"type":"response.done","response":{"output":[]}}""")
        assertTrue(complete)
    }

    @Test
    fun confirmationRequiredToolDoesNotApplyUntilExplicitResolution() {
        val socket = Socket()
        val session = ScreenAssistRealtimeSession()
        val image = ScreenAssistRealtimeImage(base64Encoder = { Base64.getEncoder().encodeToString(it) })
        val applied = mutableListOf<ScreenAssistCommand>()
        val gateway = object : ScreenAssistContextReader, ScreenAssistCommandGateway {
            override fun read() = ScreenAssistAppContext(null, null)
            override fun validate(command: ScreenAssistCommand) = true
            override fun apply(command: ScreenAssistCommand): ScreenAssistCommandResult {
                applied += command
                return ScreenAssistCommandResult(true, false, "applied")
            }
        }
        var pending: ScreenAssistPendingToolCall? = null
        val sender = ScreenAssistRealtimeSender(
            session = session,
            socket = socket,
            imageBuilder = image,
            toolHandler = ScreenAssistRealtimeToolHandler(gateway, gateway),
            onToolConfirmationRequired = { pending = it },
        )
        sender.connect(ScreenAssistEphemeralToken("ephemeral", 200L), 100L)
        sender.sendFrame(byteArrayOf(7))

        val arguments = JSONObject()
            .put("command", "set_alarm_target")
            .put("stop_id", "target-1")
            .toString()
        socket.emit(
            JSONObject()
                .put("type", "response.done")
                .put(
                    "response",
                    JSONObject().put(
                        "output",
                        org.json.JSONArray().put(
                            JSONObject()
                                .put("type", "function_call")
                                .put("name", "apply_app_command")
                                .put("call_id", "call-confirm")
                                .put("arguments", arguments),
                        ),
                    ),
                )
                .toString(),
        )

        assertTrue(applied.isEmpty())
        val waiting = pending ?: error("Expected pending confirmation")
        assertTrue(sender.resolveToolConfirmation(waiting, true))
        assertEquals(ScreenAssistCommand.SetAlarmTarget("target-1"), applied.single())
        assertTrue(socket.sent.any { it.contains("function_call_output") && it.contains("call-confirm") })
    }

    @Test
    fun refusesFrameBeforeConnectedAndCompletesAdmission() {
        val sender = ScreenAssistRealtimeSender(ScreenAssistRealtimeSession(), Socket())
        var completed = false
        assertFalse(sender.sendFrame(byteArrayOf(1)) { completed = true })
        assertTrue(completed)
    }
}
