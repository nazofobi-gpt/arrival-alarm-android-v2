package com.nazofobi.arrivalalarm

import java.io.IOException

data class HttpTransportRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
)

data class HttpTransportResponse(
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String = "",
)

sealed class HttpTransportResult {
    data class Response(val response: HttpTransportResponse, val attempts: Int) : HttpTransportResult()
    data class Failure(
        val kind: Kind,
        val attempts: Int,
        val message: String? = null,
    ) : HttpTransportResult() {
        enum class Kind { NETWORK, CIRCUIT_OPEN }
    }
}

fun interface HttpExchange {
    @Throws(IOException::class)
    fun execute(request: HttpTransportRequest, connectTimeoutMs: Int, readTimeoutMs: Int): HttpTransportResponse
}

fun interface HttpTransportClock { fun nowMillis(): Long }
fun interface HttpTransportSleeper { fun sleep(millis: Long) }

class BoundedHttpTransport(
    private val exchange: HttpExchange,
    private val clock: HttpTransportClock,
    private val sleeper: HttpTransportSleeper,
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val maxRetries: Int = 2,
    private val baseBackoffMs: Long = 250,
    private val maxRetryDelayMs: Long = 5_000,
    private val failureThreshold: Int = 3,
    private val circuitOpenMs: Long = 30_000,
) {
    init {
        require(connectTimeoutMs > 0 && readTimeoutMs > 0)
        require(maxRetries >= 0)
        require(baseBackoffMs >= 0 && maxRetryDelayMs >= 0)
        require(failureThreshold > 0 && circuitOpenMs >= 0)
    }

    private var consecutiveFailures = 0
    private var circuitOpenUntilMs = 0L

    @Synchronized
    fun execute(request: HttpTransportRequest): HttpTransportResult {
        val now = clock.nowMillis()
        if (now < circuitOpenUntilMs) {
            return HttpTransportResult.Failure(HttpTransportResult.Failure.Kind.CIRCUIT_OPEN, 0)
        }
        if (circuitOpenUntilMs != 0L) {
            circuitOpenUntilMs = 0L
            consecutiveFailures = 0
        }

        var attempts = 0
        while (true) {
            attempts += 1
            try {
                val response = exchange.execute(request, connectTimeoutMs, readTimeoutMs)
                if (!isRetryableStatus(response.status)) {
                    recordSuccess()
                    return HttpTransportResult.Response(response, attempts)
                }
                if (attempts > maxRetries) {
                    recordFailure()
                    return HttpTransportResult.Response(response, attempts)
                }
                sleeper.sleep(retryDelayMs(response, attempts))
            } catch (error: IOException) {
                if (attempts > maxRetries) {
                    recordFailure()
                    return HttpTransportResult.Failure(
                        HttpTransportResult.Failure.Kind.NETWORK,
                        attempts,
                        error.message,
                    )
                }
                sleeper.sleep(backoffMs(attempts))
            }
        }
    }

    private fun retryDelayMs(response: HttpTransportResponse, attempts: Int): Long {
        val retryAfter = if (response.status == 429 || response.status == 503) {
            response.headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                ?.value?.firstOrNull()?.trim()?.toLongOrNull()?.times(1_000)
        } else null
        return (retryAfter ?: backoffMs(attempts)).coerceAtMost(maxRetryDelayMs)
    }

    private fun backoffMs(attempts: Int): Long {
        val shift = (attempts - 1).coerceIn(0, 30)
        val multiplier = 1L shl shift
        return (baseBackoffMs * multiplier).coerceAtMost(maxRetryDelayMs)
    }

    private fun isRetryableStatus(status: Int): Boolean = status == 429 || status in 500..599

    private fun recordSuccess() {
        consecutiveFailures = 0
        circuitOpenUntilMs = 0L
    }

    private fun recordFailure() {
        consecutiveFailures += 1
        if (consecutiveFailures >= failureThreshold) {
            circuitOpenUntilMs = clock.nowMillis() + circuitOpenMs
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 6_000
        const val DEFAULT_READ_TIMEOUT_MS = 8_000
    }
}
