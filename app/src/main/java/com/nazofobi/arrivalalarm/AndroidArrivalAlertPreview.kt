package com.nazofobi.arrivalalarm

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings

/** Explicit local notification test. Does not start, arm or modify a real journey. */
internal object AndroidArrivalAlertPreview {
    internal const val CHANNEL_ID = ArrivalAlertNotificationChannel.ID
    internal const val NOTIFICATION_ID = 4111

    /** Quiet or non-vibrating channels cannot be assumed to deliver noticeable alerts. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    internal fun isQuietChannel(channel: android.app.NotificationChannel?): Boolean =
        channel == null ||
            channel.importance < NotificationManager.IMPORTANCE_DEFAULT ||
            !channel.shouldVibrate()

    /** Read only: do not modify user-owned channel preferences. */
    internal fun channelNeedsAttention(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            isQuietChannel(manager.getNotificationChannel(CHANNEL_ID))
        } catch (_: RuntimeException) {
            true
        }
    }

    /** Only the user's explicit recovery tap opens this app's OS notification settings. */
    internal fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"))
        }

    /** The alert channel is separate from general app notifications on API 26+. */
    internal fun channelSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        }

    internal fun openSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // Do not retune an existing user-configured notification channel.
                ArrivalAlertNotificationChannel.ensureExists(context, manager)
                context.startActivity(channelSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: RuntimeException) {
                // Device-specific Settings UI may not support a channel deep link.
            }
        }
        return try {
            context.startActivity(settingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Withdraw only the test notification, never the real arrival alarm. */
    fun dismiss(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    fun post(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= 26) {
                ArrivalAlertNotificationChannel.ensureExists(context, manager)
                if (manager.getNotificationChannel(CHANNEL_ID)?.importance ==
                    NotificationManager.IMPORTANCE_NONE
                ) return false
            }
            // The test banner should return to the app, never start or mutate an alarm.
            val openApp = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
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
                    .setContentIntent(openApp)
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
