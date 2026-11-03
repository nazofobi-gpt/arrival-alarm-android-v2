package com.nazofobi.arrivalalarm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveJourneyServiceInstrumentedTest {
    @Test fun arrivalChannelRegistersBoundedHapticSignalWithoutDirectVibratorBypass() {
        if (Build.VERSION.SDK_INT < 26) return
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = ActiveJourneyService.arrivalVibrationPattern()
        assertArrayEquals(longArrayOf(0L, 200L, 100L, 260L), expected)
        val configured = ActiveJourneyService.arrivalNotificationChannel(context)
        assertEquals("arrival_alerts", configured.id)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, configured.importance)
        assertTrue(configured.shouldVibrate())
        assertArrayEquals(expected, configured.vibrationPattern)

        // Read the actual Android notification-channel registration, not only our builder.
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(configured)
        val registered = manager.getNotificationChannel(configured.id)
        assertTrue(registered.shouldVibrate())
        assertArrayEquals(expected, registered.vibrationPattern)
    }

    @Test fun vibrationPermissionIsNormalAndBackgroundLocationRemainsAbsent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        assertTrue(Manifest.permission.VIBRATE in requested)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in requested)
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
