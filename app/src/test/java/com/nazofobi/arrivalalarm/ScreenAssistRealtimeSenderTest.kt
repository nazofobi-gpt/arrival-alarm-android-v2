package com.nazofobi.arrivalalarm

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
    fun refusesFrameBeforeConnectedAndCompletesAdmission() {
        val sender = ScreenAssistRealtimeSender(ScreenAssistRealtimeSession(), Socket())
        var completed = false
        assertFalse(sender.sendFrame(byteArrayOf(1)) { completed = true })
        assertTrue(completed)
    }
}
