package com.nazofobi.arrivalalarm

import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
        enum class Kind { NETWORK, TIMEOUT, CIRCUIT_OPEN }
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
                sleepOrStop(retryDelayMs(response, attempts), attempts)?.let { return it }
            } catch (error: IOException) {
                if (attempts > maxRetries) {
                    recordFailure()
                    return HttpTransportResult.Failure(
                        if (error is SocketTimeoutException) {
                            HttpTransportResult.Failure.Kind.TIMEOUT
                        } else {
                            HttpTransportResult.Failure.Kind.NETWORK
                        },
                        attempts,
                        error.message,
                    )
                }
                sleepOrStop(backoffMs(attempts), attempts)?.let { return it }
            }
        }
    }

    private fun sleepOrStop(delayMillis: Long, attempts: Int): HttpTransportResult.Failure? =
        try {
            sleeper.sleep(delayMillis)
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            HttpTransportResult.Failure(
                kind = HttpTransportResult.Failure.Kind.NETWORK,
                attempts = attempts,
                message = "retry interrupted",
            )
        }

    private fun retryDelayMs(response: HttpTransportResponse, attempts: Int): Long {
        val retryAfter = if (response.status == 429 || response.status == 503) {
            response.headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                ?.value?.firstOrNull()?.trim()?.let(::parseRetryAfterMs)
        } else null
        return (retryAfter ?: backoffMs(attempts)).coerceIn(0L, maxRetryDelayMs)
    }

    private fun parseRetryAfterMs(value: String): Long? {
        value.toLongOrNull()?.let { seconds ->
            if (seconds < 0) return null
            return saturatingMultiply(seconds, 1_000L)
        }
        return try {
            val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val deadline = formatter.parse(value)?.time ?: return null
            saturatingSubtractNonNegative(deadline, clock.nowMillis())
        } catch (_: Exception) {
            null
        }
    }

    private fun backoffMs(attempts: Int): Long {
        val shift = (attempts - 1).coerceIn(0, 62)
        var delay = baseBackoffMs
        repeat(shift) {
            delay = saturatingMultiply(delay, 2L)
            if (delay >= maxRetryDelayMs) return maxRetryDelayMs
        }
        return delay.coerceAtMost(maxRetryDelayMs)
    }

    private fun isRetryableStatus(status: Int): Boolean = status == 429 || status in 500..599

    private fun recordSuccess() {
        consecutiveFailures = 0
        circuitOpenUntilMs = 0L
    }

    private fun recordFailure() {
        consecutiveFailures += 1
        if (consecutiveFailures >= failureThreshold) {
            circuitOpenUntilMs = saturatingAdd(clock.nowMillis(), circuitOpenMs)
        }
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE
        if (right < 0 && left < Long.MIN_VALUE - right) return Long.MIN_VALUE
        return left + right
    }

    private fun saturatingMultiply(left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0L
        if (left > 0 && right > 0 && left > Long.MAX_VALUE / right) return Long.MAX_VALUE
        if (left < 0 && right < 0 && left < Long.MAX_VALUE / right) return Long.MAX_VALUE
        if (left > 0 && right < 0 && right < Long.MIN_VALUE / left) return Long.MIN_VALUE
        if (left < 0 && right > 0 && left < Long.MIN_VALUE / right) return Long.MIN_VALUE
        return left * right
    }

    private fun saturatingSubtractNonNegative(deadline: Long, now: Long): Long {
        if (deadline <= now) return 0L
        return if (now < 0 && deadline > Long.MAX_VALUE + now) Long.MAX_VALUE else deadline - now
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 6_000
        const val DEFAULT_READ_TIMEOUT_MS = 8_000
    }
}
