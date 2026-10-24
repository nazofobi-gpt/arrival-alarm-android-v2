package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceLastKnownFixPolicyTest {
    private val now = 1_000_000_000_000L
    private val second = 1_000_000_000L

    private fun sample(
        latitude: Double = 53.08,
        longitude: Double = 8.81,
        accuracy: Float? = 25f,
        ageSeconds: Long = 10L,
        elapsedNanos: Long = now - ageSeconds * second,
        wallTime: Long = 1_790_000_000_000L,
        provider: String = "gps",
    ) = DeviceLocationCandidate(
        latitude, longitude, accuracy, elapsedNanos, wallTime, provider,
    )

    @Test fun newestMonotonicFixWinsEvenWhenWallClockTimestampIsOlder() {
        val newer = sample(ageSeconds = 4L, wallTime = 100L, provider = "gps")
        val olderWithFutureWallTime = sample(ageSeconds = 40L, wallTime = Long.MAX_VALUE, provider = "network")
        assertEquals(newer, DeviceLastKnownFixPolicy.select(listOf(olderWithFutureWallTime, newer), now))
    }

    @Test fun expiredAndFutureElapsedFixesCannotSeedCurrentLocation() {
        val maxAge = DeviceLastKnownFixPolicy.MAX_FIX_AGE_MILLIS
        val onBoundary = sample(elapsedNanos = now - maxAge * 1_000_000L)
        assertEquals(onBoundary, DeviceLastKnownFixPolicy.select(listOf(onBoundary), now))
        assertNull(DeviceLastKnownFixPolicy.select(listOf(sample(elapsedNanos = now - (maxAge + 1L) * 1_000_000L)), now))
        assertNull(DeviceLastKnownFixPolicy.select(listOf(sample(elapsedNanos = now + 1L)), now))
        assertNull(DeviceLastKnownFixPolicy.select(listOf(sample(elapsedNanos = 0L)), now))
        assertNull(DeviceLastKnownFixPolicy.select(listOf(sample()), 0L))
    }

    @Test fun invalidCoordinatesAccuracyAndMissingTimestampAreRejected() {
        val invalid = listOf(
            sample(latitude = Double.NaN),
            sample(latitude = 90.01),
            sample(longitude = -180.1),
            sample(longitude = Double.POSITIVE_INFINITY),
            sample(accuracy = null),
            sample(accuracy = Float.NaN),
            sample(accuracy = Float.POSITIVE_INFINITY),
            sample(accuracy = -0.1f),
            sample(wallTime = 0L),
        )
        assertNull(DeviceLastKnownFixPolicy.select(invalid, now))
    }

    @Test fun missingProviderProvenanceCannotSeedCurrentLocation() {
        val valid = sample(provider = "network", ageSeconds = 20L)
        val missingSource = listOf(
            sample(provider = "", ageSeconds = 1L),
            sample(provider = "   ", ageSeconds = 2L),
        )
        assertNull(DeviceLastKnownFixPolicy.select(missingSource, now))
        // A recent but source-less candidate cannot shadow an older verified provider.
        assertEquals(valid, DeviceLastKnownFixPolicy.select(missingSource + valid, now))
    }

    @Test fun approximatePermissionRemainsUsableForOriginWithoutConfirmingArrival() {
        val approximate = sample(accuracy = 2_000f, provider = "network")
        assertEquals(approximate, DeviceLastKnownFixPolicy.select(listOf(approximate), now))
    }

    @Test fun tieUsesMoreAccurateFixAndNoCandidatesFailsClosed() {
        val worse = sample(accuracy = 100f, provider = "network")
        val better = sample(accuracy = 5f, provider = "gps")
        assertEquals(better, DeviceLastKnownFixPolicy.select(listOf(better, worse), now))
        assertNull(DeviceLastKnownFixPolicy.select(emptyList(), now))
    }

    @Test fun legalGeographicBoundsAreAccepted() {
        val boundary = sample(latitude = -90.0, longitude = 180.0, accuracy = 0f)
        assertEquals(boundary, DeviceLastKnownFixPolicy.select(listOf(boundary), now))
    }
}
