package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NationwideIndexReadinessTest {
    @Test fun emptyOrInvalidStopIndexIsNeverReady() {
        assertFalse(hasUsableNationwideIndex(NationwideDataState.Idle))
        assertFalse(hasUsableNationwideIndex(NationwideDataState.Loading("loading")))
        assertFalse(hasUsableNationwideIndex(NationwideDataState.Error("failed")))
        assertFalse(hasUsableNationwideIndex(NationwideDataState.Ready(0, 1L, "feed")))
        assertFalse(hasUsableNationwideIndex(NationwideDataState.Ready(-1, 1L, "feed")))
    }

    @Test fun zeroStopReadyIndexCanBeRetriedWithoutOfferingDuplicateDownloads() {
        assertTrue(shouldOfferNationwideDataRetry(NationwideDataState.Idle))
        assertTrue(shouldOfferNationwideDataRetry(NationwideDataState.Error("network failure")))
        assertTrue(shouldOfferNationwideDataRetry(NationwideDataState.Ready(0, 1L, "feed")))
        assertTrue(shouldOfferNationwideDataRetry(NationwideDataState.Ready(-1, 1L, "feed")))
        assertFalse(shouldOfferNationwideDataRetry(NationwideDataState.Loading("loading")))
        assertFalse(shouldOfferNationwideDataRetry(NationwideDataState.Ready(1, 1L, "feed")))
    }

    @Test fun populatedStopIndexCanBeConsideredUsable() {
        assertTrue(hasUsableNationwideIndex(NationwideDataState.Ready(1, 1L, "feed")))
        assertTrue(hasUsableNationwideIndex(NationwideDataState.Ready(100_000, 1L, "feed")))
    }
}
