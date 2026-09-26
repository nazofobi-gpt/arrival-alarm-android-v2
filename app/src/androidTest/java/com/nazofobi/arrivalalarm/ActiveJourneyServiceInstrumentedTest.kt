package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveJourneyServiceInstrumentedTest {
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
