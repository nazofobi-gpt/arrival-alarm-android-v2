package com.nazofobi.arrivalalarm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consent-bound MediaProjection capture engine.
 *
 * One ScreenAssistGrant creates one MediaProjection session. Frames are kept in
 * memory only, admitted through ScreenAssistFramePolicy, encoded, and handed to
 * the caller with an explicit completion callback so network backpressure stays
 * bounded.
 */
class ScreenAssistCaptureEngine(
    context: Context,
    private val framePolicy: ScreenAssistFramePolicy = ScreenAssistFramePolicy(),
    private val frameSink: (ScreenAssistEncodedFrame, () -> Unit) -> Unit,
    private val onProjectionStopped: () -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
    private val nowMs: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val projectionManager =
        context.getSystemService(MediaProjectionManager::class.java)
    private val worker = HandlerThread("screen-assist-capture").apply { start() }
    private val handler = Handler(worker.looper)

    @Volatile private var paused = false
    @Volatile private var networkDegraded = false

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val releasing = AtomicBoolean(false)

    @Synchronized
    fun start(
        grant: ScreenAssistGrant,
        width: Int,
        height: Int,
        densityDpi: Int,
    ) {
        require(width > 0 && height > 0 && densityDpi > 0)
        check(projection == null) { "Screen Assist capture session already active" }

        val mediaProjection =
            projectionManager.getMediaProjection(grant.resultCode, grant.data)
                ?: error("MediaProjection grant could not create a capture session")
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post {
                    releaseResources(stopProjection = false)
                    onProjectionStopped()
                }
            }
        }

        val reader = createReader(width, height)
        mediaProjection.registerCallback(callback, handler)

        try {
            val display = mediaProjection.createVirtualDisplay(
                "ScreenAssist",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
            projection = mediaProjection
            projectionCallback = callback
            imageReader = reader
            virtualDisplay = display
            paused = false
            framePolicy.reset()
        } catch (error: Throwable) {
            reader.close()
            runCatching { mediaProjection.unregisterCallback(callback) }
            runCatching { mediaProjection.stop() }
            throw error
        }
    }

    fun pause() {
        paused = true
        framePolicy.reset()
    }

    fun resume() {
        paused = false
    }

    fun setNetworkDegraded(value: Boolean) {
        networkDegraded = value
    }

    /**
     * Keep the same MediaProjection and VirtualDisplay session across rotation.
     * Android 14+ permits resizing the existing VirtualDisplay instead of
     * calling createVirtualDisplay a second time.
     */
    @Synchronized
    fun resize(width: Int, height: Int, densityDpi: Int) {
        require(width > 0 && height > 0 && densityDpi > 0)
        val display = virtualDisplay ?: return
        val replacement = createReader(width, height)
        val previous = imageReader

        display.resize(width, height, densityDpi)
        display.surface = replacement.surface
        imageReader = replacement
        previous?.setOnImageAvailableListener(null, null)
        previous?.close()
        framePolicy.reset()
    }

    @Synchronized
    fun stop() {
        releaseResources(stopProjection = true)
    }

    override fun close() {
        stop()
        worker.quitSafely()
    }

    private fun createReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ source ->
                handleLatestImage(source)
            }, handler)
        }

    private fun handleLatestImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            if (paused || releasing.get()) return

            val decision = framePolicy.decide(nowMs(), networkDegraded)
            if (!decision.send) return

            val bitmap = image.toBitmap()
            try {
                val encoded = ScreenAssistFrameEncoder(
                    maxDimensionPx = decision.maxEdgePx,
                    jpegQuality = decision.jpegQuality,
                ).encode(bitmap)

                try {
                    frameSink(encoded) {
                        framePolicy.onFrameFinished()
                    }
                } catch (error: Throwable) {
                    framePolicy.onFrameFinished()
                    onError(error)
                }
            } finally {
                bitmap.recycle()
            }
        } catch (error: Throwable) {
            framePolicy.onFrameFinished()
            onError(error)
        } finally {
            image.close()
        }
    }

    @Synchronized
    private fun releaseResources(stopProjection: Boolean) {
        if (!releasing.compareAndSet(false, true)) return
        try {
            framePolicy.reset()
            paused = true

            imageReader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.release()
            imageReader?.close()

            virtualDisplay = null
            imageReader = null

            val currentProjection = projection
            val currentCallback = projectionCallback
            projection = null
            projectionCallback = null

            if (currentProjection != null && currentCallback != null) {
                runCatching { currentProjection.unregisterCallback(currentCallback) }
            }
            if (stopProjection) {
                runCatching { currentProjection?.stop() }
            }
        } finally {
            releasing.set(false)
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.first()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        require(pixelStride > 0 && rowStride > 0)

        val paddedWidth = rowStride / pixelStride
        val padded = Bitmap.createBitmap(
            paddedWidth.coerceAtLeast(width),
            height,
            Bitmap.Config.ARGB_8888,
        )

        plane.buffer.rewind()
        padded.copyPixelsFromBuffer(plane.buffer)

        if (padded.width == width) return padded

        return Bitmap.createBitmap(padded, 0, 0, width, height).also {
            padded.recycle()
        }
    }
}
