package com.nazofobi.arrivalalarm

interface ScreenAssistRealtimeSocket {
    fun connect(ephemeralToken: String)
    fun send(text: String)
    fun close()
}

class ScreenAssistRealtimeSender(
    private val session: ScreenAssistRealtimeSession,
    private val socket: ScreenAssistRealtimeSocket,
    private val imageBuilder: ScreenAssistRealtimeImage = ScreenAssistRealtimeImage(),
) {
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

    fun sendFrame(jpeg: ByteArray): Boolean {
        if (session.phase != ScreenAssistRealtimePhase.CONNECTED) return false
        return try {
            socket.send(imageBuilder.buildConversationItem(jpeg))
            true
        } catch (_: Exception) {
            session.networkLost()
            false
        }
    }

    fun stop() {
        try { socket.close() } finally { session.stop() }
    }
}
