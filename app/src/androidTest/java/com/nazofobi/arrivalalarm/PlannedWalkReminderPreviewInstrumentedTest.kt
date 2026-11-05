package com.nazofobi.arrivalalarm

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real API36 OS-level proof of a distinct, opt-in TEST notice, not audible delivery. */
@RunWith(AndroidJUnit4::class)
class PlannedWalkReminderPreviewInstrumentedTest {
    @Test fun explicitPreviewPostsToRealWalkingChannelWithoutSchedulingJourney() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        assumeTrue("OS notifications unavailable in this test environment",
            manager.areNotificationsEnabled())
        if (Build.VERSION.SDK_INT >= 26) {
            assumeTrue(
                "Walking channel was disabled by the OS",
                manager.getNotificationChannel(PlannedWalkReminderScheduler.CHANNEL)
                    ?.importance != NotificationManager.IMPORTANCE_NONE,
            )
        }
        val preferences = context.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, Context.MODE_PRIVATE)
        val oldToken = preferences.getString(PlannedWalkReminderScheduler.KEY_TOKEN, null)
        val oldRoute = preferences.getString(PlannedWalkReminderScheduler.KEY_ROUTE, null)
        val oldStart = preferences.getLong(PlannedWalkReminderScheduler.KEY_START, -1L)
        try {
            assertTrue("Explicit test should submit a real OS notification",
                PlannedWalkReminderPreview.post(context))
            // API36 NotificationManager may publish asynchronously: use bounded polling.
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000L
            var entry = manager.activeNotifications.firstOrNull {
                it.id == PlannedWalkReminderPreview.NOTIFICATION_ID
            }
            while (entry == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                android.os.SystemClock.sleep(100L)
                entry = manager.activeNotifications.firstOrNull {
                    it.id == PlannedWalkReminderPreview.NOTIFICATION_ID
                }
            }
            assertNotNull("Expected the real TEST notification in NotificationManager", entry)
            val notification = requireNotNull(entry).notification
            assertEquals(PlannedWalkReminderScheduler.CHANNEL, notification.channelId)
            assertEquals(Notification.CATEGORY_STATUS, notification.category)
            assertTrue("The test notice should reopen this app",
                notification.contentIntent?.isActivity == true)
            assertEquals(context.getString(R.string.route_walk_reminder_preview_title),
                notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            // Preview never writes the actual reminder route/token or schedules WorkManager.
            assertEquals(oldToken, preferences.getString(
                PlannedWalkReminderScheduler.KEY_TOKEN, null))
            assertEquals(oldRoute, preferences.getString(
                PlannedWalkReminderScheduler.KEY_ROUTE, null))
            assertEquals(oldStart, preferences.getLong(
                PlannedWalkReminderScheduler.KEY_START, -1L))
            assertTrue(PlannedWalkReminderPreview.NOTIFICATION_ID != 2402)
        } finally {
            manager.cancel(PlannedWalkReminderPreview.NOTIFICATION_ID)
        }
    }
}
