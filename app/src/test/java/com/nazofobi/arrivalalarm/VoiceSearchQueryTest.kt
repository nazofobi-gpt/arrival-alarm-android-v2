package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSearchQueryTest {
    @Test fun usesFirstValidCandidateWithoutSelectingTarget() {
        assertEquals("Lohne Bahnhof", acceptedVoiceSearchQuery(listOf("Lohne Bahnhof", "Bremen Hbf")))
    }

    @Test fun normalizesMultilineSpeech() {
        assertEquals("Berlin Hauptbahnhof", acceptedVoiceSearchQuery(listOf(" Berlin\n Hauptbahnhof ")))
    }

    @Test fun skipsInvalidCandidates() {
        assertEquals("Hamburg Hbf", acceptedVoiceSearchQuery(listOf("", "B\u200Berlin", "Hamburg Hbf")))
    }

    @Test fun rejectsUnsafeAndOverlongQueries() {
        assertNull(acceptedVoiceSearchQuery(listOf("B\u200Berlin", "x".repeat(MAX_SEARCH_QUERY_LENGTH + 1))))
    }

    @Test fun cancelledOrEmptySpeechDoesNotReplaceQuery() {
        assertNull(acceptedVoiceSearchQuery(null))
        assertNull(acceptedVoiceSearchQuery(emptyList()))
        assertNull(acceptedVoiceSearchQuery(listOf(" ", "A")))
    }

    @Test fun supportsGermanTurkishAndEnglishPlaceNames() {
        assertEquals("München Hbf", acceptedVoiceSearchQuery(listOf("München Hbf")))
        assertEquals("İstanbul durağı", acceptedVoiceSearchQuery(listOf("İstanbul durağı")))
    }
}
