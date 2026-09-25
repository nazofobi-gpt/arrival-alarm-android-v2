package com.nazofobi.arrivalalarm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistRealtimeRuntimeTest {
    private class Socket : ScreenAssistRealtimeSocket {
        private var listener: (String) -> Unit = {}
        var connectedToken: String? = null
        var closed = false

        override fun connect(ephemeralToken: String) {
            connectedToken = ephemeralToken
        }

        override fun send(text: String) = Unit

        override fun close() {
            closed = true
        }

        override fun setEventListener(listener: (String) -> Unit) {
            this.listener = listener
        }

        fun emit(raw: String) {
            listener(raw)
        }
    }

    @Test
    fun brokerTokenConnectsWebRtcAndTextDeltaIsSurfaced() {
        val session = ScreenAssistRealtimeSession()
        val socket = Socket()
        val connected = CountDownLatch(1)
        val guidance = CountDownLatch(1)
        var delta: String? = null

        val runtime = ScreenAssistRealtimeRuntime(
            session = session,
            brokerTokenResponse = { """{"value":"ek_test","expires_at":1200}""" },
            socketFactory = { socket },
            nowEpochSeconds = { 1000L },
        )
        try {
            runtime.connect(
                onConnected = { connected.countDown() },
                onGuidanceDelta = {
                    delta = it
                    guidance.countDown()
                },
                onFailure = { throw AssertionError(it) },
            )

            assertTrue(connected.await(2, TimeUnit.SECONDS))
            assertEquals("ek_test", socket.connectedToken)
            assertEquals(ScreenAssistRealtimePhase.CONNECTED, session.phase)

            socket.emit("""{"type":"response.output_text.delta","delta":"Sağa dön"}""")
            assertTrue(guidance.await(2, TimeUnit.SECONDS))
            assertEquals("Sağa dön", delta)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun brokerFailureBlocksSessionWithoutConnectingSocket() {
        val session = ScreenAssistRealtimeSession()
        val failed = CountDownLatch(1)
        var socketCreated = false

        val runtime = ScreenAssistRealtimeRuntime(
            session = session,
            brokerTokenResponse = { error("broker unavailable") },
            socketFactory = {
                socketCreated = true
                Socket()
            },
            nowEpochSeconds = { 1000L },
        )
        try {
            runtime.connect(
                onConnected = { throw AssertionError("must not connect") },
                onGuidanceDelta = {},
                onFailure = { failed.countDown() },
            )

            assertTrue(failed.await(2, TimeUnit.SECONDS))
            assertEquals(false, socketCreated)
            assertEquals(ScreenAssistRealtimePhase.BLOCKED, session.phase)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun eventParserIgnoresNonTextEvents() {
        val parser = ScreenAssistRealtimeEventParser()
        assertEquals(null, parser.outputTextDelta("""{"type":"response.done"}"""))
        assertEquals(
            "Devam et",
            parser.outputTextDelta(
                """{"type":"response.output_text.delta","delta":"Devam et"}"""
            ),
        )
    }
}
