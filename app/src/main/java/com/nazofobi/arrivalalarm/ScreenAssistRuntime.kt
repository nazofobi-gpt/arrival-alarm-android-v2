package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Process-local bridge between the user-consented capture foreground service
 * and the already connected Realtime sender. Nothing is persisted.
 */
object ScreenAssistRuntimeBridge {
    @Volatile
    private var sender: ScreenAssistRealtimeSender? = null

    @Volatile
    private var projectionStopped: (() -> Unit)? = null

    @Volatile
    private var captureError: ((Throwable) -> Unit)? = null

    fun attach(realtimeSender: ScreenAssistRealtimeSender) {
        sender = realtimeSender
    }

    fun detach(realtimeSender: ScreenAssistRealtimeSender? = null) {
        if (realtimeSender == null || sender === realtimeSender) {
            sender = null
        }
    }

    fun observe(
        onProjectionStopped: () -> Unit,
        onCaptureError: (Throwable) -> Unit,
    ) {
        projectionStopped = onProjectionStopped
        captureError = onCaptureError
    }

    fun clearObserver() {
        projectionStopped = null
        captureError = null
    }

    internal fun dispatch(frame: ScreenAssistEncodedFrame, complete: () -> Unit) {
        val active = sender
        if (active == null) {
            complete()
            return
        }
        try {
            active.sendFrame(frame.jpegBytes, complete)
        } catch (error: Throwable) {
            complete()
            captureError?.invoke(error)
        }
    }

    internal fun projectionStopped() {
        projectionStopped?.invoke()
    }

    internal fun captureError(error: Throwable) {
        captureError?.invoke(error)
    }
}

/**
 * Activity-facing service command boundary. The MediaProjection grant remains
 * memory-only and is passed directly to the foreground service for this session.
 */
class ScreenAssistCaptureRuntime(private val context: Context) {
    fun start(
        grant: ScreenAssistGrant,
        width: Int,
        height: Int,
        densityDpi: Int,
    ) {
        require(width > 0 && height > 0 && densityDpi > 0)
        launch(
            Intent(context, ScreenAssistCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, grant.resultCode)
                .putExtra(EXTRA_RESULT_DATA, grant.data)
                .putExtra(EXTRA_WIDTH, width)
                .putExtra(EXTRA_HEIGHT, height)
                .putExtra(EXTRA_DENSITY_DPI, densityDpi),
        )
    }

    fun pause() = launch(command(ACTION_PAUSE))

    fun resume() = launch(command(ACTION_RESUME))

    fun stop() {
        context.stopService(Intent(context, ScreenAssistCaptureService::class.java))
    }

    private fun command(action: String) =
        Intent(context, ScreenAssistCaptureService::class.java).setAction(action)

    private fun launch(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    companion object {
        internal const val ACTION_START = "com.nazofobi.arrivalalarm.screenassist.START"
        internal const val ACTION_PAUSE = "com.nazofobi.arrivalalarm.screenassist.PAUSE"
        internal const val ACTION_RESUME = "com.nazofobi.arrivalalarm.screenassist.RESUME"
        internal const val ACTION_STOP = "com.nazofobi.arrivalalarm.screenassist.STOP"

        internal const val EXTRA_RESULT_CODE = "screen_assist_result_code"
        internal const val EXTRA_RESULT_DATA = "screen_assist_result_data"
        internal const val EXTRA_WIDTH = "screen_assist_width"
        internal const val EXTRA_HEIGHT = "screen_assist_height"
        internal const val EXTRA_DENSITY_DPI = "screen_assist_density_dpi"
    }
}
