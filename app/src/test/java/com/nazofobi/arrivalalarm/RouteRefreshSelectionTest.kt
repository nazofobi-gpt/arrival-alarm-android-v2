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
    fun impossibleFutureBoardingEpochCannotRetainSelectedTrip() {
        val impossible = option().copy(departureEpochMillis = Long.MAX_VALUE)
        assertNull(retainSelectedRouteAfterRefresh(
            impossible.id, listOf(impossible), listOf(impossible),
        ))

        val beyondSupportedUtc = option().copy(departureEpochMillis = 253_402_300_800_000L)
        assertNull(retainSelectedRouteAfterRefresh(
            beyondSupportedUtc.id, listOf(beyondSupportedUtc), listOf(beyondSupportedUtc),
        ))

        // This fixture must have a coherent arrival as well as a valid departure:
        // retaining a route with year-9999 boarding and year-2026 arrival is unsafe.
        val supportedBoundary = option().copy(
            departureEpochMillis = 253_402_300_799_999L,
            arrivalEpochMillis = 253_402_300_799_999L,
        )
        assertEquals(supportedBoundary.id, retainSelectedRouteAfterRefresh(
            supportedBoundary.id, listOf(supportedBoundary), listOf(supportedBoundary),
        ))
    }

    @Test
    fun arrivalBeforeBoardingInEitherSnapshotInvalidatesRetainedSelection() {
        val old = option()
        val contradictory = old.copy(arrivalEpochMillis = old.departureEpochMillis!! - 1L)
        assertNull(retainSelectedRouteAfterRefresh(
            old.id, listOf(old), listOf(contradictory),
        ))
        assertNull(retainSelectedRouteAfterRefresh(
            old.id, listOf(contradictory), listOf(old),
        ))
        assertNull(retainSelectedRouteAfterRefresh(
            old.id, listOf(contradictory), listOf(contradictory),
        ))
    }

    @Test
    fun unsupportedArrivalEpochInEitherSnapshotInvalidatesRetainedSelection() {
        val old = option()
        for (invalidEpoch in listOf(253_402_300_800_000L, Long.MAX_VALUE)) {
            val invalid = old.copy(arrivalEpochMillis = invalidEpoch)
            assertNull(retainSelectedRouteAfterRefresh(
                old.id, listOf(old), listOf(invalid),
            ))
            assertNull(retainSelectedRouteAfterRefresh(
                old.id, listOf(invalid), listOf(old),
            ))
        }
    }

    @Test
    fun arrivalDelayAfterSameBoardingStillRetainsSelection() {
        val old = option()
        val delayed = old.copy(arrivalEpochMillis = old.arrivalEpochMillis!! + 300_000L)
        assertEquals(old.id, retainSelectedRouteAfterRefresh(
            old.id, listOf(old), listOf(delayed),
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
    fun identicallyInvalidCoordinatesRequireReselection() {
        val valid = option()
        val invalidOrigin = valid.copy(origin = origin.copy(latitude = 91.0))
        assertNull(retainSelectedRouteAfterRefresh(
            invalidOrigin.id, listOf(invalidOrigin), listOf(invalidOrigin),
        ))

        val invalidDestination = valid.copy(destination = destination.copy(longitude = -181.0))
        assertNull(retainSelectedRouteAfterRefresh(
            invalidDestination.id, listOf(invalidDestination), listOf(invalidDestination),
        ))

        val infiniteStop = valid.copy(stops = stops.mapIndexed { index, stop ->
            if (index == 1) stop.copy(latitude = Double.POSITIVE_INFINITY) else stop
        })
        assertNull(retainSelectedRouteAfterRefresh(
            infiniteStop.id, listOf(infiniteStop), listOf(infiniteStop),
        ))

        val invalidUpdatedStop = valid.copy(stops = stops.mapIndexed { index, stop ->
            if (index == 1) stop.copy(longitude = Double.NaN) else stop
        })
        assertNull(retainSelectedRouteAfterRefresh(
            valid.id, listOf(valid), listOf(invalidUpdatedStop),
        ))
    }

    @Test
    fun partialStopCoordinatePairsAreNotValidTripIdentityEvidence() {
        val old = option()
        // MapPoint requires a complete non-null coordinate pair at the type level.
        // RouteStop allows nullable geometry, so partial pairs can enter via transit data.
        val missingStopLongitude = old.copy(stops = stops.mapIndexed { index, stop ->
            if (index == 1) stop.copy(longitude = null) else stop
        })
        val missingStopLatitude = old.copy(stops = stops.mapIndexed { index, stop ->
            if (index == 1) stop.copy(latitude = null) else stop
        })
        for (invalid in listOf(missingStopLongitude, missingStopLatitude)) {
            assertNull(retainSelectedRouteAfterRefresh(
                old.id, listOf(invalid), listOf(invalid),
            ))
            assertNull(retainSelectedRouteAfterRefresh(
                old.id, listOf(old), listOf(invalid),
            ))
            assertNull(retainSelectedRouteAfterRefresh(
                old.id, listOf(invalid), listOf(old),
            ))
        }
    }

    @Test
    fun unknownStopCoordinatesDoNotInvalidateOtherVerifiedEvidence() {
        val withoutOptionalStopGeometry = option().copy(stops = stops.map { it.copy(
            latitude = null, longitude = null,
        ) })
        assertEquals(
            withoutOptionalStopGeometry.id,
            retainSelectedRouteAfterRefresh(
                withoutOptionalStopGeometry.id,
                listOf(withoutOptionalStopGeometry),
                listOf(withoutOptionalStopGeometry),
            ),
        )
    }

    @Test
    fun missingStopSequenceCannotProveTripIdentity() {
        val valid = option()
        val missingStops = valid.copy(stops = emptyList())
        // A transit trip without ordered physical stops cannot prove that the
        // refreshed itinerary still boards and alights at the selected roles.
        assertNull(retainSelectedRouteAfterRefresh(
            valid.id, listOf(missingStops), listOf(missingStops),
        ))
        assertNull(retainSelectedRouteAfterRefresh(
            valid.id, listOf(valid), listOf(missingStops),
        ))
        assertNull(retainSelectedRouteAfterRefresh(
            valid.id, listOf(missingStops), listOf(valid),
        ))
    }

    @Test
    fun blankRouteKeysCannotRetainTripConsentOnRefresh() {
        val valid = option()
        for (blank in listOf("", " ", "\t")) {
            val corrupted = valid.copy(id = blank)
            assertNull(retainSelectedRouteAfterRefresh(blank, listOf(corrupted), listOf(corrupted)))
        }
        assertNull(retainSelectedRouteAfterRefresh("", listOf(valid), listOf(valid)))
    }

    @Test
    fun invisibleOrControlRouteKeyCannotSilentlyRetainJourneyConsent() {
        val valid = option()
        val rejectedKeys = listOf(
            "\u200B", // nonblank zero-width provider identifier
            "trip\u202Ereverse", // bidirectional override
            "trip\nnewline",
            "trip\tseparator",
        )
        for (id in rejectedKeys) {
            val candidate = valid.copy(id = id)
            assertNull(retainSelectedRouteAfterRefresh(
                id, listOf(candidate), listOf(candidate),
            ))
        }
        // Unicode letters remain valid physical journey keys.
        val localized = valid.copy(id = "München-RE-42")
        assertEquals(localized.id, retainSelectedRouteAfterRefresh(
            localized.id, listOf(localized), listOf(localized),
        ))
    }

    @Test
    fun invisibleTripIdsCannotProvePhysicalJourneyIdentity() {
        val valid = option()
        for (invalidId in listOf("trip\u200Bhidden", "trip\u202Ereversed", "trip\nnewline", "trip\tseparator")) {
            val malformed = valid.copy(tripIds = listOf(invalidId))
            // A repeat of the same invalid provider identifier in both snapshots
            // is not independent evidence of a physical selected TripInstance.
            assertNull(retainSelectedRouteAfterRefresh(
                malformed.id, listOf(malformed), listOf(malformed),
            ))
        }
        val localized = valid.copy(tripIds = listOf("München-RE-42"))
        assertEquals(localized.id, retainSelectedRouteAfterRefresh(
            localized.id, listOf(localized), listOf(localized),
        ))
    }

    @Test
    fun invisibleStopIdsCannotPreserveBoardingAndAlightingConsent() {
        val valid = option()
        for (invalidId in listOf("stop\u200Bhidden", "stop\u202Ereversed", "stop\nnewline", "stop\tseparator")) {
            val malformed = valid.copy(stops = valid.stops.mapIndexed { index, stop ->
                if (index == 1) stop.copy(id = invalidId) else stop
            })
            assertNull(retainSelectedRouteAfterRefresh(
                malformed.id, listOf(malformed), listOf(malformed),
            ))
        }
        val localized = valid.copy(stops = valid.stops.mapIndexed { index, stop ->
            if (index == 1) stop.copy(id = "Köln-Hbf") else stop
        })
        assertEquals(localized.id, retainSelectedRouteAfterRefresh(
            localized.id, listOf(localized), listOf(localized),
        ))
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
