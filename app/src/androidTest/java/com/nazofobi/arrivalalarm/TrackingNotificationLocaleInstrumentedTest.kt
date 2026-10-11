package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingNotificationLocaleInstrumentedTest {
    @Test fun trackingAndArrivalResourcesUseChosenLocale() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        fun contextFor(tag: String): Context {
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(tag))
            return base.createConfigurationContext(configuration)
        }

        val tr = contextFor("tr")
        val de = contextFor("de")
        val en = contextFor("en")

        assertEquals("Varış alarmı etkin", tr.getString(R.string.tracking_notification_title))
        assertEquals("Ankunftsalarm aktiv", de.getString(R.string.tracking_notification_title))
        assertEquals("Arrival alarm armed", en.getString(R.string.tracking_notification_title))

        assertEquals("Hedefe yaklaşık 250 m kaldı", tr.getString(R.string.tracking_notification_distance, 250))
        assertEquals("Noch etwa 250 m bis zum Ziel", de.getString(R.string.tracking_notification_distance, 250))
        assertEquals("About 250 m to destination", en.getString(R.string.tracking_notification_distance, 250))

        assertEquals("Varış noktasına ulaşıldı", tr.getString(R.string.tracking_notification_arrived_title))
        assertEquals("Ziel erreicht", de.getString(R.string.tracking_notification_arrived_title))
        assertEquals("Destination reached", en.getString(R.string.tracking_notification_arrived_title))
    }
}
