package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryRequestGateTest {
    @Test fun cosmeticWhitespaceUsesSameSearchEffectKey() {
        // The Compose LaunchedEffect key uses the same normalization as this gate.
        // A cosmetic edit must retain the visible results rather than clear them
        // and then suppress the redundant request.
        val initialKey = normalizedSearchQuery("Lohne")
        val cosmeticKey = normalizedSearchQuery("  Lohne \t")
        assertEquals(initialKey, cosmeticKey)

        val gate = SearchQueryRequestGate()
        gate.markRequested("Lohne")
        gate.observeQuery(cosmeticKey)
        assertFalse(gate.shouldAutoRequest(cosmeticKey))

        val changedKey = normalizedSearchQuery("Lohne Hbf")
        assertNotEquals(initialKey, changedKey)
        gate.observeQuery(changedKey)
        assertTrue(gate.shouldAutoRequest(changedKey))
    }

    @Test fun pastedInternalWhitespaceUsesTheTypedSearchAndDedupKey() {
        val typed = "Lohne Hbf"
        val pasted = "  Lohne\n\t Hbf\r "
        assertEquals(typed, normalizedSearchQuery(pasted))
        assertEquals(typed, normalizedSearchQuery("Lohne\u00A0Hbf"))
        assertTrue(isSupportedSearchQuery(pasted))

        val gate = SearchQueryRequestGate()
        gate.markRequested(typed)
        gate.observeQuery(pasted)
        assertFalse(gate.shouldAutoRequest(pasted))
        gate.observeQuery("Lohne Bahnhof")
        assertTrue(gate.shouldAutoRequest(typed))
    }

    @Test fun restoredRecentsDeduplicatePastedWhitespaceVariants() {
        val serialized = listOf(
            "Lohne Hbf", "Lohne\nHbf", "Lohne\u00A0Hbf", "Achim",
        ).joinToString("\u001F")
        assertEquals(listOf("Lohne Hbf", "Achim"), restoredRecentSearchQueries(serialized))
    }

    @Test fun clearingQueryAllowsSameQueryToRunAgain() {
        val gate = SearchQueryRequestGate()
        assertTrue(gate.shouldAutoRequest("Lohne"))
        gate.markRequested("Lohne")
        assertFalse(gate.shouldAutoRequest(" Lohne "))
        assertFalse(gate.shouldAutoRequest(""))
        assertTrue(gate.shouldAutoRequest("Lohne"))
    }

    @Test fun newCatalogDoesNotInheritPreviousQueryDeduplication() {
        val previousCatalog = SearchQueryRequestGate()
        previousCatalog.markRequested("Lohne")
        assertFalse(previousCatalog.shouldAutoRequest("Lohne"))

        val activatedCatalog = SearchQueryRequestGate()
        assertTrue(activatedCatalog.shouldAutoRequest("Lohne"))
    }

    @Test fun aChangedQueryAndExplicitRetryRemainPossible() {
        val gate = SearchQueryRequestGate()
        gate.markRequested("Achim")
        assertTrue(gate.shouldAutoRequest("Bremen"))
        gate.markRequested("Bremen")
        assertFalse(gate.shouldAutoRequest("Bremen"))
        gate.reset()
        assertTrue(gate.shouldAutoRequest("Bremen"))
    }

    @Test fun returningAfterAbandonedIntermediateQueryRequestsFreshResults() {
        val gate = SearchQueryRequestGate()
        gate.markRequested("Lohne")
        assertFalse(gate.shouldAutoRequest("Lohne"))

        // No actual request for Achim: the UI cancelled its debounce.
        gate.observeQuery("Achim")
        assertTrue(gate.shouldAutoRequest("Lohne"))
        gate.markRequested("Lohne")
        assertFalse(gate.shouldAutoRequest("Lohne"))
    }

    @Test fun cosmeticWhitespaceDoesNotInvalidateAnIdenticalQuery() {
        val gate = SearchQueryRequestGate()
        gate.markRequested("Lohne")
        gate.observeQuery(" Lohne ")
        assertFalse(gate.shouldAutoRequest("Lohne"))
    }

    @Test fun immediateRecentSelectionIsNotCancelledByItsOwnComposeEffect() {
        val gate = SearchQueryRequestGate()
        gate.markRequested("Bremen") // Explicit recent/completion search started.
        gate.observeQuery("Bremen") // Compose effect for the new text runs later.

        // The effect must leave the already-started callback alive.
        assertFalse(gate.shouldAutoRequest("Bremen"))

        // Changing the query still requires cancellation and a fresh lookup.
        gate.observeQuery("Lohne")
        assertTrue(gate.shouldAutoRequest("Lohne"))
    }

    @Test fun immediateManualRetryPreservesTheNewAttempt() {
        val gate = SearchQueryRequestGate()
        gate.markRequested("Bremen")
        gate.reset() // A retry or provider failure allows the next attempt.
        gate.markRequested("Bremen")
        gate.observeQuery(" Bremen ")
        assertFalse(gate.shouldAutoRequest("Bremen"))
    }

    @Test fun shortQueryDoesNotTriggerAutoRequest() {
        val gate = SearchQueryRequestGate()
        assertFalse(gate.shouldAutoRequest("L"))
        assertFalse(gate.shouldAutoRequest("   "))
        assertTrue(gate.shouldAutoRequest(" Lo "))
    }

    @Test fun maximumLengthSearchRemainsAllowedAfterTrimming() {
        val maxLength = "L".repeat(MAX_SEARCH_QUERY_LENGTH)
        assertTrue(isSupportedSearchQuery("  $maxLength  "))
        assertFalse(isSupportedSearchQuery("L"))
        assertFalse(isSupportedSearchQuery("  "))
        assertTrue(isSupportedSearchQuery("Lohne"))

        val gate = SearchQueryRequestGate()
        assertTrue(gate.shouldAutoRequest(maxLength))
        gate.markRequested(maxLength)
        assertFalse(gate.shouldAutoRequest(" $maxLength "))
    }

    @Test fun oversizedQueryIsNeverAutoRequestedOrSavedAsDedupKey() {
        val valid = "L".repeat(MAX_SEARCH_QUERY_LENGTH)
        val oversized = valid + "x"
        assertFalse(isSupportedSearchQuery(oversized))

        val gate = SearchQueryRequestGate()
        assertFalse(gate.shouldAutoRequest(oversized))
        gate.markRequested(oversized)
        assertTrue(gate.shouldAutoRequest(valid))
        gate.markRequested(valid)
        assertFalse(gate.shouldAutoRequest(valid))
        // Rejecting an oversized edit must release the previous request key.
        assertFalse(gate.shouldAutoRequest(oversized))
        assertTrue(gate.shouldAutoRequest(valid))
    }

    @Test fun restoredSearchHistoryExcludesLegacyInvalidQueriesBeforeLimitingResults() {
        val max = "L".repeat(MAX_SEARCH_QUERY_LENGTH)
        val oversized = "X".repeat(MAX_SEARCH_QUERY_LENGTH + 1)
        val saved = listOf(
            " ", "L", oversized, "  Lohne  ", " $max ", "Achim", "Berlin",
            "Bremen", "Hamburg",
        ).joinToString("\u001F")

        assertEquals(
            listOf("Lohne", max, "Achim", "Berlin", "Bremen"),
            restoredRecentSearchQueries(saved),
        )
    }

    @Test fun restoredLegacyHistoryDeduplicatesCaseAndWhitespaceBeforeFiveItemCap() {
        val saved = listOf(
            " Lohne ", "lohNE", " BREMEN ", "Bremen", "Achim", "Kiel",
            "Berlin", "Hamburg",
        ).joinToString("\u001F")

        assertEquals(
            listOf("Lohne", "BREMEN", "Achim", "Kiel", "Berlin"),
            restoredRecentSearchQueries(saved),
        )
    }

    @Test fun restoredSearchHistoryHandlesMissingAndInvalidLegacyValues() {
        assertTrue(restoredRecentSearchQueries(null).isEmpty())
        assertTrue(restoredRecentSearchQueries("").isEmpty())
        assertTrue(restoredRecentSearchQueries("A\u001F \u001F" + "Z".repeat(257)).isEmpty())
        assertEquals(listOf("Lohne"), restoredRecentSearchQueries("  Lohne  "))
    }
    @Test fun invisibleControlCharactersAreRejectedFromSearchAndRestoredHistory() {
        val invalidStop = "Loh\u0000ne"
        val invalidC1Stop = "Bremen\u0085Hbf"
        assertFalse(isSupportedSearchQuery(invalidStop))
        assertFalse(isSupportedSearchQuery(invalidC1Stop))

        val gate = SearchQueryRequestGate()
        assertFalse(gate.shouldAutoRequest(invalidStop))
        gate.markRequested(invalidStop) // An invalid query must not poison the dedup key.
        assertTrue(gate.shouldAutoRequest("Lohne"))

        val serialized = listOf(
            invalidStop, "Achim", invalidC1Stop, "Bremen",
        ).joinToString("\u001F")
        assertEquals(listOf("Achim", "Bremen"), restoredRecentSearchQueries(serialized))
    }


    @Test fun invisibleUnicodeSearchFormatControlsAreRejectedWithoutPoisoningRetryOrRecents() {
        val invalidCharacters = listOf(
            '\u061C', '\u200B', '\u200E', '\u200F',
            '\u202A', '\u202C', '\u202E', '\u2060',
            '\u2066', '\u2069', '\uFEFF',
        )
        val gate = SearchQueryRequestGate()
        for (character in invalidCharacters) {
            val invalid = "Loh${character}ne"
            assertFalse("Unexpectedly allowed U+${character.code.toString(16)}", isSupportedSearchQuery(invalid))
            assertFalse(gate.shouldAutoRequest(invalid))
            gate.markRequested(invalid)
            assertTrue(gate.shouldAutoRequest("Lohne"))
        }

        val history = listOf("Loh\u202Ene", "Achim", "Bremen\u200B", "Lohne")
            .joinToString("\u001F")
        assertEquals(listOf("Achim", "Lohne"), restoredRecentSearchQueries(history))
        assertTrue(isSupportedSearchQuery("Lohne Hbf"))
        assertTrue(isSupportedSearchQuery("Bremen"))
    }
}
