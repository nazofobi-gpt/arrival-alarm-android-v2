package com.nazofobi.arrivalalarm

import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class ConnectorDeviceSession(
    val baseUrl: String,
    val accessToken: String,
    val expiresAtEpochSeconds: Long? = null,
) {
    fun isExpired(nowEpochSeconds: Long, clockSkewSeconds: Long = 30): Boolean =
        expiresAtEpochSeconds?.let { nowEpochSeconds >= it - clockSkewSeconds } ?: false
}

interface ConnectorSessionProvider {
    fun currentSession(): ConnectorDeviceSession?
}

enum class ConnectorRuntimeState {
    STOPPED,
    DISCONNECTED,
    AUTH_EXPIRED,
    SYNCING,
    CONNECTED,
    ERROR,
}

data class ConnectorRuntimeStatus(
    val state: ConnectorRuntimeState,
    val lastSyncAtEpochSeconds: Long? = null,
    val lastResult: ConnectorSyncResult? = null,
    val errorCode: String? = null,
)

interface ConnectorRuntimeControl {
    fun start()
    fun syncNow(): ConnectorRuntimeStatus
    fun stop()
}

/**
 * App-process connector runtime.
 *
 * It never owns or persists credentials. Each cycle asks [ConnectorSessionProvider] for the
 * current device-bound session, performs one deterministic sync, and fails closed when the
 * session is absent or expired. Lifecycle owners coordinate this runtime so the visible UI
 * and an active-journey foreground service can share one serialized sync loop.
 */
class ConnectorRuntime(
    private val processor: ConnectorCommandProcessor,
    private val sessionProvider: ConnectorSessionProvider,
    private val transportFactory: (ConnectorDeviceSession) -> ConnectorGatewayTransport = { session ->
        HttpConnectorGatewayTransport(
            baseUrl = session.baseUrl,
            bearerTokenProvider = { session.accessToken },
        )
    },
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val syncIntervalSeconds: Long = 15,
    private val onStatus: (ConnectorRuntimeStatus) -> Unit = {},
) : Closeable, ConnectorRuntimeControl {
    @Volatile
    var status: ConnectorRuntimeStatus = ConnectorRuntimeStatus(ConnectorRuntimeState.STOPPED)
        private set

    private var executor: ScheduledExecutorService? = null

    @Synchronized
    override fun start() {
        if (executor != null) return
        val created = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "arrival-connector-sync").apply { isDaemon = true }
        }
        executor = created
        created.scheduleWithFixedDelay(
            { syncNow() },
            0,
            syncIntervalSeconds.coerceAtLeast(1),
            TimeUnit.SECONDS,
        )
    }

    @Synchronized
    override fun syncNow(): ConnectorRuntimeStatus {
        val now = nowEpochSeconds()
        val session = sessionProvider.currentSession()
        if (session == null || session.accessToken.isBlank()) {
            return publish(
                ConnectorRuntimeStatus(
                    state = ConnectorRuntimeState.DISCONNECTED,
                    lastSyncAtEpochSeconds = status.lastSyncAtEpochSeconds,
                )
            )
        }
        if (session.isExpired(now)) {
            return publish(
                ConnectorRuntimeStatus(
                    state = ConnectorRuntimeState.AUTH_EXPIRED,
                    lastSyncAtEpochSeconds = status.lastSyncAtEpochSeconds,
                )
            )
        }

        publish(
            ConnectorRuntimeStatus(
                state = ConnectorRuntimeState.SYNCING,
                lastSyncAtEpochSeconds = status.lastSyncAtEpochSeconds,
            )
        )
        return try {
            val result = ConnectorSyncClient(
                processor = processor,
                transport = transportFactory(session),
                nowEpochSeconds = nowEpochSeconds,
            ).syncOnce()
            publish(
                ConnectorRuntimeStatus(
                    state = ConnectorRuntimeState.CONNECTED,
                    lastSyncAtEpochSeconds = nowEpochSeconds(),
                    lastResult = result,
                )
            )
        } catch (error: Exception) {
            publish(
                ConnectorRuntimeStatus(
                    state = ConnectorRuntimeState.ERROR,
                    lastSyncAtEpochSeconds = status.lastSyncAtEpochSeconds,
                    errorCode = classify(error),
                )
            )
        }
    }

    @Synchronized
    override fun stop() {
        executor?.shutdownNow()
        executor = null
        publish(
            ConnectorRuntimeStatus(
                state = ConnectorRuntimeState.STOPPED,
                lastSyncAtEpochSeconds = status.lastSyncAtEpochSeconds,
                lastResult = status.lastResult,
            )
        )
    }

    override fun close() = stop()

    private fun publish(next: ConnectorRuntimeStatus): ConnectorRuntimeStatus {
        status = next
        onStatus(next)
        return next
    }

    private fun classify(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            message.startsWith("connector_http_401") -> "AUTH_INVALID"
            message.startsWith("connector_http_403") -> "AUTH_SCOPE"
            message.startsWith("connector_http_") -> "GATEWAY_HTTP"
            error is IllegalArgumentException -> "CONFIG_INVALID"
            else -> "SYNC_FAILED"
        }
    }
}
