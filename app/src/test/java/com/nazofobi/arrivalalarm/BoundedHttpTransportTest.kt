package com.nazofobi.arrivalalarm

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedHttpTransportTest {
    private class FakeClock(var now: Long = 0) : HttpTransportClock {
        override fun nowMillis(): Long = now
    }

    @Test
    fun defaultsAreExplicitAndOrdinary4xxIsNotRetried() {
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, connect, read ->
                calls += 1
                assertEquals(6_000, connect)
                assertEquals(8_000, read)
                HttpTransportResponse(404, mapOf("X-Test" to listOf("kept")), "missing")
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { error("must not sleep") },
        )

        val result = transport.execute(HttpTransportRequest("https://example.invalid"))
            as HttpTransportResult.Response

        assertEquals(1, calls)
        assertEquals(1, result.attempts)
        assertEquals(404, result.response.status)
        assertEquals("missing", result.response.body)
        assertEquals("kept", result.response.headers["X-Test"]?.single())
    }

    @Test
    fun retryable5xxUsesBoundedBackoffAndHardAttemptBudget() {
        var calls = 0
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                HttpTransportResponse(500, body = "upstream")
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 2,
            baseBackoffMs = 100,
        )

        val result = transport.execute(HttpTransportRequest("https://example.invalid"))
            as HttpTransportResult.Response

        assertEquals(3, calls)
        assertEquals(3, result.attempts)
        assertEquals(listOf(100L, 200L), delays)
    }

    @Test
    fun retryAfterIsHonoredAndClampedFor429And503() {
        val statuses = ArrayDeque(listOf(429, 503, 200))
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                val status = statuses.removeFirst()
                HttpTransportResponse(status, mapOf("Retry-After" to listOf(if (status == 429) "99" else "2")))
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 2,
            maxRetryDelayMs = 3_000,
        )

        val result = transport.execute(HttpTransportRequest("https://example.invalid"))
            as HttpTransportResult.Response

        assertEquals(200, result.response.status)
        assertEquals(listOf(3_000L, 2_000L), delays)
    }

    @Test
    fun networkFailuresAreBoundedWithoutRealSleep() {
        var calls = 0
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                throw IOException("offline")
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 1,
            baseBackoffMs = 25,
        )

        val result = transport.execute(HttpTransportRequest("https://example.invalid"))
            as HttpTransportResult.Failure

        assertEquals(HttpTransportResult.Failure.Kind.NETWORK, result.kind)
        assertEquals(2, result.attempts)
        assertEquals(2, calls)
        assertEquals(listOf(25L), delays)
    }

    @Test
    fun repeatedFailuresOpenCircuitAndRecoveryIsClockDriven() {
        val clock = FakeClock()
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                HttpTransportResponse(500)
            },
            clock = clock,
            sleeper = HttpTransportSleeper { },
            maxRetries = 0,
            failureThreshold = 2,
            circuitOpenMs = 1_000,
        )

        transport.execute(HttpTransportRequest("https://example.invalid"))
        transport.execute(HttpTransportRequest("https://example.invalid"))
        val blocked = transport.execute(HttpTransportRequest("https://example.invalid"))
            as HttpTransportResult.Failure

        assertEquals(HttpTransportResult.Failure.Kind.CIRCUIT_OPEN, blocked.kind)
        assertEquals(0, blocked.attempts)
        assertEquals(2, calls)

        clock.now = 1_000
        val recovered = transport.execute(HttpTransportRequest("https://example.invalid"))
        assertTrue(recovered is HttpTransportResult.Response)
        assertEquals(3, calls)
    }
}
