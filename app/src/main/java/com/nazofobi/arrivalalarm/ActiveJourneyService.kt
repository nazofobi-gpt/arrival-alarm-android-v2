package com.nazofobi.arrivalalarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
import android.os.SystemClock
import java.util.concurrent.Executors
import kotlin.math.roundToInt

object ActiveJourneyPermissions {
    fun hasRuntimeGrants(context: Context): Boolean {
        val hasLocation =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNotifications =
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return hasLocation && hasNotifications
    }

    fun hasRequired(context: Context): Boolean {
        if (!hasRuntimeGrants(context)) return false
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        if (!isDeviceLocationEnabledForTracking(manager)) return false
        // Do not report a trackable journey if the only enabled provider is passive.
        return runCatching {
            ActiveJourneyLocationProviderSelection.choose(manager.getProviders(true)) != null
        }.getOrDefault(false)
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
    private fun locationServicesEnabled(): Boolean =
        runCatching { isDeviceLocationEnabledForTracking(locationManager) }.getOrDefault(false)
    private val completionExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var providerReceiverRegistered = false
    private val providerChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                retryProviderRegistration()
            }
        }
    }
    private val stateMonitor = object : Runnable {
        override fun run() {
            // Keep an armed journey alive when a provider disappears; stop only
            // when the journey ends or the user no longer grants runtime access.
            if (!arrivalHandled) {
                val registeredProviderEnabled = registeredProvider?.let { provider ->
                    runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
                } == true
                when (LocationProviderRecovery.decide(
                    graph.controller.state.phase,
                    locationRegistered,
                    registeredProviderEnabled,
                    ActiveJourneyPermissions.hasRuntimeGrants(this@ActiveJourneyService),
                    locationServicesEnabled(),
                )) {
                    LocationProviderRecovery.Decision.STOP -> {
                        stopSelf()
                        return
                    }
                    LocationProviderRecovery.Decision.DISARM_AND_STOP -> {
                        disarmAndStopAfterLocationLoss()
                        return
                    }
                    LocationProviderRecovery.Decision.RETRY -> {
                        graph.connectorPort.resetArrivalConfirmation()
                        removeLocationUpdates()
                        registerLocationUpdates()
                        updateTrackingNotification(null, accuracyDegraded = true)
                    }
                    LocationProviderRecovery.Decision.KEEP -> Unit
                }
            }
            mainHandler.postDelayed(this, STATE_MONITOR_MILLIS)
        }
    }
    @Volatile private var arrivalHandled = false
    private var locationRegistered = false
    private var registeredProvider: String? = null

    override fun onCreate() {
        super.onCreate()
        graph = ArrivalAlarmRuntimeGraph.get(applicationContext)
        graph.connectorPort.resetArrivalConfirmation()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        graph.connectorRuntimeCoordinator.acquire(SERVICE_RUNTIME_OWNER)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CANCEL) {
            // The notification action must disarm the authoritative journey state;
            // stopping only the service would leave a silently armed alarm behind.
            if (graph.controller.state.phase == JourneyPhase.ARMED) {
                graph.connectorPort.cancelArrivalAlarm()
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (graph.controller.state.phase != JourneyPhase.ARMED) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android requires system-wide location services ON for a location FGS.
        // With that prerequisite met, a transiently missing enabled provider
        // must not kill the already-armed journey before recovery can run.
        // New journey initiation still uses the stricter hasRequired() UI gate.
        when (LocationProviderRecovery.startupDecision(
            journeyPhase = graph.controller.state.phase,
            hasRuntimeGrants = ActiveJourneyPermissions.hasRuntimeGrants(this),
            locationServicesEnabled = locationServicesEnabled(),
        )) {
            LocationProviderRecovery.Decision.STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            LocationProviderRecovery.Decision.DISARM_AND_STOP -> {
                disarmAndStopAfterLocationLoss()
                return START_NOT_STICKY
            }
            LocationProviderRecovery.Decision.KEEP,
            LocationProviderRecovery.Decision.RETRY -> Unit
        }

        if (!startLocationForeground()) {
            // A rejected location FGS cannot protect an armed destination alarm.
            // Never leave the controller ARMED with no tracking service.
            when (LocationProviderRecovery.foregroundStartFailureDecision(graph.controller.state.phase)) {
                LocationProviderRecovery.Decision.DISARM_AND_STOP -> disarmAndStopAfterLocationLoss()
                else -> stopSelf()
            }
            return START_NOT_STICKY
        }
        // Provider registration can fail after the initial availability check.
        // Keep the armed foreground journey alive and retry through the existing
        // state monitor, but stop promptly if grants are revoked or the trip ends.
        val registered = registerLocationUpdates()
        val providerEnabled = registeredProvider?.let { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        } == true
        when (LocationProviderRecovery.decide(
            graph.controller.state.phase,
            registered,
            providerEnabled,
            ActiveJourneyPermissions.hasRuntimeGrants(this),
            locationServicesEnabled(),
        )) {
            LocationProviderRecovery.Decision.STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            LocationProviderRecovery.Decision.DISARM_AND_STOP -> {
                disarmAndStopAfterLocationLoss()
                return START_NOT_STICKY
            }
            LocationProviderRecovery.Decision.RETRY -> {
                updateTrackingNotification(null, accuracyDegraded = true)
            }
            LocationProviderRecovery.Decision.KEEP -> Unit
        }
        observeProviderChanges()
        mainHandler.removeCallbacks(stateMonitor)
        mainHandler.postDelayed(stateMonitor, STATE_MONITOR_MILLIS)
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        // LocationManager can deliver a queued fix after cancellation or arrival.
        // Never update terminal journey state or publish a stale tracking notification.
        if (!LocationProviderRecovery.acceptsLocationCallback(graph.controller.state.phase, arrivalHandled)) return
        if (!ArrivalLocationQuality.hasValidCoordinates(location.latitude, location.longitude)) {
            graph.connectorPort.resetArrivalConfirmation()
            return
        }
        val destination = graph.controller.state.destination ?: run {
            graph.connectorPort.resetArrivalConfirmation()
            return
        }
        val distanceMeters = ArrivalProgressController.distanceMeters(
            GeoPoint(location.latitude, location.longitude),
            destination,
        )
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val evidence = ArrivalLocationEvidence(
            distanceMeters = distanceMeters,
            horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            ageMillis = (nowNanos - location.elapsedRealtimeNanos) / 1_000_000L,
        )
        if (!ArrivalLocationQuality.isUsableForTracking(evidence)) {
            graph.connectorPort.resetArrivalConfirmation()
            updateTrackingNotification(null, accuracyDegraded = true)
            return
        }
        graph.connectorPort.updateDistanceToDestination(
            distanceMeters,
            allowArrival = ArrivalLocationQuality.confirmsArrival(evidence),
            fixElapsedRealtimeNanos = location.elapsedRealtimeNanos,
        )
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

    override fun onProviderEnabled(provider: String) {
        retryProviderRegistration()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider != registeredProvider) return
        graph.connectorPort.resetArrivalConfirmation()
        // Enforce the same fail-closed permission/system-location policy as the
        // broadcast receiver and the periodic monitor before re-registering.
        retryProviderRegistration()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        graph.connectorPort.resetArrivalConfirmation()
        mainHandler.removeCallbacks(stateMonitor)
        if (providerReceiverRegistered) {
            runCatching { unregisterReceiver(providerChangeReceiver) }
            providerReceiverRegistered = false
        }
        removeLocationUpdates()
        graph.connectorRuntimeCoordinator.release(SERVICE_RUNTIME_OWNER)
        completionExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun disarmAndStopAfterLocationLoss() {
        // Stopping the FGS alone would leave a silently armed alarm with no location updates.
        if (graph.controller.state.phase == JourneyPhase.ARMED) {
            graph.connectorPort.cancelArrivalAlarm()
        }
        stopSelf()
    }

    private fun retryProviderRegistration() {
        if (arrivalHandled) return
        graph.connectorPort.resetArrivalConfirmation()
        val registeredProviderEnabled = registeredProvider?.let { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        } == true
        when (LocationProviderRecovery.decide(
            graph.controller.state.phase,
            locationRegistered,
            registeredProviderEnabled,
            ActiveJourneyPermissions.hasRuntimeGrants(this),
            locationServicesEnabled(),
        )) {
            LocationProviderRecovery.Decision.STOP -> stopSelf()
            LocationProviderRecovery.Decision.DISARM_AND_STOP -> disarmAndStopAfterLocationLoss()
            LocationProviderRecovery.Decision.KEEP -> Unit
            LocationProviderRecovery.Decision.RETRY -> {
                removeLocationUpdates()
                registerLocationUpdates()
                // Provider availability is not evidence of a fresh, accurate GPS fix.
                updateTrackingNotification(null, accuracyDegraded = true)
            }
        }
    }

    private fun observeProviderChanges() {
        if (providerReceiverRegistered) return
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        providerReceiverRegistered = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(providerChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(providerChangeReceiver, filter)
            }
            true
        }.getOrDefault(false)
        // The existing five-second monitor remains the fallback.
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
            registeredProvider = provider
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun removeLocationUpdates() {
        if (locationRegistered) runCatching { locationManager.removeUpdates(this) }
        locationRegistered = false
        registeredProvider = null
    }

    private fun enabledLocationProvider(): String? {
        val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
        return ActiveJourneyLocationProviderSelection.choose(providers)
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
        } catch (_: IllegalStateException) {
            // Includes Android foreground-service start restrictions.
            false
        }
    }

    private fun updateTrackingNotification(distanceMeters: Double?, accuracyDegraded: Boolean = false) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(TRACKING_NOTIFICATION_ID, trackingNotification(distanceMeters, accuracyDegraded))
    }

    private fun trackingNotification(distanceMeters: Double?, accuracyDegraded: Boolean = false): Notification {
        val text = when {
            accuracyDegraded -> getString(R.string.active_tracking_degraded)
            distanceMeters == null -> getString(R.string.active_tracking_in_progress)
            else -> getString(R.string.active_tracking_distance, distanceMeters.roundToInt())
        }
        return notificationBuilder(TRACKING_CHANNEL_ID)
            .setContentTitle(getString(R.string.active_tracking_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent())
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.cancel_alarm),
                    cancelAlarmPendingIntent(),
                ).build()
            )
            .build()
    }

    private fun postArrivalNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            ARRIVAL_NOTIFICATION_ID,
            notificationBuilder(ARRIVAL_CHANNEL_ID)
                .setContentTitle(getString(R.string.arrival_notification_title))
                .setContentText(getString(R.string.arrival_notification_body))
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

    private fun cancelAlarmPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            CANCEL_REQUEST_CODE,
            cancellationIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                TRACKING_CHANNEL_ID,
                getString(R.string.active_tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ARRIVAL_CHANNEL_ID,
                getString(R.string.arrival_alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    companion object {
        const val ACTION_START = "com.nazofobi.arrivalalarm.action.START_ACTIVE_JOURNEY"
        const val ACTION_STOP = "com.nazofobi.arrivalalarm.action.STOP_ACTIVE_JOURNEY"
        const val ACTION_CANCEL = "com.nazofobi.arrivalalarm.action.CANCEL_ACTIVE_JOURNEY"
        const val SERVICE_RUNTIME_OWNER = "active-journey-service"
        private const val TRACKING_CHANNEL_ID = "arrival_tracking"
        private const val ARRIVAL_CHANNEL_ID = "arrival_alerts"
        private const val TRACKING_NOTIFICATION_ID = 4101
        private const val ARRIVAL_NOTIFICATION_ID = 4102
        private const val CANCEL_REQUEST_CODE = 4103
        private const val MIN_UPDATE_MILLIS = 10_000L
        private const val MIN_UPDATE_METERS = 25f
        private const val STATE_MONITOR_MILLIS = 5_000L

        internal fun cancellationIntent(context: Context): Intent =
            Intent(context, ActiveJourneyService::class.java).setAction(ACTION_CANCEL)

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
