package com.nazofobi.arrivalalarm

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ActiveAlarmRetargetLocalizationTest {
    private val activeAlarmGuardMessage = "Aktif alarmı önce iptal edin"

    @Test
    fun rejectedRetargetUsesTheAppLanguageInsteadOfRawTurkish() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedByLanguage = mapOf(
            "tr" to "Aktif varış alarmını önce iptal edin; ardından yeni bir rota seçin.",
            "de" to "Bitte zuerst den aktiven Ankunftsalarm abbrechen und dann eine neue Route wählen.",
            "en" to "Cancel the active arrival alarm before choosing a new route.",
        )

        expectedByLanguage.forEach { (language, expected) ->
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localizedContext = context.createConfigurationContext(configuration)
            assertEquals(
                "Rejected alarm retarget must be localized for $language",
                expected,
                localizedDomainMessage(localizedContext, activeAlarmGuardMessage),
            )
        }
    }

    @Test
    fun unrelatedExistingErrorStillUsesItsLocalizedResource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val localizedContext = context.createConfigurationContext(configuration)
        assertEquals(
            localizedContext.getString(R.string.error_no_alarm),
            localizedDomainMessage(localizedContext, "İptal edilecek kurulu alarm yok"),
        )
    }
}
