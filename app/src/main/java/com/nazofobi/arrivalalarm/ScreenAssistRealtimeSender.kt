package com.nazofobi.arrivalalarm

import org.json.JSONObject

interface ScreenAssistRealtimeSocket {
    fun connect(ephemeralToken: String)
    fun send(text: String)
    fun close()
    fun setEventListener(listener: (String) -> Unit) = Unit
}

class ScreenAssistRealtimeSender(
    private val session: ScreenAssistRealtimeSession,
    private val socket: ScreenAssistRealtimeSocket,
    private val imageBuilder: ScreenAssistRealtimeImage = ScreenAssistRealtimeImage(),
    private val onServerEvent: (String) -> Unit = {},
) {
    private var pendingFrameComplete: (() -> Unit)? = null

    init {
        socket.setEventListener(::handleServerEvent)
    }

    fun connect(token: ScreenAssistEphemeralToken, nowEpochSeconds: Long): Boolean {
        if (!session.acceptToken(token, nowEpochSeconds)) return false
        if (!session.connect(nowEpochSeconds)) return false
        return try {
            socket.connect(token.value)
            true
        } catch (_: Exception) {
            session.networkLost()
            false
        }
    }

    fun sendFrame(jpeg: ByteArray): Boolean = sendFrame(jpeg) {}

    fun sendFrame(jpeg: ByteArray, onComplete: () -> Unit): Boolean {
        if (session.phase != ScreenAssistRealtimePhase.CONNECTED) {
            onComplete()
            return false
        }

        synchronized(this) {
            if (pendingFrameComplete != null) {
                onComplete()
                return false
            }
            pendingFrameComplete = onComplete
        }

        return try {
            socket.send(imageBuilder.buildConversationItem(jpeg))
            socket.send(imageBuilder.buildGuidanceResponseRequest())
            true
        } catch (_: Exception) {
            finishPendingFrame()
            session.networkLost()
            false
        }
    }

    fun stop() {
        finishPendingFrame()
        try {
            socket.close()
        } finally {
            session.stop()
        }
    }

    private fun handleServerEvent(raw: String) {
        onServerEvent(raw)
        val type = runCatching { JSONObject(raw).optString("type") }.getOrDefault("")
        if (
            type == "response.done" ||
            type == "error" ||
            type == "session.closed"
        ) {
            finishPendingFrame()
        }
        if (type == "error") {
            session.networkLost()
        }
    }

    private fun finishPendingFrame() {
        val complete = synchronized(this) {
            val value = pendingFrameComplete
            pendingFrameComplete = null
            value
        }
        complete?.invoke()
    }
}
