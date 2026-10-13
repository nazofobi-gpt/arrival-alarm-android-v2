package com.nazofobi.arrivalalarm

import org.junit.Assert.*
import org.junit.Test

class TripInferenceEngineTest {
    private val now = 1_800_000_000L
    private val hbf = GeoPoint(53.0830, 8.8130)

    private fun candidate(
        id: String,
        direction: String,
        bearing: Double,
        offsetSec: Long = 0,
        anchor: GeoPoint = hbf,
        active: Boolean = true,
        rtDelay: Int? = 0,
    ) = TripCandidate(
        routeId = id,
        line = "6",
        direction = direction,
        anchor = anchor,
        expectedBearingDegrees = bearing,
        scheduledEpochSeconds = now + offsetSec,
        realtimeDelaySeconds = rtDelay,
        serviceActive = active,
    )

    private fun observation(
        point: GeoPoint = GeoPoint(53.0831, 8.8131),
        speed: Double = 8.0,
        bearing: Double? = 180.0,
        time: Long = now,
    ) = MovementObservation(point, speed, bearing, time)

    @Test fun disabledFeatureNeverSuggests() {
        val result = TripInferenceEngine(TripInferenceSettings(enabled = false))
            .infer(observation(), listOf(candidate("south", "Flughafen", 180.0)))
        assertNull(result.accepted)
        assertTrue(result.suggestions.isEmpty())
        assertTrue(result.requiresExplicitConfirmation)
    }

    @Test fun clearCandidateUsesLocationMovementTimeAndRealtimeEvidence() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        val result = engine.infer(
            observation(),
            listOf(
                candidate("south", "Flughafen", 180.0, rtDelay = 30),
                candidate("north", "Universität", 0.0, offsetSec = 600),
            )
        )
        assertEquals("south", result.accepted?.candidate?.routeId)
        assertTrue(result.accepted!!.confidence >= 0.72)
        assertTrue(result.accepted!!.explanation.contains("RT=30s"))
        assertTrue(result.requiresExplicitConfirmation)
    }

    @Test fun oppositeDirectionAmbiguityFailsClosed() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true, ambiguityMargin = 0.12))
        val result = engine.infer(
            observation(bearing = null, speed = 0.2),
            listOf(
                candidate("south", "Flughafen", 180.0),
                candidate("north", "Universität", 0.0),
            )
        )
        assertNull(result.accepted)
        assertTrue(result.reason.contains("belirsiz"))
    }

    @Test fun singleVisibleCandidateStillChecksRunnerUpBeforeAccepting() {
        val engine = TripInferenceEngine(
            TripInferenceSettings(enabled = true, topK = 1, ambiguityMargin = 0.10)
        )
        val result = engine.infer(
            observation(bearing = null, speed = 0.2),
            listOf(
                candidate("south", "Flughafen", 180.0),
                candidate("north", "Universität", 0.0),
            )
        )
        assertEquals(1, result.suggestions.size)
        assertNull(result.accepted)
        assertTrue(result.reason.contains("belirsiz"))
        assertTrue(result.requiresExplicitConfirmation)
    }

    @Test fun singleVisibleCandidateCanAcceptUnambiguousTrip() {
        val engine = TripInferenceEngine(
            TripInferenceSettings(enabled = true, topK = 1, ambiguityMargin = 0.10)
        )
        val result = engine.infer(
            observation(),
            listOf(
                candidate("south", "Flughafen", 180.0),
                candidate("north", "Universität", 0.0, offsetSec = 600),
            )
        )
        assertEquals(1, result.suggestions.size)
        assertEquals("south", result.accepted?.candidate?.routeId)
        assertTrue(result.requiresExplicitConfirmation)
    }

    @Test fun nonpositiveTopKDoesNotCrashOrAcceptTrip() {
        for (topK in listOf(0, -1)) {
            val engine = TripInferenceEngine(
                TripInferenceSettings(enabled = true, topK = topK)
            )
            val result = engine.infer(
                observation(),
                listOf(candidate("south", "Flughafen", 180.0))
            )
            assertTrue(result.suggestions.isEmpty())
            assertNull(result.accepted)
        }
    }

    @Test fun malformedSensorEvidenceCannotBecomeAConfidentTripSuggestion() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        val valid = listOf(candidate("south", "Flughafen", 180.0))
        val corrupt = listOf(
            observation(speed = Double.NaN, bearing = null),
            observation(speed = Double.POSITIVE_INFINITY, bearing = null),
            observation(speed = -1.0, bearing = null),
            observation(bearing = Double.NaN),
            observation(bearing = Double.NEGATIVE_INFINITY),
            observation(point = GeoPoint(93.0, 8.813)),
            observation(point = GeoPoint(53.083, 181.0)),
            observation(point = GeoPoint(Double.NaN, 8.813)),
            observation(time = 0L),
        )
        corrupt.forEach { evidence ->
            val result = engine.infer(evidence, valid)
            assertNull("Invalid sensor evidence must not be accepted: $evidence", result.accepted)
            assertTrue("Invalid evidence must produce no suggestions", result.suggestions.isEmpty())
            assertTrue(result.requiresExplicitConfirmation)
        }
    }

    @Test fun invalidCandidatesAreExcludedBeforeScoring() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        val candidates = listOf(
            candidate("invalid-lat", "Bad", 180.0, anchor = GeoPoint(91.0, 8.813)),
            candidate("invalid-lon", "Bad", 180.0, anchor = GeoPoint(53.083, 181.0)),
            candidate("invalid-bearing", "Bad", Double.NaN),
            candidate("invalid-time", "Bad", 180.0).copy(scheduledEpochSeconds = 0L),
        )
        val result = engine.infer(observation(), candidates)
        assertNull(result.accepted)
        assertTrue(result.suggestions.isEmpty())
        assertEquals("Uygun aktif sefer adayı yok", result.reason)
    }

    @Test fun invalidConfidenceConfigurationFailsClosed() {
        val candidateList = listOf(candidate("south", "Flughafen", 180.0))
        val badSettings = listOf(
            TripInferenceSettings(enabled = true, minimumConfidence = Double.NaN),
            TripInferenceSettings(enabled = true, minimumConfidence = -0.1),
            TripInferenceSettings(enabled = true, minimumConfidence = 1.1),
            TripInferenceSettings(enabled = true, ambiguityMargin = Double.POSITIVE_INFINITY),
            TripInferenceSettings(enabled = true, ambiguityMargin = -0.1),
        )
        badSettings.forEach { settings ->
            val result = TripInferenceEngine(settings).infer(observation(), candidateList)
            assertNull(result.accepted)
            assertTrue(result.suggestions.isEmpty())
        }
    }

    @Test fun parallelRouteAmbiguityFailsClosed() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true, ambiguityMargin = 0.08))
        val result = engine.infer(
            observation(),
            listOf(
                candidate("route-a", "Flughafen", 180.0),
                candidate("route-b", "Neustadt", 184.0, anchor = GeoPoint(53.08315, 8.81315)),
            )
        )
        assertNull(result.accepted)
    }

    @Test fun offRouteAndNoServiceProduceNoSuggestion() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        val far = GeoPoint(53.20, 9.10)
        assertNull(engine.infer(observation(point = far), listOf(candidate("route", "Flughafen", 180.0))).accepted)
        assertNull(engine.infer(observation(), listOf(candidate("closed", "Flughafen", 180.0, active = false))).accepted)
    }

    @Test fun frozenSyntheticEvaluationReportsConfusionAndNaturalAccuracyIsNotImplied() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true, ambiguityMargin = 0.10))
        val fixtures = listOf(
            LabeledInferenceFixture(
                "clear south", observation(), listOf(candidate("south", "Flughafen", 180.0)), "south"
            ),
            LabeledInferenceFixture(
                "clear north",
                observation(bearing = 0.0),
                listOf(candidate("north", "Universität", 0.0), candidate("south", "Flughafen", 180.0)),
                "north"
            ),
            LabeledInferenceFixture(
                "ambiguous parallel",
                observation(bearing = null, speed = 0.2),
                listOf(candidate("a", "A", 90.0), candidate("b", "B", 270.0)),
                null
            ),
            LabeledInferenceFixture(
                "off route",
                observation(point = GeoPoint(53.20, 9.10)),
                listOf(candidate("south", "Flughafen", 180.0)),
                null
            ),
            LabeledInferenceFixture(
                "no service",
                observation(),
                listOf(candidate("closed", "Flughafen", 180.0, active = false)),
                null
            ),
        )
        val metrics = TripInferenceEvaluator.evaluate(engine, fixtures)
        assertEquals(5, metrics.total)
        assertEquals(5, metrics.correct)
        assertEquals(0, metrics.falsePositive)
        assertEquals(0, metrics.falseNegative)
        assertEquals(1.0, metrics.precision, 0.0)
        assertEquals(0.4, metrics.coverage, 0.0)
        assertEquals(1.0, metrics.accuracy, 0.0)
    }
}
