package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteRefreshSelectionTest {
    private val origin = MapPoint(52.663, 8.233, "Lohne")
    private val destination = MapPoint(53.079, 8.801, "Bremen")
    private val stops = listOf(
        RouteStop(id = "lo", name = "Lohne", latitude = 52.663, longitude = 8.233, plannedDeparture = "10:00"),
        RouteStop(id = "ve", name = "Vechta", latitude = 52.73, longitude = 8.29),
        RouteStop(id = "br", name = "Bremen", latitude = 53.079, longitude = 8.801, plannedArrival = "11:00"),
    )

    private fun option() = RouteOption(
        id = "provider-itinerary-1",
        origin = origin,
        destination = destination,
        line = "RE",
        direction = "Bremen Hbf",
        departure = "10:00",
        arrival = "11:00",
        walkingMinutes = 0,
        transfers = 0,
        tripIds = listOf("trip-42"),
        stops = stops,
        departureEpochMillis = 1_792_400_400_000L,
        arrivalEpochMillis = 1_792_404_000_000L,
    )

    @Test
    fun samePhysicalTripSurvivesRealtimeDisplayChanges() {
        val old = option()
        val update = old.copy(
            line = "RE updated",
            departure = "10:05",
            arrival = "11:07",
            stops = old.stops.map { it.copy(name = "Updated " + it.name, platform = "2", arrival = "11:07") },
        )
        assertEquals(old.id, retainSelectedRouteAfterRefresh(old.id, listOf(old), listOf(update)))
    }

    @Test
    fun reusedUiIdForAnotherTripIsNotImplicitConsent() {
        val old = option()
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old), listOf(old.copy(tripIds = listOf("trip-43")))))
    }

    @Test
    fun repeatedGtfsTripIdOnNextServiceDayRequiresReselection() {
        val old = option()
        assertNull(retainSelectedRouteAfterRefresh(
            old.id,
            listOf(old),
            listOf(old.copy(departureEpochMillis = old.departureEpochMillis!! + 86_400_000L)),
        ))
    }

    @Test
    fun changedBoardingOrAlightingStopRolesRequireReselection() {
        val old = option()
        assertNull(retainSelectedRouteAfterRefresh(
            old.id,
            listOf(old),
            listOf(old.copy(stops = old.stops.reversed())),
        ))
        assertNull(retainSelectedRouteAfterRefresh(
            old.id,
            listOf(old),
            listOf(old.copy(stops = old.stops.drop(1))),
        ))
    }

    @Test
    fun changedStopCoordinatesOrDestinationRequireReselection() {
        val old = option()
        assertNull(retainSelectedRouteAfterRefresh(
            old.id,
            listOf(old),
            listOf(old.copy(stops = old.stops.dropLast(1) + old.stops.last().copy(latitude = 53.08))),
        ))
        assertNull(retainSelectedRouteAfterRefresh(
            old.id,
            listOf(old),
            listOf(old.copy(destination = destination.copy(latitude = 53.08))),
        ))
    }

    @Test
    fun insufficientSourceIdentityFailsClosedWithoutGuessing() {
        val old = option()
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old), listOf(old.copy(departureEpochMillis = null))))
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old.copy(departureEpochMillis = null)), listOf(old)))
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old.copy(tripIds = emptyList())), listOf(old)))
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old), listOf(old.copy(stops = old.stops.map { it.copy(id = null) }))))
    }

    @Test
    fun missingOrAmbiguousCandidateDoesNotSelectAnotherRoute() {
        val old = option()
        assertNull(retainSelectedRouteAfterRefresh(null, listOf(old), listOf(old)))
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old), emptyList()))
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old), listOf(old, old)))
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old, old), listOf(old)))
        assertNull(retainSelectedRouteAfterRefresh(old.id, listOf(old), listOf(old.copy(id = "replacement"))))
    }
}
