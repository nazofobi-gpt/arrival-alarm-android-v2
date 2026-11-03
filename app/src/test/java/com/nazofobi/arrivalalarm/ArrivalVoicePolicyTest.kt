package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalVoicePolicyTest {
    private fun choose(
        enabled: Boolean = true,
        arrived: Boolean = true,
        language: String? = "tr-TR",
        label: String? = "Bremen Hbf",
        generic: String = "Varış alarmı tamamlandı.",
    ): ArrivalVoiceRequest? = ArrivalVoicePolicy.select(
        enabled = enabled,
        arrived = arrived,
        preferredLocale = language,
        verifiedDestinationLabel = label,
        genericText = generic,
        namedText = { "Hedef: " + it + ". Varış alarmı tamamlandı." },
    )

    @Test fun defaultOffCannotEnqueueSpeech() {
        assertNull(choose(enabled = false))
    }

    @Test fun unverifiedArrivalCannotEnqueueSpeech() {
        assertNull(choose(arrived = false))
    }

    @Test fun namedArrivalUsesOnlyValidatedLabelAndPreferredLanguage() {
        assertEquals(
            ArrivalVoiceRequest("Hedef: Bremen Hbf. Varış alarmı tamamlandı.", "de-DE"),
            choose(language = "de-DE"),
        )
        assertEquals("en-US", choose(language = "en-US")?.localeTag)
        assertEquals("tr-TR", choose(language = "tr-TR")?.localeTag)
    }

    @Test fun missingVerifiedLabelFallsBackToGenericText() {
        assertEquals(
            ArrivalVoiceRequest("Varış alarmı tamamlandı.", "tr-TR"),
            choose(label = null),
        )
        assertEquals("Varış alarmı tamamlandı.", choose(label = " ")?.text)
    }

    @Test fun malformedLanguageFallsBackToSupportedTurkishWithoutInventingText() {
        assertEquals("tr-TR", choose(language = "invalid-locale-xyz")?.localeTag)
    }

    @Test fun emptyOrOversizedSpeechFailsClosed() {
        assertNull(choose(label = null, generic = " "))
        assertNull(choose(label = null, generic = "a".repeat(
            GuidanceReliabilityController.MAX_UTTERANCE_TEXT_CHARACTERS + 1
        )))
    }
}
