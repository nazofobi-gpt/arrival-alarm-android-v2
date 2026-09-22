package com.nazofobi.arrivalalarm

import kotlin.math.*

data class TripInferenceSettings(
    val enabled: Boolean = false,
    val minimumConfidence: Double = 0.72,
    val ambiguityMargin: Double = 0.08,
    val topK: Int = 3,
)

data class MovementObservation(
    val point: GeoPoint,
    val speedMps: Double,
    val bearingDegrees: Double?,
    val epochSeconds: Long,
)

data class TripCandidate(
    val routeId: String,
    val line: String,
    val direction: String,
    val anchor: GeoPoint,
    val expectedBearingDegrees: Double?,
    val scheduledEpochSeconds: Long,
    val realtimeDelaySeconds: Int? = null,
    val serviceActive: Boolean = true,
)

data class TripCandidateScore(
    val candidate: TripCandidate,
    val confidence: Double,
    val distanceScore: Double,
    val headingScore: Double,
    val timeScore: Double,
    val movementScore: Double,
    val explanation: String,
)

data class TripInferenceResult(
    val suggestions: List<TripCandidateScore>,
    val accepted: TripCandidateScore?,
    val reason: String,
    val requiresExplicitConfirmation: Boolean = true,
)

class TripInferenceEngine(private val settings: TripInferenceSettings) {
    fun infer(observation: MovementObservation, candidates: List<TripCandidate>): TripInferenceResult {
        if (!settings.enabled) return TripInferenceResult(emptyList(), null, "Sefer algılama kullanıcı tarafından kapalı")
        val scored = candidates
            .asSequence()
            .filter { it.serviceActive }
            .map { score(observation, it) }
            .filter { it.confidence > 0.0 }
            .sortedByDescending { it.confidence }
            .take(settings.topK)
            .toList()

        val best = scored.firstOrNull()
            ?: return TripInferenceResult(emptyList(), null, "Uygun aktif sefer adayı yok")

        if (best.confidence < settings.minimumConfidence) {
            return TripInferenceResult(scored, null, "Güven eşiği altında")
        }
        val runnerUp = scored.getOrNull(1)
        if (runnerUp != null && best.confidence - runnerUp.confidence < settings.ambiguityMargin) {
            return TripInferenceResult(scored, null, "Adaylar belirsiz; kullanıcıya otomatik öneri yok")
        }
        return TripInferenceResult(scored, best, "Aday bulundu; hedef/alarm yalnız kullanıcı onayıyla kurulabilir")
    }

    private fun score(observation: MovementObservation, candidate: TripCandidate): TripCandidateScore {
        val distanceMeters = haversineMeters(observation.point, candidate.anchor)
        val distanceScore = linearScore(distanceMeters, good = 80.0, bad = 900.0)

        val headingScore = when {
            observation.bearingDegrees == null || candidate.expectedBearingDegrees == null -> 0.55
            observation.speedMps < 1.0 -> 0.55
            else -> {
                val delta = angularDifference(observation.bearingDegrees, candidate.expectedBearingDegrees)
                linearScore(delta, good = 12.0, bad = 100.0)
            }
        }

        val expectedEpoch = candidate.scheduledEpochSeconds + (candidate.realtimeDelaySeconds ?: 0)
        val timeDelta = abs(observation.epochSeconds - expectedEpoch).toDouble()
        val timeScore = linearScore(timeDelta, good = 90.0, bad = 900.0)

        val movementScore = when {
            observation.speedMps < 0.5 -> 0.30
            observation.speedMps <= 35.0 -> 1.0
            else -> 0.20
        }

        val confidence = (
            0.38 * distanceScore +
            0.27 * headingScore +
            0.25 * timeScore +
            0.10 * movementScore
        ).coerceIn(0.0, 1.0)

        val explanation = buildString {
            append("${candidate.line} → ${candidate.direction}: ")
            append("mesafe=${distanceMeters.roundToInt()}m, ")
            append("konum=${pct(distanceScore)}, yön=${pct(headingScore)}, ")
            append("zaman=${pct(timeScore)}, hareket=${pct(movementScore)}")
            if (candidate.realtimeDelaySeconds != null) append(", RT=${candidate.realtimeDelaySeconds}s")
        }
        return TripCandidateScore(
            candidate, confidence, distanceScore, headingScore, timeScore, movementScore, explanation
        )
    }

    private fun linearScore(value: Double, good: Double, bad: Double): Double = when {
        value <= good -> 1.0
        value >= bad -> 0.0
        else -> 1.0 - ((value - good) / (bad - good))
    }

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val radius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * radius * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun angularDifference(a: Double, b: Double): Double {
        val raw = abs((a - b) % 360.0)
        return min(raw, 360.0 - raw)
    }

    private fun pct(value: Double) = "${(value * 100).roundToInt()}%"
}

data class LabeledInferenceFixture(
    val label: String,
    val observation: MovementObservation,
    val candidates: List<TripCandidate>,
    val expectedRouteId: String?,
)

data class TripInferenceMetrics(
    val total: Int,
    val correct: Int,
    val falsePositive: Int,
    val falseNegative: Int,
    val coverage: Double,
    val precision: Double,
    val accuracy: Double,
)

object TripInferenceEvaluator {
    fun evaluate(engine: TripInferenceEngine, fixtures: List<LabeledInferenceFixture>): TripInferenceMetrics {
        var correct = 0
        var falsePositive = 0
        var falseNegative = 0
        var suggestions = 0
        var truePositive = 0

        fixtures.forEach { fixture ->
            val predicted = engine.infer(fixture.observation, fixture.candidates).accepted?.candidate?.routeId
            if (predicted != null) suggestions++
            if (predicted == fixture.expectedRouteId) {
                correct++
                if (predicted != null) truePositive++
            } else {
                if (predicted != null) falsePositive++
                if (fixture.expectedRouteId != null) falseNegative++
            }
        }
        val precision = if (suggestions == 0) 1.0 else truePositive.toDouble() / suggestions
        return TripInferenceMetrics(
            total = fixtures.size,
            correct = correct,
            falsePositive = falsePositive,
            falseNegative = falseNegative,
            coverage = if (fixtures.isEmpty()) 0.0 else suggestions.toDouble() / fixtures.size,
            precision = precision,
            accuracy = if (fixtures.isEmpty()) 0.0 else correct.toDouble() / fixtures.size,
        )
    }
}
