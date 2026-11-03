package com.nazofobi.arrivalalarm

/** Only a confirmed arrival and an explicit local opt-in may request background speech. */
internal data class ArrivalVoiceRequest(val text: String, val localeTag: String)

internal object ArrivalVoicePolicy {
    fun select(
        enabled: Boolean,
        arrived: Boolean,
        preferredLocale: String?,
        verifiedDestinationLabel: String?,
        genericText: String,
        namedText: (String) -> String,
    ): ArrivalVoiceRequest? {
        if (!enabled || !arrived) return null
        val text = verifiedDestinationLabel?.takeIf { it.isNotBlank() }
            ?.let(namedText) ?: genericText
        if (text.isBlank() ||
            text.length > GuidanceReliabilityController.MAX_UTTERANCE_TEXT_CHARACTERS
        ) return null
        return ArrivalVoiceRequest(
            text = text,
            localeTag = GuidanceLanguage.fromLocaleTag(preferredLocale).localeTag,
        )
    }
}
