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
