package com.nazofobi.arrivalalarm

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
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
    fun postServerFailureDoesNotRetryPotentiallyAppliedCommand() {
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { request, _, _ ->
                assertEquals("POST", request.method)
                calls += 1
                HttpTransportResponse(503)
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { error("POST must not be retried") },
            maxRetries = 3,
        )

        val response = transport.execute(
            HttpTransportRequest("https://example.invalid/command", method = "POST")
        ) as HttpTransportResult.Response
        assertEquals(503, response.response.status)
        assertEquals(1, response.attempts)
        assertEquals(1, calls)
    }

    @Test
    fun postTimeoutDoesNotRepeatPotentiallyAppliedCommand() {
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                throw SocketTimeoutException("request outcome unknown")
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { error("POST must not be retried") },
            maxRetries = 3,
        )

        val failure = transport.execute(
            HttpTransportRequest("https://example.invalid/command", method = "POST")
        ) as HttpTransportResult.Failure
        assertEquals(HttpTransportResult.Failure.Kind.TIMEOUT, failure.kind)
        assertEquals(1, failure.attempts)
        assertEquals(1, calls)
    }

    @Test
    fun headRetainsRetryForReadOnlyRequests() {
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                HttpTransportResponse(if (calls == 1) 503 else 204)
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { },
            maxRetries = 2,
        )

        val result = transport.execute(
            HttpTransportRequest("https://example.invalid/status", method = "head")
        ) as HttpTransportResult.Response
        assertEquals(204, result.response.status)
        assertEquals(2, result.attempts)
        assertEquals(2, calls)
    }

    @Test
    fun invalidResponseCodeNeverCountsAsHealthyAndOpensCircuit() {
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                HttpTransportResponse(0)
            },
            clock = HttpTransportClock { 100L },
            sleeper = HttpTransportSleeper { error("invalid status must not retry") },
            maxRetries = 0,
            failureThreshold = 1,
        )
        val failure = transport.execute(
            HttpTransportRequest("https://example.invalid")
        ) as HttpTransportResult.Failure
        assertEquals(HttpTransportResult.Failure.Kind.NETWORK, failure.kind)
        assertEquals("invalid_http_status", failure.message)
        assertEquals(1, failure.attempts)
        val blocked = transport.execute(
            HttpTransportRequest("https://example.invalid")
        ) as HttpTransportResult.Failure
        assertEquals(HttpTransportResult.Failure.Kind.CIRCUIT_OPEN, blocked.kind)
        assertEquals(0, blocked.attempts)
        assertEquals(1, calls)
    }

    @Test
    fun informationalAndOutOfRangeCodesUseOnlyTheBoundedReadRetryBudget() {
        val responses = ArrayDeque(listOf(
            HttpTransportResponse(101),
            HttpTransportResponse(999),
            HttpTransportResponse(200),
        ))
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ -> responses.removeFirst() },
            clock = HttpTransportClock { 0L },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 2,
            baseBackoffMs = 10L,
        )
        val response = transport.execute(
            HttpTransportRequest("https://example.invalid", method = "GET")
        ) as HttpTransportResult.Response
        assertEquals(200, response.response.status)
        assertEquals(3, response.attempts)
        assertEquals(listOf(10L, 20L), delays)
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
    fun retryAfterHttpDateRejectsTrailingGarbageAndHonorsValidDate() {
        val responses = ArrayDeque(listOf(
            HttpTransportResponse(503, mapOf("Retry-After" to listOf("Sun, 06 Nov 1994 08:49:39 GMT; invalid"))),
            HttpTransportResponse(503, mapOf("Retry-After" to listOf("Sun, 06 Nov 1994 08:49:39 GMT"))),
            HttpTransportResponse(200),
        ))
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ -> responses.removeFirst() },
            clock = HttpTransportClock { 784111777000L },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 2,
            baseBackoffMs = 125L,
            maxRetryDelayMs = 4_000L,
        )

        val response = transport.execute(
            HttpTransportRequest("https://example.invalid", method = "GET")
        ) as HttpTransportResult.Response

        assertEquals(200, response.response.status)
        assertEquals(3, response.attempts)
        assertEquals(listOf(125L, 2_000L), delays)
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
    fun retryAfterOverflowingDeltaSecondsUsesMaxDelayInsteadOfShortFallback() {
        val responses = ArrayDeque(listOf(
            HttpTransportResponse(429, mapOf("Retry-After" to listOf("9223372036854775808"))),
            HttpTransportResponse(503, mapOf("retry-after" to listOf("999999999999999999999999999999999999"))),
            HttpTransportResponse(200),
        ))
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ -> responses.removeFirst() },
            clock = HttpTransportClock { 0L },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 2,
            baseBackoffMs = 25L,
            maxRetryDelayMs = 4_000L,
        )

        val result = transport.execute(HttpTransportRequest("https://example.invalid")) as HttpTransportResult.Response
        assertEquals(200, result.response.status)
        assertEquals(3, result.attempts)
        assertEquals(listOf(4_000L, 4_000L), delays)
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
    fun socketTimeoutRetainsTimeoutKindAfterBoundedRetries() {
        var calls = 0
        val delays = mutableListOf<Long>()
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                throw SocketTimeoutException("read timed out")
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { delays += it },
            maxRetries = 1,
            baseBackoffMs = 25,
        )

        val result = transport.execute(
            HttpTransportRequest("https://example.invalid")
        ) as HttpTransportResult.Failure

        assertEquals(HttpTransportResult.Failure.Kind.TIMEOUT, result.kind)
        assertEquals(2, result.attempts)
        assertEquals(2, calls)
        assertEquals(listOf(25L), delays)
    }

    @Test
    fun networkFailureDoesNotExposeTokenOrPreciseLocationInDiagnostics() {
        val secret = "api_token_please_do_not_log"
        val coordinates = "52.666123,8.238456"
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                throw IOException("GET https://provider.invalid?token=$secret&location=$coordinates failed")
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { error("no retry expected") },
            maxRetries = 0,
        )

        val result = transport.execute(
            HttpTransportRequest("https://provider.invalid?token=$secret")
        ) as HttpTransportResult.Failure

        assertEquals(HttpTransportResult.Failure.Kind.NETWORK, result.kind)
        assertEquals(1, result.attempts)
        assertEquals("network_error", result.message)
        assertTrue(!result.toString().contains(secret))
        assertTrue(!result.toString().contains(coordinates))
    }

    @Test
    fun timeoutFailureDoesNotExposeRequestUrlOrCredential() {
        val secret = "private_bearer_token"
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                throw SocketTimeoutException("Read timed out at https://provider.invalid?key=$secret")
            },
            clock = HttpTransportClock { 0 },
            sleeper = HttpTransportSleeper { error("no retry expected") },
            maxRetries = 0,
        )

        val result = transport.execute(
            HttpTransportRequest("https://provider.invalid?key=$secret")
        ) as HttpTransportResult.Failure

        assertEquals(HttpTransportResult.Failure.Kind.TIMEOUT, result.kind)
        assertEquals(1, result.attempts)
        assertEquals("timeout", result.message)
        assertTrue(!result.toString().contains(secret))
        assertTrue(!result.toString().contains("https://provider.invalid"))
    }

    @Test
    fun alreadyInterruptedThreadDoesNotCallProviderOrChangeCircuitState() {
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                HttpTransportResponse(200)
            },
            clock = HttpTransportClock { 100L },
            sleeper = HttpTransportSleeper { error("pre-interrupted request must not back off") },
            failureThreshold = 1,
        )

        try {
            Thread.currentThread().interrupt()
            val failure = transport.execute(HttpTransportRequest("https://provider.invalid")) as HttpTransportResult.Failure
            assertEquals(HttpTransportResult.Failure.Kind.NETWORK, failure.kind)
            assertEquals("request_interrupted", failure.message)
            assertEquals(0, failure.attempts)
            assertEquals(0, calls)
            assertTrue(Thread.currentThread().isInterrupted)

            // A cancellation must not trip the provider circuit for later requests.
            Thread.interrupted()
            val success = transport.execute(HttpTransportRequest("https://provider.invalid")) as HttpTransportResult.Response
            assertEquals(200, success.response.status)
            assertEquals(1, success.attempts)
            assertEquals(1, calls)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun interruptedNetworkIoDoesNotRetryLeakDetailsOrTripCircuit() {
        var calls = 0
        val secret = "secret_request_token"
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                if (calls == 1) {
                    throw InterruptedIOException("GET https://provider.invalid?token=$secret cancelled")
                }
                HttpTransportResponse(200)
            },
            clock = HttpTransportClock { 100L },
            sleeper = HttpTransportSleeper { error("cancelled request must not be retried") },
            maxRetries = 3,
            failureThreshold = 1,
        )

        try {
            val failure = transport.execute(HttpTransportRequest("https://provider.invalid")) as HttpTransportResult.Failure
            assertEquals(HttpTransportResult.Failure.Kind.NETWORK, failure.kind)
            assertEquals(1, failure.attempts)
            assertEquals("request_interrupted", failure.message)
            assertEquals(1, calls)
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(!failure.toString().contains(secret))

            // A client cancellation must not count as a failed provider and trip
            // the circuit; a subsequent new request may still succeed.
            Thread.interrupted()
            val success = transport.execute(HttpTransportRequest("https://provider.invalid")) as HttpTransportResult.Response
            assertEquals(200, success.response.status)
            assertEquals(1, success.attempts)
            assertEquals(2, calls)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun interruptionDuringSuccessfulBackoffStillStopsBeforeRetrying() {
        var calls = 0
        var backoffs = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                HttpTransportResponse(if (calls == 1) 503 else 200)
            },
            clock = HttpTransportClock { 100L },
            // A cancellation-aware backoff adapter can return normally even
            // when the thread has become interrupted.
            sleeper = HttpTransportSleeper {
                backoffs += 1
                Thread.currentThread().interrupt()
            },
            maxRetries = 2,
            failureThreshold = 1,
        )

        try {
            val failure = transport.execute(
                HttpTransportRequest("https://provider.invalid")
            ) as HttpTransportResult.Failure
            assertEquals(HttpTransportResult.Failure.Kind.NETWORK, failure.kind)
            assertEquals("request_interrupted", failure.message)
            assertEquals(1, failure.attempts)
            assertEquals(1, calls)
            assertEquals(1, backoffs)
            assertTrue(Thread.currentThread().isInterrupted)

            // Cancellation is not a provider outage and must not open the circuit.
            Thread.interrupted()
            val success = transport.execute(
                HttpTransportRequest("https://provider.invalid")
            ) as HttpTransportResult.Response
            assertEquals(200, success.response.status)
            assertEquals(1, success.attempts)
            assertEquals(2, calls)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun interruptedSuccessfulExchangeFailsClosedWithoutTrippingCircuit() {
        var calls = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                if (calls == 1) Thread.currentThread().interrupt()
                HttpTransportResponse(200)
            },
            clock = HttpTransportClock { 100L },
            sleeper = HttpTransportSleeper { throw AssertionError("No retry backoff expected") },
            maxRetries = 2,
            failureThreshold = 1,
        )

        try {
            val failure = transport.execute(
                HttpTransportRequest("https://provider.invalid")
            ) as HttpTransportResult.Failure
            assertEquals(HttpTransportResult.Failure.Kind.NETWORK, failure.kind)
            assertEquals("request_interrupted", failure.message)
            assertEquals(1, failure.attempts)
            assertEquals(1, calls)
            assertTrue(Thread.currentThread().isInterrupted)

            Thread.interrupted()
            val recovered = transport.execute(
                HttpTransportRequest("https://provider.invalid")
            ) as HttpTransportResult.Response
            assertEquals(200, recovered.response.status)
            assertEquals(1, recovered.attempts)
            assertEquals(2, calls)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun interruptedPlainIoExceptionStopsBeforeBackoff() {
        var calls = 0
        var sleeps = 0
        val transport = BoundedHttpTransport(
            exchange = HttpExchange { _, _, _ ->
                calls += 1
                if (calls == 1) {
                    Thread.currentThread().interrupt()
                    throw IOException("credential=secret&lat=52.1")
                }
                HttpTransportResponse(200)
            },
            clock = HttpTransportClock { 100L },
            sleeper = HttpTransportSleeper {
                sleeps += 1
                // A poorly behaved adapter can consume a cancellation.
                Thread.interrupted()
            },
            maxRetries = 2,
            failureThreshold = 1,
        )

        try {
            val failure = transport.execute(
                HttpTransportRequest("https://provider.invalid")
            ) as HttpTransportResult.Failure
            assertEquals(HttpTransportResult.Failure.Kind.NETWORK, failure.kind)
            assertEquals("request_interrupted", failure.message)
            assertEquals(1, failure.attempts)
            assertEquals(1, calls)
            assertEquals(0, sleeps)
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(!failure.toString().contains("secret"))

            // Cancellation must not trip the provider circuit.
            Thread.interrupted()
            val success = transport.execute(
                HttpTransportRequest("https://provider.invalid")
            ) as HttpTransportResult.Response
            assertEquals(200, success.response.status)
            assertEquals(1, success.attempts)
            assertEquals(2, calls)
        } finally {
            Thread.interrupted()
        }
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
