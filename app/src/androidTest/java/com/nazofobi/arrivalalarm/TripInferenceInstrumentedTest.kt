package com.nazofobi.arrivalalarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripInferenceInstrumentedTest {
    @Test fun api36ScorerIsUserGatedAndRequiresExplicitConfirmation() {
        val now = 1_800_000_000L
        val observation = MovementObservation(
            GeoPoint(53.0831, 8.8131), speedMps = 8.0, bearingDegrees = 180.0, epochSeconds = now
        )
        val candidate = TripCandidate(
            routeId = "fixture-6-south",
            line = "6",
            direction = "Flughafen Bremen",
            anchor = GeoPoint(53.0830, 8.8130),
            expectedBearingDegrees = 180.0,
            scheduledEpochSeconds = now,
            realtimeDelaySeconds = 20,
        )
        assertNull(TripInferenceEngine(TripInferenceSettings(enabled = false)).infer(observation, listOf(candidate)).accepted)

        val enabled = TripInferenceEngine(TripInferenceSettings(enabled = true)).infer(observation, listOf(candidate))
        assertEquals("fixture-6-south", enabled.accepted?.candidate?.routeId)
        assertTrue(enabled.requiresExplicitConfirmation)
        assertTrue(enabled.reason.contains("yalnız kullanıcı onayıyla"))
    }
}
