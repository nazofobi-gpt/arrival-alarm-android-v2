package com.nazofobi.arrivalalarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Informational opt-in reminder. WorkManager is NOT an exact alarm: Doze, battery
 * policies, notification permission or stopped app state may delay/suppress delivery.
 * It never starts location tracking, changes a journey or arms an arrival alarm.
 */
internal fun plannedWalkReminderDelayMillis(
    option: RouteOption,
    nowMillis: Long,
    safetyBufferMinutes: Int = 0,
): Long? {
    val target = plannedWalkStartEpochMillis(option, safetyBufferMinutes) ?: return null
    val delay = runCatching { Math.subtractExact(target, nowMillis) }.getOrNull() ?: return null
    // A selected offline GTFS journey can be planned up to seven days ahead.
    // WorkManager stays best-effort; a delayed job is discarded at delivery time.
    return delay.takeIf { it in 60_000L..604_800_000L }
}

internal data class PlannedWalkReminderDetails(val stopName: String, val startMillis: Long)

internal class PlannedWalkReminderScheduler(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The one opt-in reminder may belong to a previously selected route. */
    fun scheduledRouteId(): String? =
        preferences.getString(KEY_ROUTE, null)?.takeIf {
            it.isNotBlank() && !preferences.getString(KEY_TOKEN, null).isNullOrEmpty()
        }

    fun isScheduledForRoute(routeId: String): Boolean =
        routeId.isNotBlank() && scheduledRouteId() == routeId

    /** Details are shown only when atomically bound to the current opt-in token. */
    fun scheduledDetails(): PlannedWalkReminderDetails? {
        val token = preferences.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: return null
        if (scheduledRouteId() == null ||
            preferences.getString(KEY_DETAILS_TOKEN, null) != token) return null
        val start = preferences.getLong(KEY_START, -1L).takeIf { it > 0L }
            ?: return null
        val stop = preferences.getString(KEY_STOP, null)?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 80 } ?: return null
        return PlannedWalkReminderDetails(stop, start)
    }

    /** Call on an IO dispatcher: enqueue completion is awaited before claiming success. */
    fun schedule(option: RouteOption, bufferMinutes: Int): Boolean {
        if (option.id.isBlank() || !notificationPermissionGranted(context)) return false
        val start = plannedWalkStartEpochMillis(option, bufferMinutes) ?: return false
        val delay = plannedWalkReminderDelayMillis(option, System.currentTimeMillis(), bufferMinutes)
            ?: return false
        val token = UUID.randomUUID().toString()
        val stop = option.accessWalking?.stopName?.trim()?.take(80)
            ?.takeIf { it.isNotBlank() } ?: return false
        val request = OneTimeWorkRequestBuilder<PlannedWalkReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_TOKEN to token, KEY_START to start, KEY_STOP to stop))
            .build()
        return runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request).result.get()
            if (!preferences.edit()
                    .putString(KEY_TOKEN, token)
                    .putString(KEY_ROUTE, option.id)
                    .putLong(KEY_START, start)
                    .putString(KEY_STOP, stop)
                    .putString(KEY_DETAILS_TOKEN, token)
                    .commit()) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                false
            } else true
        }.getOrDefault(false)
    }

    /** Invalidate the persisted token *before* asking WorkManager to cancel. */
    fun cancel(): Boolean {
        if (!preferences.edit().remove(KEY_TOKEN).remove(KEY_ROUTE)
                .remove(KEY_START).remove(KEY_STOP).remove(KEY_DETAILS_TOKEN)
                .commit()) return false
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        return true
    }

    companion object {
        const val PREFS = "planned_walk_reminder_v1"
        const val WORK_NAME = "planned_walk_leave_soon_opt_in_v1"
        const val KEY_TOKEN = "token"
        const val KEY_ROUTE = "route_id"
        const val KEY_START = "start"
        const val KEY_STOP = "stop_name"
        const val KEY_DETAILS_TOKEN = "details_token"
        const val CHANNEL = "planned_walk_estimate_v1"

        fun notificationPermissionGranted(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return false
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager ?: return false
            return Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled()
        }
    }
}

class PlannedWalkReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, Context.MODE_PRIVATE)
        val token = inputData.getString(PlannedWalkReminderScheduler.KEY_TOKEN)
            ?: return Result.success()
        val start = inputData.getLong(PlannedWalkReminderScheduler.KEY_START, -1L)
        if (start <= 0L || token != prefs.getString(PlannedWalkReminderScheduler.KEY_TOKEN, null) ||
            start != prefs.getLong(PlannedWalkReminderScheduler.KEY_START, -1L)) {
            return Result.success()
        }

        // Clock changes / Android background delays must never produce a misleading
        // old "leave now" notification. Refuse both premature and >2-minute-late sends.
        val lateness = runCatching { Math.subtractExact(System.currentTimeMillis(), start) }
            .getOrNull() ?: return Result.success()
        if (lateness !in 0L..120_000L) {
            if (lateness > 120_000L) clearTokenIfCurrent(prefs, token)
            return Result.success()
        }
        if (!PlannedWalkReminderScheduler.notificationPermissionGranted(applicationContext)) {
            clearTokenIfCurrent(prefs, token)
            return Result.success()
        }
        val stop = inputData.getString(PlannedWalkReminderScheduler.KEY_STOP)
            ?.takeIf { it.isNotBlank() } ?: return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return Result.success()
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                PlannedWalkReminderScheduler.CHANNEL,
                applicationContext.getString(R.string.route_walk_reminder_notification_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
        val main = Intent(applicationContext, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(applicationContext, 2402, main, flags)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(applicationContext, PlannedWalkReminderScheduler.CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(
                R.string.route_walk_reminder_notification_title))
            .setContentText(applicationContext.getString(
                R.string.route_walk_reminder_notification_body, stop))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // Invalidate before send: retries or duplicate workers cannot notify twice.
        if (clearTokenIfCurrent(prefs, token)) {
            runCatching { manager.notify(2402, notification) }
        }
        return Result.success()
    }

    private fun clearTokenIfCurrent(
        prefs: android.content.SharedPreferences, expected: String,
    ): Boolean {
        if (prefs.getString(PlannedWalkReminderScheduler.KEY_TOKEN, null) != expected)
            return false
        return prefs.edit()
            .remove(PlannedWalkReminderScheduler.KEY_TOKEN)
            .remove(PlannedWalkReminderScheduler.KEY_ROUTE)
            .remove(PlannedWalkReminderScheduler.KEY_START)
            .remove(PlannedWalkReminderScheduler.KEY_STOP)
            .remove(PlannedWalkReminderScheduler.KEY_DETAILS_TOKEN)
            .commit()
    }
}
