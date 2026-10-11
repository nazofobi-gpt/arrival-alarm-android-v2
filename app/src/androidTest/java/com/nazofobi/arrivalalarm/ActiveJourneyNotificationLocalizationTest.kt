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
class ActiveJourneyNotificationLocalizationTest {
    @Test
    fun trackingAndArrivalStringsRespectGermanEnglishAndTurkish() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        fun localized(tag: String): Context {
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(tag))
            return base.createConfigurationContext(configuration)
        }

        val tr = localized("tr")
        val de = localized("de")
        val en = localized("en")

        assertEquals("Varış alarmı etkin", tr.getString(R.string.active_tracking_title))
        assertEquals("Ankunftsalarm aktiv", de.getString(R.string.active_tracking_title))
        assertEquals("Arrival alarm active", en.getString(R.string.active_tracking_title))

        assertEquals("Hedefe yaklaşık 250 m kaldı", tr.getString(R.string.active_tracking_distance, 250))
        assertEquals("Noch etwa 250 m bis zum Ziel", de.getString(R.string.active_tracking_distance, 250))
        assertEquals("About 250 m to your destination", en.getString(R.string.active_tracking_distance, 250))

        assertEquals("Varış noktasına ulaşıldı", tr.getString(R.string.arrival_notification_title))
        assertEquals("Ziel erreicht", de.getString(R.string.arrival_notification_title))
        assertEquals("Destination reached", en.getString(R.string.arrival_notification_title))
    }
}
