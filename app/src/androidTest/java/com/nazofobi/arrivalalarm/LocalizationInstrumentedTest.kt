package com.nazofobi.arrivalalarm

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizationInstrumentedTest {
    @Test
    fun germanAndEnglishResourcesAreResolvedFromSystemLocale() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()

        fun localized(languageTag: String): android.content.Context {
            val config = Configuration(base.resources.configuration)
            config.setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
            return base.createConfigurationContext(config)
        }

        assertEquals("Ankunftsalarm", localized("de-DE").getString(R.string.app_name))
        assertEquals("Arrival Alarm", localized("en-US").getString(R.string.app_name))
        assertEquals(
            "Nächste Haltestellen",
            localized("de-DE").getString(R.string.nearby_stops),
        )
    }
}
