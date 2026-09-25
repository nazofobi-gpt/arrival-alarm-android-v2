package com.nazofobi.arrivalalarm

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

class ScreenAssistRealtimeEventParser {
    fun outputTextDelta(raw: String): String? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optString("type") != "response.output_text.delta") return null
        return root.optString("delta").takeIf { it.isNotEmpty() }
    }
}

/**
 * Coordinates the app-owned ephemeral-token broker with the native Realtime
 * WebRTC socket off the Android main thread.
 *
 * No standard OpenAI key is accepted by this class. The only credential that
 * reaches the mobile WebRTC layer is a short-lived ScreenAssistEphemeralToken.
 */
class ScreenAssistRealtimeRuntime(
    private val session: ScreenAssistRealtimeSession,
    private val brokerTransportFactory: () -> ScreenAssistBrokerTransport,
    private val socketFactory: () -> ScreenAssistRealtimeSocket,
    private val responseParser: ScreenAssistBrokerResponseParser = ScreenAssistBrokerResponseParser(),
    private val eventParser: ScreenAssistRealtimeEventParser = ScreenAssistRealtimeEventParser(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "screen-assist-realtime").apply { isDaemon = true }
    },
) : AutoCloseable {
    private val generation = AtomicLong(0)

    @Volatile
    private var sender: ScreenAssistRealtimeSender? = null

    fun connect(
        onConnected: (ScreenAssistRealtimeSender) -> Unit,
        onGuidanceDelta: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val ticket = generation.incrementAndGet()
        val previous = sender
        sender = null
        previous?.stop()
        session.beginConnecting()

        executor.execute {
            var candidate: ScreenAssistRealtimeSender? = null
            try {
                val brokerResponse = brokerTransportFactory().requestTokenResponse()
                val token = responseParser.parse(brokerResponse)
                val socket = socketFactory()
                candidate = ScreenAssistRealtimeSender(
                    session = session,
                    socket = socket,
                    onServerEvent = { raw ->
                        eventParser.outputTextDelta(raw)?.let(onGuidanceDelta)
                    },
                )
                check(candidate.connect(token, nowEpochSeconds())) {
                    "Realtime WebRTC connection could not be established"
                }

                if (generation.get() != ticket) {
                    candidate.stop()
                    return@execute
                }

                sender = candidate
                onConnected(candidate)
            } catch (error: Throwable) {
                candidate?.stop()
                if (generation.get() == ticket) {
                    session.connectionFailed()
                    onFailure(error)
                }
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        val current = sender
        sender = null
        current?.stop()
        session.stop()
    }

    override fun close() {
        stop()
        executor.shutdownNow()
    }
}
