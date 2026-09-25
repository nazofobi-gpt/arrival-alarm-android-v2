package com.nazofobi.arrivalalarm

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ScreenAssistBrokerTransportTest {
    private class FakeConnection(url: URL, private val code: Int, private val body: String) : HttpURLConnection(url) {
        val written = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getOutputStream() = written
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            if (error is T) return
            throw error
        }
        fail("Expected failure")
    }

    @Test fun postsToBrokerOverHttps() {
        lateinit var fake: FakeConnection
        val transport = ScreenAssistBrokerTransport("https://example.com/realtime") { url ->
            FakeConnection(url, 200, "{}").also { fake = it }
        }
        assertEquals("{}", transport.requestTokenResponse())
        assertEquals("POST", fake.requestMethod)
        assertEquals("{}", fake.written.toString(Charsets.UTF_8.name()))
    }

    @Test fun rejectsNonHttpsBroker() {
        expectFailure<IllegalArgumentException> { ScreenAssistBrokerTransport("http://example.com/realtime") }
    }

    @Test fun failsClosedOnBrokerError() {
        val transport = ScreenAssistBrokerTransport("https://example.com/realtime") { url -> FakeConnection(url, 503, "{}") }
        expectFailure<IllegalStateException> { transport.requestTokenResponse() }
    }
}
