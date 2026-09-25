package com.nazofobi.arrivalalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground-service boundary for user-consented MediaProjection sessions.
 *
 * The Activity obtains a fresh grant first. This service promotes itself to a
 * mediaProjection foreground service before creating MediaProjection, owns the
 * capture engine lifecycle, and never persists the grant or raw frames.
 */
class ScreenAssistCaptureService : Service() {
    private var engine: ScreenAssistCaptureEngine? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ekran Asistanı", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()

        when (intent?.action) {
            ScreenAssistCaptureRuntime.ACTION_START -> startCapture(intent)
            ScreenAssistCaptureRuntime.ACTION_PAUSE -> engine?.pause()
            ScreenAssistCaptureRuntime.ACTION_RESUME -> engine?.resume()
            ScreenAssistCaptureRuntime.ACTION_STOP -> stopCapture()
            else -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine?.close()
        engine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Ekran Asistanı etkin")
            .setContentText("Ekran paylaşımı yalnız açık oturum süresince sürer")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(intent: Intent) {
        val resultCode = intent.getIntExtra(
            ScreenAssistCaptureRuntime.EXTRA_RESULT_CODE,
            Int.MIN_VALUE,
        )
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                ScreenAssistCaptureRuntime.EXTRA_RESULT_DATA,
                Intent::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(ScreenAssistCaptureRuntime.EXTRA_RESULT_DATA)
        }
        val width = intent.getIntExtra(ScreenAssistCaptureRuntime.EXTRA_WIDTH, 0)
        val height = intent.getIntExtra(ScreenAssistCaptureRuntime.EXTRA_HEIGHT, 0)
        val densityDpi = intent.getIntExtra(ScreenAssistCaptureRuntime.EXTRA_DENSITY_DPI, 0)

        if (resultCode == Int.MIN_VALUE || resultData == null ||
            width <= 0 || height <= 0 || densityDpi <= 0
        ) {
            ScreenAssistRuntimeBridge.captureError(
                IllegalArgumentException("Invalid Screen Assist capture grant or display metrics"),
            )
            stopCapture()
            return
        }

        engine?.close()
        val next = ScreenAssistCaptureEngine(
            context = this,
            frameSink = ScreenAssistRuntimeBridge::dispatch,
            onProjectionStopped = {
                ScreenAssistRuntimeBridge.projectionStopped()
                stopSelf()
            },
            onError = ScreenAssistRuntimeBridge::captureError,
        )

        try {
            next.start(
                ScreenAssistGrant(resultCode, resultData),
                width,
                height,
                densityDpi,
            )
            engine = next
        } catch (error: Throwable) {
            next.close()
            ScreenAssistRuntimeBridge.captureError(error)
            stopCapture()
        }
    }

    private fun stopCapture() {
        engine?.close()
        engine = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "screen_assist_capture"
        private const val NOTIFICATION_ID = 150
    }
}
