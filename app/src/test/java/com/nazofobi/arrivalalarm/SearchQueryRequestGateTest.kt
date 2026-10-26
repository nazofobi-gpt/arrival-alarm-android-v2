package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryRequestGateTest {
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

    @Test fun shortQueryDoesNotTriggerAutoRequest() {
        val gate = SearchQueryRequestGate()
        assertFalse(gate.shouldAutoRequest("L"))
        assertFalse(gate.shouldAutoRequest("   "))
        assertTrue(gate.shouldAutoRequest(" Lo "))
    }
}
