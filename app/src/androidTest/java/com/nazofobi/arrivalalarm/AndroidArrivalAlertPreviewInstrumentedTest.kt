package com.nazofobi.arrivalalarm

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidArrivalAlertPreviewInstrumentedTest {
    @Test fun notificationRecoveryTargetsOnlyThisAppWithoutArmingAnAlarm() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = AndroidArrivalAlertPreview.settingsIntent(context)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(null, intent.data)
        assertEquals(null, intent.component)
    }

    @Test fun newArrivalChannelHasHapticsWithoutChangingExistingUserSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val channel = ArrivalAlertNotificationChannel.newChannel(context)
        assertEquals(AndroidArrivalAlertPreview.CHANNEL_ID, channel.id)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue("Arrival alerts must provide an OS-managed haptic fallback", channel.shouldVibrate())
        assertTrue(
            channel.vibrationPattern.contentEquals(longArrayOf(0L, 200L, 100L, 260L))
        )
    }

    @Test fun explicitPreviewPostsRealLocalNotificationOnArrivalChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        assumeTrue("OS notifications are disabled", manager.areNotificationsEnabled())
        try {
            assertTrue(AndroidArrivalAlertPreview.post(context))
            // NotificationManager posts asynchronously on API 36; observe the real OS state
            // with a bounded deadline instead of assuming immediate publication.
            val deadlineMs = android.os.SystemClock.elapsedRealtime() + 5_000L
            var status = manager.activeNotifications.firstOrNull {
                it.id == AndroidArrivalAlertPreview.NOTIFICATION_ID
            }
            while (status == null && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
                android.os.SystemClock.sleep(100L)
                status = manager.activeNotifications.firstOrNull {
                    it.id == AndroidArrivalAlertPreview.NOTIFICATION_ID
                }
            }
            assertNotNull("Expected preview notification ID after bounded wait", status)
            val notification = status!!.notification
            assertEquals(AndroidArrivalAlertPreview.CHANNEL_ID, notification.channelId)
            assertTrue("Preview should return to the app on tap", notification.contentIntent?.isActivity == true)
            assertEquals(Notification.CATEGORY_ALARM, notification.category)
            assertEquals(
                context.getString(R.string.arrival_alert_preview_title),
                notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            )
        } finally {
            manager.cancel(AndroidArrivalAlertPreview.NOTIFICATION_ID)
        }
    }
}
