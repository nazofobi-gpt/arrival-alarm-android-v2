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

    @Test fun locationAndHeadingAloneCannotAcceptTemporallyImpossibleTrip() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        // At 15+ minutes from the expected departure, location/heading/motion
        // still contribute up to 0.75 and used to exceed the 0.72 threshold.
        val result = engine.infer(
            observation(),
            listOf(candidate("stale-trip", "Flughafen", 180.0, offsetSec = -3_600L)),
        )

        assertEquals(1, result.suggestions.size)
        assertEquals(0.0, result.suggestions.single().timeScore, 0.0)
        assertTrue(result.suggestions.single().confidence >= 0.72)
        assertNull(result.accepted)
        assertTrue(result.reason.contains("zaman"))
        assertTrue(result.requiresExplicitConfirmation)
    }

    @Test fun departureWithPositiveTimeEvidenceRemainsEligible() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        val result = engine.infer(
            observation(),
            listOf(candidate("nearby-trip", "Flughafen", 180.0, offsetSec = 120L)),
        )
        assertEquals("nearby-trip", result.accepted?.candidate?.routeId)
        assertTrue(result.suggestions.single().timeScore > 0.0)
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

    @Test fun invalidMinimumConfidenceNeverAcceptsOrSuggests() {
        val values = listOf(-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        values.forEach { invalid ->
            val engine = TripInferenceEngine(
                TripInferenceSettings(enabled = true, minimumConfidence = invalid)
            )
            val result = engine.infer(
                observation(),
                listOf(candidate("south", "Flughafen", 180.0))
            )
            assertNull("invalid minimumConfidence=$invalid", result.accepted)
            assertTrue("invalid minimumConfidence=$invalid", result.suggestions.isEmpty())
            assertTrue(result.requiresExplicitConfirmation)
        }
    }

    @Test fun invalidAmbiguityMarginNeverBypassesRunnerUpGate() {
        val values = listOf(-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        values.forEach { invalid ->
            val engine = TripInferenceEngine(
                TripInferenceSettings(enabled = true, ambiguityMargin = invalid, topK = 1)
            )
            val result = engine.infer(
                observation(bearing = null, speed = 0.2),
                listOf(
                    candidate("south", "Flughafen", 180.0),
                    candidate("north", "Universität", 0.0),
                )
            )
            assertNull("invalid ambiguityMargin=$invalid", result.accepted)
            assertTrue("invalid ambiguityMargin=$invalid", result.suggestions.isEmpty())
            assertTrue(result.requiresExplicitConfirmation)
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
            observation(bearing = -1.0),
            observation(bearing = 360.0),
            observation(bearing = 540.0),
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

    @Test fun missingLineOrDirectionCannotBeSuggestedOrAccepted() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        val valid = candidate("route-6", "Flughafen", 180.0)
        val invalid = listOf(
            valid.copy(line = ""),
            valid.copy(line = "  "),
            valid.copy(direction = ""),
            valid.copy(direction = "\t"),
        )
        for (candidate in invalid) {
            val alone = engine.infer(observation(), listOf(candidate))
            assertNull("Incomplete trip identity cannot be accepted", alone.accepted)
            assertTrue("Incomplete trip identity cannot be suggested", alone.suggestions.isEmpty())
            assertTrue(alone.requiresExplicitConfirmation)

            val mixed = engine.infer(observation(), listOf(candidate, valid))
            assertEquals("route-6", mixed.accepted?.candidate?.routeId)
            assertEquals(1, mixed.suggestions.size)
        }
        assertEquals("route-6", engine.infer(observation(), listOf(valid)).accepted?.candidate?.routeId)
    }

    @Test fun missingRouteIdentityCannotProduceConfidentTripOrAmbiguity() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        for (missing in listOf("", " ", "\t")) {
            val invalid = candidate("valid", "Flughafen", 180.0).copy(routeId = missing)
            val alone = engine.infer(observation(), listOf(invalid))
            assertNull("Missing route ID must not be accepted", alone.accepted)
            assertTrue("Missing route ID must not be suggested", alone.suggestions.isEmpty())
            assertTrue(alone.requiresExplicitConfirmation)

            // Invalid provider rows must not tie with a valid candidate.
            val mixed = engine.infer(
                observation(),
                listOf(invalid, candidate("valid", "Flughafen", 180.0)),
            )
            assertEquals("valid", mixed.accepted?.candidate?.routeId)
            assertEquals(1, mixed.suggestions.size)
        }
    }

    @Test fun invalidCandidatesAreExcludedBeforeScoring() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        val candidates = listOf(
            candidate("invalid-lat", "Bad", 180.0, anchor = GeoPoint(91.0, 8.813)),
            candidate("invalid-lon", "Bad", 180.0, anchor = GeoPoint(53.083, 181.0)),
            candidate("invalid-bearing", "Bad", Double.NaN),
            candidate("negative-bearing", "Bad", -1.0),
            candidate("one-turn-bearing", "Bad", 360.0),
            candidate("wrapped-bearing", "Bad", 540.0),
            candidate("invalid-time", "Bad", 180.0).copy(scheduledEpochSeconds = 0L),
        )
        val result = engine.infer(observation(), candidates)
        assertNull(result.accepted)
        assertTrue(result.suggestions.isEmpty())
        assertEquals("Uygun aktif sefer adayı yok", result.reason)
    }

    @Test fun validHeadingEndpointsAndAbsentHeadingRemainEligible() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        listOf(0.0, 359.999).forEach { heading ->
            val id = "valid-heading-$heading"
            val result = engine.infer(
                observation(bearing = heading),
                listOf(candidate(id, "Flughafen", heading)),
            )
            assertEquals(id, result.accepted?.candidate?.routeId)
            assertTrue(result.requiresExplicitConfirmation)
        }

        val absentObservation = engine.infer(
            observation(bearing = null),
            listOf(candidate("absent-observation", "Flughafen", 180.0)),
        )
        assertEquals("absent-observation", absentObservation.accepted?.candidate?.routeId)

        val absentCandidate = engine.infer(
            observation(),
            listOf(candidate("absent-candidate", "Flughafen", 180.0)
                .copy(expectedBearingDegrees = null)),
        )
        assertEquals("absent-candidate", absentCandidate.accepted?.candidate?.routeId)
    }

    @Test fun extremeProviderEpochsCannotGainArtificialTimeConfidenceFromOverflow() {
        val engine = TripInferenceEngine(
            TripInferenceSettings(enabled = true, minimumConfidence = 0.90)
        )
        val malformedDirect = candidate("invalid-direct", "Flughafen", 180.0).copy(
            scheduledEpochSeconds = Long.MIN_VALUE + now,
            realtimeDelaySeconds = 0,
        )
        val malformedDelayed = candidate("invalid-delay", "Flughafen", 180.0).copy(
            scheduledEpochSeconds = Long.MAX_VALUE - 100L,
            realtimeDelaySeconds = now.toInt() + 101,
        )
        // Negative/sentinel epochs are now discarded before scoring.
        val direct = engine.infer(observation(), listOf(malformedDirect))
        assertNull(direct.accepted)
        assertTrue(direct.suggestions.isEmpty())
        assertTrue(direct.requiresExplicitConfirmation)

        // Positive but extreme timestamps remain candidates with zero time confidence.
        val delayed = engine.infer(observation(), listOf(malformedDelayed))
        assertNull(delayed.accepted)
        assertEquals(0.0, delayed.suggestions.single().timeScore, 0.0)
        assertTrue(delayed.requiresExplicitConfirmation)
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

    @Test fun emptyPredictionsReportZeroPrecisionInsteadOfPerfectScore() {
        val engine = TripInferenceEngine(TripInferenceSettings(enabled = true))
        val fixtures = listOf(
            LabeledInferenceFixture(
                "true negative",
                observation(),
                listOf(candidate("closed", "Flughafen", 180.0, active = false)),
                null,
            ),
            LabeledInferenceFixture(
                "missed service",
                observation(),
                listOf(candidate("stale", "Flughafen", 180.0, offsetSec = -3600L)),
                "stale",
            ),
        )
        val metrics = TripInferenceEvaluator.evaluate(engine, fixtures)
        assertEquals(2, metrics.total)
        assertEquals(1, metrics.correct)
        assertEquals(0, metrics.falsePositive)
        assertEquals(1, metrics.falseNegative)
        assertEquals(0.0, metrics.coverage, 0.0)
        assertEquals(0.0, metrics.precision, 0.0)
        assertEquals(0.5, metrics.accuracy, 0.0)

        val empty = TripInferenceEvaluator.evaluate(engine, emptyList())
        assertEquals(0.0, empty.precision, 0.0)
        assertEquals(0.0, empty.coverage, 0.0)
    }
}
