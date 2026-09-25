package com.nazofobi.arrivalalarm

import android.content.Context
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Native Android WebRTC transport for OpenAI Realtime.
 *
 * The client receives only a short-lived ephemeral token from the application's
 * broker. Standard OpenAI API keys never cross this boundary.
 */
class ScreenAssistOpenAiWebRtcSocket(
    context: Context,
    private val endpoint: String = "https://api.openai.com/v1/realtime/calls",
    private val connectTimeoutSeconds: Long = 15,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) : ScreenAssistRealtimeSocket {
    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var eventListener: (String) -> Unit = {}

    @Volatile
    private var connectionFailure: String? = null

    init {
        require(endpoint == "https://api.openai.com/v1/realtime/calls") {
            "Realtime WebRTC endpoint must use the official HTTPS calls endpoint"
        }
        require(connectTimeoutSeconds in 1..60)
        ensureWebRtcInitialized(appContext)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    override fun setEventListener(listener: (String) -> Unit) {
        eventListener = listener
    }

    override fun connect(ephemeralToken: String) {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Realtime WebRTC connection must be established off the main thread"
        }
        require(ephemeralToken.isNotBlank()) { "Ephemeral token is required" }
        check(peerConnection == null) { "Realtime WebRTC socket already connected" }

        val openLatch = CountDownLatch(1)
        val observer = peerObserver(openLatch)
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = checkNotNull(factory.createPeerConnection(config, observer)) {
            "Unable to create WebRTC peer connection"
        }
        peerConnection = peer

        val channel = peer.createDataChannel("oai-events", DataChannel.Init())
        dataChannel = channel
        channel.registerObserver(dataObserver(openLatch))

        try {
            val offer = createOffer(peer)
            setLocalDescription(peer, offer)
            val answerSdp = exchangeSdp(offer.description, ephemeralToken)
            setRemoteDescription(
                peer,
                SessionDescription(SessionDescription.Type.ANSWER, answerSdp),
            )

            if (!openLatch.await(connectTimeoutSeconds, TimeUnit.SECONDS)) {
                error("Timed out waiting for Realtime WebRTC data channel")
            }
            connectionFailure?.let { error(it) }
            check(channel.state() == DataChannel.State.OPEN) {
                "Realtime WebRTC data channel did not open"
            }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    override fun send(text: String) {
        val channel = checkNotNull(dataChannel) { "Realtime data channel is not connected" }
        check(channel.state() == DataChannel.State.OPEN) { "Realtime data channel is not open" }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        check(channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))) {
            "Realtime data channel rejected event"
        }
    }

    override fun close() {
        dataChannel?.unregisterObserver()
        dataChannel?.close()
        dataChannel?.dispose()
        dataChannel = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        connectionFailure = null
    }

    private fun createOffer(peer: PeerConnection): SessionDescription {
        val observer = BlockingSdpObserver()
        peer.createOffer(observer, MediaConstraints())
        check(observer.await(connectTimeoutSeconds)) {
            "Timed out creating Realtime WebRTC offer"
        }
        observer.failure?.let { error("Failed to create Realtime WebRTC offer: $it") }
        return checkNotNull(observer.created) { "Realtime WebRTC offer was empty" }
    }

    private fun setLocalDescription(peer: PeerConnection, description: SessionDescription) {
        val observer = BlockingSdpObserver()
        peer.setLocalDescription(observer, description)
        check(observer.await(connectTimeoutSeconds)) {
            "Timed out setting local Realtime SDP"
        }
        observer.failure?.let { error("Failed to set local Realtime SDP: $it") }
    }

    private fun setRemoteDescription(peer: PeerConnection, description: SessionDescription) {
        val observer = BlockingSdpObserver()
        peer.setRemoteDescription(observer, description)
        check(observer.await(connectTimeoutSeconds)) {
            "Timed out setting remote Realtime SDP"
        }
        observer.failure?.let { error("Failed to set remote Realtime SDP: $it") }
    }

    private fun exchangeSdp(offerSdp: String, ephemeralToken: String): String {
        val connection = connectionFactory(URL(endpoint))
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutSeconds.toInt() * 1_000
            connection.readTimeout = connectTimeoutSeconds.toInt() * 1_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $ephemeralToken")
            connection.setRequestProperty("Content-Type", "application/sdp")
            connection.outputStream.use {
                it.write(offerSdp.toByteArray(StandardCharsets.UTF_8))
            }

            val status = connection.responseCode
            val body = if (status in 200..299) {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                    .orEmpty()
            }
            check(status in 200..299) {
                "Realtime WebRTC SDP exchange failed: HTTP $status"
            }
            check(body.isNotBlank()) { "Realtime WebRTC SDP answer was empty" }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun dataObserver(openLatch: CountDownLatch) = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) = Unit

        override fun onStateChange() {
            when (dataChannel?.state()) {
                DataChannel.State.OPEN -> openLatch.countDown()
                DataChannel.State.CLOSED -> {
                    connectionFailure = "Realtime WebRTC data channel closed"
                    openLatch.countDown()
                }
                else -> Unit
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            if (buffer.binary) return
            val view = buffer.data.slice()
            val bytes = ByteArray(view.remaining())
            view.get(bytes)
            eventListener(String(bytes, StandardCharsets.UTF_8))
        }
    }

    private fun peerObserver(openLatch: CountDownLatch) = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            if (
                newState == PeerConnection.IceConnectionState.FAILED ||
                newState == PeerConnection.IceConnectionState.CLOSED
            ) {
                connectionFailure = "Realtime WebRTC ICE connection failed"
                openLatch.countDown()
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) = Unit

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            if (
                newState == PeerConnection.PeerConnectionState.FAILED ||
                newState == PeerConnection.PeerConnectionState.CLOSED
            ) {
                connectionFailure = "Realtime WebRTC peer connection failed"
                openLatch.countDown()
            }
        }
    }

    private class BlockingSdpObserver : SdpObserver {
        private val latch = CountDownLatch(1)

        @Volatile
        var created: SessionDescription? = null
            private set

        @Volatile
        var failure: String? = null
            private set

        override fun onCreateSuccess(description: SessionDescription) {
            created = description
            latch.countDown()
        }

        override fun onSetSuccess() {
            latch.countDown()
        }

        override fun onCreateFailure(error: String) {
            failure = error
            latch.countDown()
        }

        override fun onSetFailure(error: String) {
            failure = error
            latch.countDown()
        }

        fun await(timeoutSeconds: Long): Boolean =
            latch.await(timeoutSeconds, TimeUnit.SECONDS)
    }

    companion object {
        private val initialized = AtomicBoolean(false)

        private fun ensureWebRtcInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions(),
                )
            }
        }
    }
}
