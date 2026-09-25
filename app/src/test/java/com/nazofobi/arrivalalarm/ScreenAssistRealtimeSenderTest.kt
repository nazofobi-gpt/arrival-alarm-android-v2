package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ScreenAssistRealtimeSenderTest {
    private class Socket : ScreenAssistRealtimeSocket {
        var connected = false
        val sent = mutableListOf<String>()
        override fun connect(ephemeralToken: String) { connected = ephemeralToken == "ephemeral" }
        override fun send(text: String) { sent += text }
        override fun close() { connected = false }
    }

    @Test fun connectsWithUsableEphemeralTokenAndSendsBoundedImageEvent() {
        val socket = Socket()
        val session = ScreenAssistRealtimeSession()
        val image = ScreenAssistRealtimeImage(base64Encoder = { Base64.getEncoder().encodeToString(it) })
        val sender = ScreenAssistRealtimeSender(session, socket, image)
        assertTrue(sender.connect(ScreenAssistEphemeralToken("ephemeral", 200L), 100L))
        assertTrue(sender.sendFrame(byteArrayOf(1, 2, 3)))
        assertTrue(socket.connected)
        assertTrue(socket.sent.single().contains("conversation.item.create"))
    }

    @Test fun refusesFrameBeforeConnected() {
        val sender = ScreenAssistRealtimeSender(ScreenAssistRealtimeSession(), Socket())
        assertFalse(sender.sendFrame(byteArrayOf(1)))
    }
}
