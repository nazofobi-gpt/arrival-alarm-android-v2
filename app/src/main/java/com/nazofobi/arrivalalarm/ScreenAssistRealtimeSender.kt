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
    private val toolHandler: ScreenAssistRealtimeToolHandler? = null,
    private val onToolConfirmationRequired: (ScreenAssistPendingToolCall) -> Unit = {},
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
            toolHandler?.let { socket.send(it.sessionUpdateEvent()) }
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

    fun resolveToolConfirmation(
        pending: ScreenAssistPendingToolCall,
        confirmed: Boolean,
    ): Boolean {
        val handler = toolHandler ?: return false
        return sendToolOutputs(
            outcomes = listOf(handler.resolvePending(pending, confirmed)),
            requestFollowUp = true,
        )
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

        if (type == "response.done") {
            val outcomes = toolHandler?.handleServerEvent(raw).orEmpty()
            if (outcomes.isEmpty()) {
                finishPendingFrame()
            } else {
                val immediate = outcomes.filterIsInstance<ScreenAssistToolOutcome.Immediate>()
                val pending = outcomes.filterIsInstance<ScreenAssistToolOutcome.AwaitConfirmation>()

                val sent = sendToolOutputs(
                    outcomes = immediate,
                    requestFollowUp = pending.isEmpty(),
                )
                if (!sent) return

                pending.forEach { onToolConfirmationRequired(it.pending) }
            }
        } else if (type == "error" || type == "session.closed") {
            finishPendingFrame()
        }

        if (type == "error") {
            session.networkLost()
        }
    }

    private fun sendToolOutputs(
        outcomes: List<ScreenAssistToolOutcome.Immediate>,
        requestFollowUp: Boolean,
    ): Boolean {
        if (outcomes.isEmpty() && !requestFollowUp) return true
        return try {
            outcomes.forEach { outcome ->
                socket.send(
                    JSONObject()
                        .put("type", "conversation.item.create")
                        .put(
                            "item",
                            JSONObject()
                                .put("type", "function_call_output")
                                .put("call_id", outcome.callId)
                                .put("output", outcome.outputJson),
                        )
                        .toString(),
                )
            }
            if (requestFollowUp) {
                socket.send(JSONObject().put("type", "response.create").toString())
            }
            true
        } catch (_: Exception) {
            finishPendingFrame()
            session.networkLost()
            false
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
