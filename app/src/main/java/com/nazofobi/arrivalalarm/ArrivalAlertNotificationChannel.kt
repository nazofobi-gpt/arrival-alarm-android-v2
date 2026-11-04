package com.nazofobi.arrivalalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Shared arrival notification channel policy for both the armed foreground
 * service and the explicit preview. The OS owns existing channel preferences:
 * never recreate or retune a channel that the user may have configured.
 */
internal object ArrivalAlertNotificationChannel {
    internal const val ID = "arrival_alerts"

    fun ensureExists(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(ID) != null) return
        manager.createNotificationChannel(newChannel(context))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    internal fun newChannel(context: Context): NotificationChannel =
        NotificationChannel(
            ID,
            context.getString(R.string.arrival_alerts_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 200L, 100L, 260L)
        }
}
