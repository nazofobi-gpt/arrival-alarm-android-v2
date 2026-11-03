package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStopTypoMatcherTest {
    @Test fun oneTypoCanBeSubstitutionTranspositionOrMissingCharacter() {
        assertTrue(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Knowm Good Bahnhof"), "Known Good Bahnhof"))
        assertTrue(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Bremne"), "Bremen Hbf"))
        assertTrue(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Breme"), "Bremen Hauptbahnhof"))
        assertTrue(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Bremmen"), "Bremen Hauptbahnhof"))
    }

    @Test fun preservesOrderedTokensAndRejectsBroadOrMultipleMistakes() {
        assertFalse(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Known Good Bahnhof"), "Known Good Bahnhof"))
        assertFalse(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Knowm Goot Bahnhof"), "Known Good Bahnhof"))
        assertFalse(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Bahnhof Known Good"), "Known Good Bahnhof"))
        assertFalse(OfflineStopTypoMatcher.eligible(OfflineStopTypoMatcher.tokens("Br")))
        assertFalse(OfflineStopTypoMatcher.eligible(OfflineStopTypoMatcher.tokens("!")))
        assertFalse(OfflineStopTypoMatcher.eligible(OfflineStopTypoMatcher.tokens("very long search with too many tokens")))
    }

    @Test fun unicodeNormalizationSupportsGermanStopNamesWithoutInventingMatches() {
        assertTrue(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Munchne"), "München"))
        assertTrue(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Strase"), "Straße"))
        assertFalse(OfflineStopTypoMatcher.matchesOneTypo(
            OfflineStopTypoMatcher.tokens("Zuerich"), "München"))
    }
}
