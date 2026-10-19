package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitExperienceTest {
    private val now = 1_000L
    private val departures = listOf(
        Departure("old","1","A",900,realtimeEpochSeconds=950),
        Departure("b","2","B",1_300,realtimeEpochSeconds=1_100),
        Departure("cancelled","3","C",1_050,cancelled=true),
        Departure("a","1","A",1_200)
    )

    @Test fun boardUsesRealtimeWhenSupportedAndFiltersPastAndCancelled() {
        val engine=TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures=true))
        val board=engine.departureBoard(departures,now)
        assertEquals(listOf("b","a"),board.map{it.tripId})
        assertTrue(board.first().isRealtime)
    }

    @Test fun invalidRealtimeEpochFallsBackToScheduledDeparture() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures = true))
        val samples = listOf(
            Departure("zero", "1", "Lohne", 1_100L, realtimeEpochSeconds = 0L),
            Departure("negative", "2", "Diepholz", 1_200L, realtimeEpochSeconds = -1L),
            Departure("sentinel", "3", "Bremen", 1_300L, realtimeEpochSeconds = Long.MIN_VALUE),
        )
        val board = engine.departureBoard(samples, now)
        assertEquals(listOf("zero", "negative", "sentinel"), board.map { it.tripId })
        assertEquals(listOf(1_100L, 1_200L, 1_300L), board.map { it.effectiveEpochSeconds })
        assertTrue(board.none { it.isRealtime })
    }

    @Test fun validRealtimeTimestampStillOverridesSchedule() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures = true))
        val departure = Departure("late", "1", "Bremen", 1_300L, realtimeEpochSeconds = 1_050L)
        val board = engine.departureBoard(listOf(departure), now)
        assertEquals(1_050L, board.single().effectiveEpochSeconds)
        assertTrue(board.single().isRealtime)
    }

    @Test fun unsupportedRealtimeFallsBackToScheduleWithoutInventingLiveState() {
        val engine=TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures=false))
        val board=engine.departureBoard(departures,now)
        assertEquals(listOf("a","b"),board.map{it.tripId})
        assertFalse(board.any{it.isRealtime})
        assertTrue(engine.alerts(listOf(ServiceAlert("x","x","x"))).isEmpty())
        assertTrue(engine.vehicles(listOf(LiveVehicle("a",GeoPoint(1.0,2.0)))).isEmpty())
    }

    @Test fun alternateDepartureExcludesSelectedTrip() {
        val engine=TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures=true))
        assertEquals(listOf("a"),engine.alternateDepartures(departures,"b",now).map{it.tripId})
    }

    @Test fun selectedTripCannotExhaustEightDepartureSlotsBeforeAlternatives() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures = true))
        val selected = (0 until 10).map { index ->
            Departure("selected", "1", "A", 1_010L + index)
        }
        val alternatives = listOf(
            Departure("alternative-a", "2", "B", 1_100L),
            Departure("alternative-b", "3", "C", 1_200L),
        )
        assertEquals(
            listOf("alternative-a", "alternative-b"),
            engine.alternateDepartures(selected + alternatives, "selected", now)
                .map { it.tripId },
        )
    }

    @Test fun alternateDeparturesRemainSortedFilteredAndBounded() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures = true))
        val selected = (0 until 9).map { Departure("selected", "1", "A", 1_010L + it) }
        val alternates = (0 until 10).map { index ->
            Departure("alternative-$index", "2", "B", 1_100L + index * 10L)
        }
        val candidates = selected + alternates + listOf(
            Departure("cancelled", "3", "C", 1_001L, cancelled = true),
            Departure("past", "4", "D", 900L),
        )
        val board = engine.alternateDepartures(candidates, "selected", now)
        assertEquals((0 until 8).map { "alternative-$it" }, board.map { it.tripId })
    }

    @Test fun stationEvidenceRequiresRealServiceDayAndValidKeys() {
        val trip = GtfsScheduleTrip(
            id = "T17", routeId = "R5", serviceId = "WEEKDAY",
            headsign = "Bremen", directionId = 1,
            shapeId = null, wheelchairAccessible = null, bikesAllowed = null,
        )
        val date = GtfsServiceDate(2026, 10, 19)
        val evidence = stationDepartureEvidence(trip, stopSequence = 7, serviceDate = date)

        assertEquals(GtfsTripInstanceIdentity.scheduled(trip, date), evidence?.identity)
        assertEquals(7, evidence?.boardingStopSequence)
        assertEquals(null, stationDepartureEvidence(trip, 7, null))
        assertEquals(null, stationDepartureEvidence(trip, -1, date))
        for (invalid in listOf(
            trip.copy(id = " "),
            trip.copy(serviceId = ""),
            trip.copy(routeId = " "),
        )) {
            assertEquals(null, stationDepartureEvidence(invalid, 7, date))
        }
        val followingDay = GtfsServiceDate(2026, 10, 20)
        assertFalse(evidence?.identity ==
            stationDepartureEvidence(trip, 7, followingDay)?.identity)
    }

    @Test fun stationBoardPreservesDistinctLoopStopBoardingPositions() {
        // A single GTFS trip can call at the same station twice. Different
        // stop_sequence values must not collapse into one departure card.
        val firstVisit = Departure(
            tripId = "loop-trip",
            line = "RB 1",
            direction = "Bremen",
            scheduledEpochSeconds = 1_200L,
            routeId = "loop-route",
            boardingStopSequence = 2,
        )
        val repeatUpdate = firstVisit.copy(realtimeEpochSeconds = 1_240L)
        val secondVisit = firstVisit.copy(boardingStopSequence = 9)
        val deduped = distinctStationDepartures(
            listOf(firstVisit, repeatUpdate, secondVisit, firstVisit),
        )

        assertEquals(listOf(2, 9), deduped.map { it.boardingStopSequence })
        assertEquals(firstVisit, deduped.first())
        assertFalse(firstVisit.sameScheduledInstanceAs(secondVisit))
        assertTrue(firstVisit.sameScheduledInstanceAs(repeatUpdate))
    }

    @Test fun frequencyInstancesRemainDistinctAlternatives() {
        val first = Departure(
            tripId = "template", line = "RE 9", direction = "Bremen",
            scheduledEpochSeconds = 1_200L, routeId = "route-re9",
            realtimeTripStartDate = "20261019", realtimeTripStartTime = "08:00:00",
        )
        val next = first.copy(
            scheduledEpochSeconds = 1_500L,
            realtimeTripStartTime = "08:05:00",
        )
        val engine = TransitExperienceEngine(TransitProviderCapabilities())
        assertEquals(listOf(1_500L),
            engine.alternateDepartures(listOf(first, next), first, now)
                .map { it.scheduledEpochSeconds })
        assertFalse(first.sameScheduledInstanceAs(next))
        assertTrue(first.sameScheduledInstanceAs(first.copy(realtimeEpochSeconds = 1_350L)))
    }

    @Test fun excludedFrequencyInstancesDoNotConsumeAlternativeLimit() {
        val selected = Departure("template", "RE", "Lohne", 1_200L)
        val manySameInstance = (0 until 12).map { selected }
        val alternatives = (0 until 10).map { selected.copy(scheduledEpochSeconds = 1_300L + it) }
        val found = TransitExperienceEngine(TransitProviderCapabilities())
            .alternateDepartures(manySameInstance + alternatives, selected, now)
        assertEquals((0 until 8).map { 1_300L + it }, found.map { it.scheduledEpochSeconds })
    }

    @Test fun identicalProviderRowsCannotCrowdOutDistinctDepartures() {
        val repeated = Departure("first", "RE", "Lohne", 1_100L)
        val second = Departure("second", "RE", "Diepholz", 1_200L)
        val third = Departure("third", "RE", "Bremen", 1_300L)
        val all = List(12) { repeated } + listOf(second, third)
        val engine = TransitExperienceEngine(TransitProviderCapabilities())

        // De-duplicate before the result limit; otherwise repeated first rows
        // hide other, independently selectable services.
        assertEquals(
            listOf("first", "second", "third"),
            engine.departureBoard(all, now, limit = 3).map { it.tripId },
        )
        assertEquals(
            listOf("second", "third"),
            engine.alternateDepartures(all, repeated, now).map { it.tripId },
        )
    }

    @Test fun accessibilityPenaltyOnlyAppliesWhenProviderSupportsIt() {
        val pref=AccessibilityPreference(stepFreeOnly=true,extraTransferMinutes=6)
        assertEquals(11,TransitExperienceEngine(TransitProviderCapabilities(accessibility=true)).walkingTransferMinutes(5,pref))
        assertEquals(5,TransitExperienceEngine(TransitProviderCapabilities(accessibility=false)).walkingTransferMinutes(5,pref))
    }

    @Test fun largeAccessibilityTransferDurationsNeverWrapToZero() {
        val accessible = TransitExperienceEngine(TransitProviderCapabilities(accessibility = true))
        val plain = TransitExperienceEngine(TransitProviderCapabilities(accessibility = false))
        val preference = AccessibilityPreference(stepFreeOnly = true, extraTransferMinutes = 6)
        assertEquals(Int.MAX_VALUE, accessible.walkingTransferMinutes(Int.MAX_VALUE - 2, preference))
        assertEquals(Int.MAX_VALUE - 2, plain.walkingTransferMinutes(Int.MAX_VALUE - 2, preference))
        assertEquals(
            Int.MAX_VALUE,
            accessible.walkingTransferMinutes(
                Int.MAX_VALUE,
                AccessibilityPreference(stepFreeOnly = true, extraTransferMinutes = Int.MAX_VALUE),
            ),
        )
    }

    @Test fun negativeAccessibilityPenaltyCannotShortenTransferTime() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(accessibility = true))
        for (negative in listOf(-1, -6, Int.MIN_VALUE)) {
            val preference = AccessibilityPreference(stepFreeOnly = true, extraTransferMinutes = negative)
            assertEquals(
                "Negative accessibility penalty must not reduce base walking minutes: $negative",
                8,
                engine.walkingTransferMinutes(8, preference),
            )
        }
        assertEquals(
            14,
            engine.walkingTransferMinutes(8, AccessibilityPreference(stepFreeOnly = true, extraTransferMinutes = 6)),
        )
    }

    @Test fun negativeBaseMinutesStillClampToZero() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(accessibility = true))
        assertEquals(
            0,
            engine.walkingTransferMinutes(
                -10,
                AccessibilityPreference(stepFreeOnly = true, extraTransferMinutes = 6),
            ),
        )
    }

    @Test fun favoritesAndRecentsAreDeterministicAndBounded() {
        val store=TransitRecentsStore(maxItems=2)
        assertTrue(store.toggleFavorite("s1")); assertFalse(store.toggleFavorite("s1"))
        store.recordTrip("a"); store.recordTrip("b"); store.recordTrip("a"); store.recordTrip("c")
        assertEquals(listOf("c","a"),store.recents())
    }

    @Test fun clearingTripHistoryPreservesFavoritesAndPublishesOnce() {
        var writes = 0
        var persistedFavorites = emptyList<String>()
        var persistedRecents = emptyList<String>()
        val store = TransitRecentsStore(maxItems = 3) { favorites, recents ->
            writes++
            persistedFavorites = favorites
            persistedRecents = recents
        }
        assertTrue(store.toggleFavorite("stop-a"))
        store.recordTrip("trip-a")
        store.recordTrip("trip-b")
        val beforeClear = writes

        store.clearRecents()
        assertTrue(store.recents().isEmpty())
        assertEquals(listOf("stop-a"), store.favorites())
        assertEquals(listOf("stop-a"), persistedFavorites)
        assertTrue(persistedRecents.isEmpty())
        assertEquals(beforeClear + 1, writes)

        store.clearRecents()
        assertEquals(beforeClear + 1, writes) // Empty-history clear is idempotent.
        store.recordTrip("trip-new")
        assertEquals(listOf("trip-new"), persistedRecents)
        assertEquals(listOf("stop-a"), persistedFavorites)
    }

    @Test fun failedRecentsPersistenceKeepsCurrentSessionStateAndCanRecover() {
        var backingStoreAvailable = false
        var persistedFavorites = emptyList<String>()
        var persistedRecents = emptyList<String>()
        val store = TransitRecentsStore(maxItems = 2) { favorites, recents ->
            if (!backingStoreAvailable) throw IllegalStateException("Simulated preferences write failure")
            persistedFavorites = favorites
            persistedRecents = recents
        }

        assertTrue(store.toggleFavorite("stop-a"))
        store.recordTrip("trip-a")
        assertEquals(listOf("stop-a"), store.favorites())
        assertEquals(listOf("trip-a"), store.recents())
        assertTrue(persistedFavorites.isEmpty())
        assertTrue(persistedRecents.isEmpty())

        backingStoreAvailable = true
        store.recordTrip("trip-b")
        assertEquals(listOf("stop-a"), persistedFavorites)
        assertEquals(listOf("trip-b", "trip-a"), persistedRecents)
        assertFalse(store.toggleFavorite("stop-a"))
        assertTrue(persistedFavorites.isEmpty())
    }

    @Test fun duplicateRestoredRecentsDoNotDisplaceDistinctJourneyHistory() {
        val store = TransitRecentsStore(
            maxItems = 3,
            recentTripIds = listOf("trip-a", "trip-a", "trip-b", "trip-c"),
        )
        assertEquals(listOf("trip-a", "trip-b", "trip-c"), store.recents())

        store.recordTrip("trip-b")
        assertEquals(listOf("trip-b", "trip-a", "trip-c"), store.recents())
    }

    @Test fun blankIdentifiersAreRejectedWithoutPublishingPhantomState() {
        var publishes = 0
        val store = TransitRecentsStore(
            maxItems = 3,
            favoriteIds = listOf("", "  ", "station"),
            recentTripIds = listOf("", "trip-one", " ", "trip-two"),
        ) { _, _ -> publishes++ }

        assertEquals(listOf("station"), store.favorites())
        assertEquals(listOf("trip-one", "trip-two"), store.recents())
        assertFalse(store.isFavorite("  "))
        assertFalse(store.toggleFavorite(""))
        assertFalse(store.toggleFavorite(" "))
        store.recordTrip("")
        store.recordTrip("   ")
        assertEquals(0, publishes)
        assertEquals(listOf("station"), store.favorites())
        assertEquals(listOf("trip-one", "trip-two"), store.recents())

        assertTrue(store.toggleFavorite("station-two"))
        store.recordTrip("trip-three")
        assertEquals(2, publishes)
        assertEquals(listOf("station", "station-two"), store.favorites())
        assertEquals(listOf("trip-three", "trip-one", "trip-two"), store.recents())
    }

    @Test fun favoritesAndRecentsCanSeedAndPublishPersistentState() {
        var savedFavorites = emptyList<String>()
        var savedRecents = emptyList<String>()
        val store = TransitRecentsStore(
            maxItems = 2,
            favoriteIds = listOf("stop-a"),
            recentTripIds = listOf("old-2", "old-1"),
        ) { favorites, recents ->
            savedFavorites = favorites
            savedRecents = recents
        }

        assertTrue(store.isFavorite("stop-a"))
        assertFalse(store.isFavorite("stop-b"))
        assertTrue(store.toggleFavorite("stop-b"))
        store.recordTrip("new")

        assertEquals(listOf("stop-a", "stop-b"), savedFavorites)
        assertEquals(listOf("new", "old-2"), savedRecents)
    }
    @Test fun malformedScheduledEpochCannotBeMadeSelectableByRealtime() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures = true))
        val invalid = listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
            MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS + 1)
        val departures = invalid.mapIndexed { index, epoch ->
            Departure("invalid-$index", "RE", "Bremen", epoch, realtimeEpochSeconds = 1_200L)
        } + Departure("valid", "RE", "Bremen", 1_300L)
        assertEquals(
            listOf("valid"),
            engine.departureBoard(departures, now).map { it.tripId },
        )
    }

    @Test fun outOfRangeRealtimeEpochFallsBackToVerifiedSchedule() {
        val engine = TransitExperienceEngine(TransitProviderCapabilities(realtimeDepartures = true))
        val samples = listOf(Long.MAX_VALUE, MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS + 1)
            .mapIndexed { index, malformed ->
                Departure("bad-live-$index", "RE", "Bremen", 1_200L, realtimeEpochSeconds = malformed)
            }
        val board = engine.departureBoard(samples, now)
        assertEquals(listOf(1_200L, 1_200L), board.map { it.effectiveEpochSeconds })
        assertTrue(board.none { it.isRealtime })
        val validBoundary = Departure("boundary", "RE", "Bremen", 1_100L,
            realtimeEpochSeconds = MAX_DISPLAYABLE_DEPARTURE_EPOCH_SECONDS)
        assertTrue(engine.departureBoard(listOf(validBoundary), now).single().isRealtime)
    }

}
