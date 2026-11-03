package com.nazofobi.arrivalalarm

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveJourneyServiceInstrumentedTest {
    @Test fun forcedTrackingStopNoticeHasNoPrivateDestinationAndOpensTheApp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notice = ActiveJourneyService.trackingStoppedNotification(context)
        assertEquals(
            context.getString(R.string.active_tracking_stopped_title),
            notice.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
        assertEquals(
            context.getString(R.string.active_tracking_stopped_body),
            notice.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        assertEquals(Notification.CATEGORY_ERROR, notice.category)
        assertEquals(Notification.VISIBILITY_PUBLIC, notice.visibility)
        assertFalse(notice.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(notice.contentIntent)
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals("arrival_alerts", notice.channelId)
        }
    }

    @Test fun clearingStaleForcedStopNoticeRemovesOnlyTheOldAlert() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // A real notification assertion requires a runtime grant on the API36 emulator.
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val stoppedId = ActiveJourneyService.TRACKING_STOPPED_NOTIFICATION_ID
        manager.cancel(stoppedId)
        try {
            manager.notify(stoppedId, ActiveJourneyService.trackingStoppedNotification(context))
            assertTrue(manager.activeNotifications.any { it.id == stoppedId })
            ActiveJourneyService.clearStoppedTrackingNotification(context)
            assertFalse(manager.activeNotifications.any { it.id == stoppedId })
        } finally {
            manager.cancel(stoppedId)
        }
    }

    @Test fun cancellationActionTargetsTheNonExportedTrackingService() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = ActiveJourneyService.cancellationIntent(context)

        assertEquals(ActiveJourneyService.ACTION_CANCEL, intent.action)
        assertEquals(ComponentName(context, ActiveJourneyService::class.java), intent.component)
    }

    @Test fun manifestDeclaresNonExportedLocationForegroundService() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val component = ComponentName(context, ActiveJourneyService::class.java)
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getServiceInfo(
                component,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        }

        assertEquals(false, info.exported)
        assertTrue(
            info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0
        )
    }

    @Test fun manifestUsesForegroundLocationWithoutBackgroundLocationGrant() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        val requested = info.requestedPermissions.orEmpty().toSet()

        assertTrue(Manifest.permission.FOREGROUND_SERVICE_LOCATION in requested)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in requested)
    }
}
