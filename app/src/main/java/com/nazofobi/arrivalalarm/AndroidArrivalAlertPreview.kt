package com.nazofobi.arrivalalarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Explicit local notification test. Does not start, arm or modify a real journey. */
internal object AndroidArrivalAlertPreview {
    internal const val CHANNEL_ID = "arrival_alerts"
    internal const val NOTIFICATION_ID = 4111

    fun post(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= 26) {
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            context.getString(R.string.arrival_alerts_channel_name),
                            NotificationManager.IMPORTANCE_HIGH,
                        ).apply {
                            enableVibration(true)
                            vibrationPattern = longArrayOf(0L, 200L, 100L, 260L)
                        }
                    )
                }
                if (manager.getNotificationChannel(CHANNEL_ID)?.importance ==
                    NotificationManager.IMPORTANCE_NONE
                ) return false
            }
            val notification = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                Notification.Builder(context).setDefaults(Notification.DEFAULT_VIBRATE)
            }
            manager.notify(
                NOTIFICATION_ID,
                notification
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(context.getString(R.string.arrival_alert_preview_title))
                    .setContentText(context.getString(R.string.arrival_alert_preview_body))
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setAutoCancel(true)
                    .build(),
            )
            // Android accepting the post is not evidence that a person saw/felt it.
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}
