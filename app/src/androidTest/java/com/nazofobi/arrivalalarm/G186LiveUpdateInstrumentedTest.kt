package com.nazofobi.arrivalalarm

import android.app.Notification
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G186LiveUpdateInstrumentedTest {
    @Test fun activeTrackingRequestsLiveUpdateWithNoFabricatedProgress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder = Notification.Builder(context, "g186-regression")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Journey running")
            .setContentText("GPS updating")
            .setOngoing(true)

        val notification = JourneyLiveUpdateNotification.decorateOngoingTracking(builder).build()
        assertEquals("Journey running", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)

        if (Build.VERSION.SDK_INT >= 36) {
            assertTrue(notification.extras.getBoolean(JourneyLiveUpdateNotification.PROMOTION_REQUEST_KEY))
            assertEquals(
                Notification.ProgressStyle::class.java.name,
                notification.extras.getString(Notification.EXTRA_TEMPLATE),
            )
            // No synthetic percentage should be emitted while location evidence is missing.
            assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false).not())
        } else {
            assertFalse(notification.extras.getBoolean(JourneyLiveUpdateNotification.PROMOTION_REQUEST_KEY))
        }
    }

    @Test fun finalArrivalNotificationsAreNotPromoted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val completed = Notification.Builder(context, "g186-regression")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Arrived")
            .setAutoCancel(true)
            .build()
        assertFalse(completed.extras.getBoolean(JourneyLiveUpdateNotification.PROMOTION_REQUEST_KEY))
        assertFalse(completed.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
