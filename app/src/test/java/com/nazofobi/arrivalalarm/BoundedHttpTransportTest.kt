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
        val result = transport.execute(HttpTransportRequest("https://example.invalid")) as HttpTransportResult.Response
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
            exchange = HttpExchange { _, _, _ -> calls += 1; HttpTransportResponse(500, body = "upstream") },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 2,
            baseBackoffMs = 100,
        )
        val result = transport.execute(HttpTransportRequest("https://example.invalid")) as HttpTransportResult.Response
        assertEquals(3, calls)
        assertEquals(3, result.attempts)
        assertEquals(listOf(100L, 200L), delays)
    }

    @Test
    fun retryAfterDelaySecondsAndHttpDateAreHonoredAndClamped() {
        val clock = FakeClock(784111777000L) // Sun, 06 Nov 1994 08:49:37 GMT
        val responses = ArrayDeque(listOf(
            HttpTransportResponse(429, mapOf("Retry-After" to listOf("99"))),
            HttpTransportResponse(503, mapOf("Retry-After" to listOf("Sun, 06 Nov 1994 08:49:39 GMT"))),
            HttpTransportResponse(200),
        ))
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ -> responses.removeFirst() },
            clock = clock,
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 2,
            maxRetryDelayMs = 3_000,
        )
        val result = transport.execute(HttpTransportRequest("https://example.invalid")) as HttpTransportResult.Response
        assertEquals(200, result.response.status)
        assertEquals(listOf(3_000L, 2_000L), delays)
    }

    @Test
    fun retryAfterPastInvalidAndHugeDecimalAreSafe() {
        val clock = FakeClock(784111777000L)
        val headers = ArrayDeque(listOf(
            "Sun, 06 Nov 1994 08:49:36 GMT",
            "not-a-date",
            "9223372036854775",
        ))
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                if (headers.isEmpty()) HttpTransportResponse(200)
                else HttpTransportResponse(503, mapOf("Retry-After" to listOf(headers.removeFirst())))
            },
            clock = clock,
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 3,
            baseBackoffMs = 125,
            maxRetryDelayMs = 4_000,
        )
        transport.execute(HttpTransportRequest("https://example.invalid"))
        assertEquals(listOf(0L, 250L, 4_000L), delays)
    }

    @Test
    fun networkFailuresAreBoundedWithoutRealSleep() {
        var calls = 0
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ -> calls += 1; throw IOException("offline") },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 1,
            baseBackoffMs = 25,
        )
        val result = transport.execute(HttpTransportRequest("https://example.invalid")) as HttpTransportResult.Failure
        assertEquals(HttpTransportResult.Failure.Kind.NETWORK, result.kind)
        assertEquals(2, result.attempts)
        assertEquals(2, calls)
        assertEquals(listOf(25L), delays)
    }

    @Test
    fun interruptedBackoffStopsRetryAndPreservesThreadInterrupt() {
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                HttpTransportResponse(503)
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { throw InterruptedException("cancelled") },
            maxRetries = 2,
            failureThreshold = 1,
        )

        try {
            val result = transport.execute(
                HttpTransportRequest("https://example.invalid")
            ) as HttpTransportResult.Failure

            assertEquals(HttpTransportResult.Failure.Kind.NETWORK, result.kind)
            assertEquals(1, result.attempts)
            assertEquals("retry interrupted", result.message)
            assertEquals(1, calls)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun hugeBackoffAndCircuitDeadlineNeverOverflowNegative() {
        val clock = FakeClock(Long.MAX_VALUE - 10)
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ -> HttpTransportResponse(500) },
            clock = clock,
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 2,
            baseBackoffMs = Long.MAX_VALUE / 2 + 1,
            maxRetryDelayMs = Long.MAX_VALUE,
            failureThreshold = 1,
            circuitOpenMs = 100,
        )
        transport.execute(HttpTransportRequest("https://example.invalid"))
        assertTrue(delays.all { it >= 0 })
        val blocked = transport.execute(HttpTransportRequest("https://example.invalid")) as HttpTransportResult.Failure
        assertEquals(HttpTransportResult.Failure.Kind.CIRCUIT_OPEN, blocked.kind)
    }

    @Test
    fun repeatedFailuresOpenCircuitAndRecoveryIsClockDriven() {
        val clock = FakeClock()
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ -> calls += 1; HttpTransportResponse(500) },
            clock = clock,
            sleeper = HttpTransportSleeper { },
            maxRetries = 0,
            failureThreshold = 2,
            circuitOpenMs = 1_000,
        )
        transport.execute(HttpTransportRequest("https://example.invalid"))
        transport.execute(HttpTransportRequest("https://example.invalid"))
        val blocked = transport.execute(HttpTransportRequest("https://example.invalid")) as HttpTransportResult.Failure
        assertEquals(HttpTransportResult.Failure.Kind.CIRCUIT_OPEN, blocked.kind)
        assertEquals(0, blocked.attempts)
        assertEquals(2, calls)
        clock.now = 1_000
        val recovered = transport.execute(HttpTransportRequest("https://example.invalid"))
        assertTrue(recovered is HttpTransportResult.Response)
        assertEquals(3, calls)
    }
}
