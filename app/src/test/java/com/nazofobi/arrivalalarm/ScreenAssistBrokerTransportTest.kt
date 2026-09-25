package com.nazofobi.arrivalalarm

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Test

class ScreenAssistBrokerTransportTest {
    private class FakeConnection(
        url: URL,
        private val code: Int,
        private val body: String,
    ) : HttpURLConnection(url) {
        val written = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getOutputStream() = written
    }

    @Test
    fun requestsEphemeralResponseOverHttps() {
        lateinit var fake: FakeConnection
        val transport = ScreenAssistBrokerTransport("https://broker.example/realtime") { url ->
            FakeConnection(url, 200, """{"client_secret":{"value":"temporary_value","expires_at":1060}}""").also { fake = it }
        }
        val body = transport.requestTokenResponse()
        assertEquals("POST", fake.requestMethod)
        assertEquals("{}", fake.written.toString(Charsets.UTF_8.name()))
        assertEquals(1060L, ScreenAssistBrokerResponseParser().parse(body).expiresAtEpochSeconds)
    }

    @Test
    fun rejectsNonHttpsBroker() {
        assertFailsWith<IllegalArgumentException> {
            ScreenAssistBrokerTransport("http://broker.example/realtime")
        }
    }

    @Test
    fun failsClosedOnBrokerError() {
        val transport = ScreenAssistBrokerTransport("https://broker.example/realtime") { url ->
            FakeConnection(url, 503, "{}")
        }
        assertFailsWith<IllegalStateException> { transport.requestTokenResponse() }
    }
}
