package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteShareSummaryTest {
    private fun route() = RouteOption(
        id = "private-trip-identifier",
        origin = MapPoint(52.66, 8.23, "Lohne"),
        destination = MapPoint(53.08, 8.80, "Bremen Hbf"),
        line = "RE 9",
        direction = "Bremen",
        departure = "10:02",
        arrival = "11:05",
        walkingMinutes = 0,
        transfers = 0,
        tripIds = listOf("secret-trip-identifier"),
        stops = listOf(
            RouteStop(id = "one", name = "Lohne", latitude = 52.66, longitude = 8.23, plannedDeparture = "10:00"),
            RouteStop(id = "two", name = "Bremen Hbf", latitude = 53.08, longitude = 8.80, plannedArrival = "11:00"),
        ),
    )

    private fun share(option: RouteOption = route(), offline: Boolean = false) =
        buildRouteShareSummary(
            option, offline, "Journey summary",
            "Verify service before travel",
            "Offline timetable; current service not verified",
            "Unknown",
        )

    @Test fun selectedRouteHasNamedEndpointsAndDisclosure() {
        val summary = share()
        assertTrue(summary.contains("Lohne → Bremen Hbf"))
        assertTrue(summary.contains("RE 9 • Bremen"))
        assertTrue(summary.contains("10:02 → 11:05"))
        assertTrue(summary.contains("Verify service before travel"))
    }

    @Test fun offlineOutputUsesPlannedStopClocks() {
        val summary = share(offline = true)
        assertTrue(summary.contains("10:00 → 11:00"))
        assertFalse(summary.contains("10:02 → 11:05"))
        assertTrue(summary.contains("Offline timetable; current service not verified"))
    }

    @Test fun noGpsOrProviderIdsAreSerialized() {
        val summary = share()
        for (secret in listOf("private-trip-identifier", "secret-trip-identifier", "52.66", "53.08", "8.23", "8.80")) {
            assertFalse("Must not leak: $secret", summary.contains(secret))
        }
    }

    @Test fun untrustedProviderTextCannotInsertExtraLines() {
        val summary = share(route().copy(
            origin = MapPoint(52.66, 8.23, "Lohne\nprivate: token"),
            line = "RE\nAuthorization: secret",
        ))
        assertFalse(summary.contains("\nprivate: token"))
        assertFalse(summary.contains("\nAuthorization: secret"))
        assertTrue(summary.contains("Lohne private: token"))
    }
}
