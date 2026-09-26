package com.nazofobi.arrivalalarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.math.roundToInt

object ActiveJourneyPermissions {
    fun hasRequired(context: Context): Boolean {
        val hasLocation =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNotifications =
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasProvider = runCatching { manager.getProviders(true).isNotEmpty() }.getOrDefault(false)
        return hasLocation && hasNotifications && hasProvider
    }

    fun runtimePermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}

/**
 * User-started foreground lifecycle for an armed journey.
 *
 * This service performs real location tracking, updates the authoritative journey distance,
 * and keeps the connector runtime leased while the active alarm needs background continuity.
 * It is intentionally a location FGS rather than a dataSync FGS.
 */
class ActiveJourneyService : Service(), LocationListener {
    private lateinit var graph: ArrivalAlarmProcessGraph
    private lateinit var locationManager: LocationManager
    private val completionExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateMonitor = object : Runnable {
        override fun run() {
            if (!arrivalHandled && graph.controller.state.phase != JourneyPhase.ARMED) {
                stopSelf()
                return
            }
            mainHandler.postDelayed(this, STATE_MONITOR_MILLIS)
        }
    }
    @Volatile private var arrivalHandled = false
    private var locationRegistered = false

    override fun onCreate() {
        super.onCreate()
        graph = ArrivalAlarmRuntimeGraph.get(applicationContext)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        graph.connectorRuntimeCoordinator.acquire(SERVICE_RUNTIME_OWNER)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (graph.controller.state.phase != JourneyPhase.ARMED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ActiveJourneyPermissions.hasRequired(this) || enabledLocationProvider() == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!startLocationForeground() || !registerLocationUpdates()) {
            stopSelf()
            return START_NOT_STICKY
        }
        mainHandler.removeCallbacks(stateMonitor)
        mainHandler.postDelayed(stateMonitor, STATE_MONITOR_MILLIS)
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        val destination = graph.controller.state.destination ?: return
        val distanceMeters = ArrivalProgressController.distanceMeters(
            GeoPoint(location.latitude, location.longitude),
            destination,
        )
        graph.connectorPort.updateDistanceToDestination(distanceMeters)
        updateTrackingNotification(distanceMeters)

        if (graph.controller.state.phase == JourneyPhase.ARRIVED && !arrivalHandled) {
            arrivalHandled = true
            removeLocationUpdates()
            completionExecutor.execute {
                graph.connectorRuntimeCoordinator.syncNow()
                postArrivalNotification()
                stopSelf()
            }
        }
    }

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) {
        removeLocationUpdates()
        if (!registerLocationUpdates()) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacks(stateMonitor)
        removeLocationUpdates()
        graph.connectorRuntimeCoordinator.release(SERVICE_RUNTIME_OWNER)
        completionExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun registerLocationUpdates(): Boolean {
        val provider = enabledLocationProvider() ?: return false
        return try {
            locationManager.requestLocationUpdates(
                provider,
                MIN_UPDATE_MILLIS,
                MIN_UPDATE_METERS,
                this,
                Looper.getMainLooper(),
            )
            locationRegistered = true
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun removeLocationUpdates() {
        if (!locationRegistered) return
        runCatching { locationManager.removeUpdates(this) }
        locationRegistered = false
    }

    private fun enabledLocationProvider(): String? {
        val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
        return when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> providers.firstOrNull()
        }
    }

    private fun startLocationForeground(): Boolean {
        val notification = trackingNotification(graph.controller.state.distanceMeters)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    TRACKING_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(TRACKING_NOTIFICATION_ID, notification)
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun updateTrackingNotification(distanceMeters: Double) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(TRACKING_NOTIFICATION_ID, trackingNotification(distanceMeters))
    }

    private fun trackingNotification(distanceMeters: Double?): Notification {
        val text = if (distanceMeters == null) {
            "Konum takip ediliyor"
        } else {
            "Hedefe yaklaşık " + distanceMeters.roundToInt() + " m kaldı"
        }
        return notificationBuilder(TRACKING_CHANNEL_ID)
            .setContentTitle("Varış alarmı etkin")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent())
            .build()
    }

    private fun postArrivalNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            ARRIVAL_NOTIFICATION_ID,
            notificationBuilder(ARRIVAL_CHANNEL_ID)
                .setContentTitle("Varış noktasına ulaşıldı")
                .setContentText("Varış alarmı tamamlandı.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(openAppPendingIntent())
                .build(),
        )
    }

    private fun notificationBuilder(channelId: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId)
        else Notification.Builder(this)

    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                TRACKING_CHANNEL_ID,
                "Aktif varış takibi",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ARRIVAL_CHANNEL_ID,
                "Varış uyarıları",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    companion object {
        const val ACTION_START = "com.nazofobi.arrivalalarm.action.START_ACTIVE_JOURNEY"
        const val ACTION_STOP = "com.nazofobi.arrivalalarm.action.STOP_ACTIVE_JOURNEY"
        const val SERVICE_RUNTIME_OWNER = "active-journey-service"
        private const val TRACKING_CHANNEL_ID = "arrival_tracking"
        private const val ARRIVAL_CHANNEL_ID = "arrival_alerts"
        private const val TRACKING_NOTIFICATION_ID = 4101
        private const val ARRIVAL_NOTIFICATION_ID = 4102
        private const val MIN_UPDATE_MILLIS = 10_000L
        private const val MIN_UPDATE_METERS = 25f
        private const val STATE_MONITOR_MILLIS = 5_000L

        fun tryStart(context: Context): Boolean {
            if (!ActiveJourneyPermissions.hasRequired(context)) return false
            val intent = Intent(context, ActiveJourneyService::class.java).setAction(ACTION_START)
            return try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
                true
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ActiveJourneyService::class.java))
        }
    }
}
