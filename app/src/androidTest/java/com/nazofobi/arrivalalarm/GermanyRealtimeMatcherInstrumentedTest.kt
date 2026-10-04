package com.nazofobi.arrivalalarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GermanyRealtimeMatcherInstrumentedTest {
    @Test
    fun exactTripAndAssignedPlatformMatchOnApi36() {
        val trip = GtfsScheduleTrip(
            id = "T1",
            routeId = "R1",
            serviceId = "S",
            headsign = null,
            directionId = 0,
            shapeId = null,
            wheelchairAccessible = null,
            bikesAllowed = null,
        )
        val staticData = object : GermanyRealtimeStaticData {
            override fun trip(tripId: String) = trip.takeIf { tripId == "T1" }

            override fun route(routeId: String) =
                GtfsScheduleRoute("R1", "agency", "R1", null, 3).takeIf { routeId == "R1" }

            override fun stop(stopId: String) = when (stopId) {
                "scheduled" -> GtfsScheduleStop(
                    "scheduled",
                    "Scheduled",
                    52.0,
                    8.0,
                    null,
                    0,
                    "1",
                    null,
                )
                "assigned" -> GtfsScheduleStop(
                    "assigned",
                    "Assigned",
                    52.0,
                    8.0,
                    null,
                    0,
                    "2",
                    null,
                )
                else -> null
            }

            override fun stopTimesForTrip(
                tripId: String,
                limit: Int,
            ) = if (tripId == "T1") {
                listOf(
                    GtfsScheduleStopTime(
                        "T1",
                        1,
                        "scheduled",
                        "08:00:00",
                        "08:00:00",
                        null,
                        null,
                        null,
                        null,
                    ),
                ).take(limit)
            } else {
                emptyList()
            }

            override fun frequenciesForTrip(
                tripId: String,
                limit: Int,
            ) = emptyList<GtfsScheduleFrequency>()

            override fun tripsForRouteDirection(
                routeId: String,
                directionId: Int,
                limit: Int,
            ) = emptyList<GtfsScheduleTrip>()

            override fun isServiceActive(
                serviceId: String,
                serviceDate: GtfsServiceDate,
            ) = true
        }

        val update = GermanyRealtimeTripUpdate(
            entityId = "entity",
            tripId = "T1",
            routeId = "R1",
            startDate = null,
            scheduleRelationship = "SCHEDULED",
            cancelled = false,
            stops = listOf(
                GermanyRealtimeStopUpdate(
                    stopSequence = 1,
                    stopId = "assigned",
                    arrivalDelaySeconds = null,
                    departureDelaySeconds = 60,
                    arrivalTimeEpochSeconds = null,
                    departureTimeEpochSeconds = null,
                    assignedStopId = "assigned",
                ),
            ),
            directionId = 0,
            selectorValidity = GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
        )
        val snapshot = GermanyRealtimeSnapshot(
            source = GermanyGtfsRealtimeProvenance.PROVIDER,
            license = GermanyGtfsRealtimeProvenance.LICENSE,
            feedTimestampEpochSeconds = 1_800_000_000L,
            fetchedAtEpochSeconds = 1_800_000_005L,
            tripUpdates = listOf(update),
            serviceAlerts = emptyList(),
            gtfsRealtimeVersion = "2.0",
            incrementality = "FULL_DATASET",
            feedVersion = "api36",
        )

        val result = GermanyRealtimeMatcher(staticData).match(snapshot, 1_800_000_010L)
        assertTrue(result is GermanyRealtimeOverlayResult.Matched)
        val stop = (result as GermanyRealtimeOverlayResult.Matched)
            .tripOverlays.single().stopUpdates.single()
        assertEquals("scheduled", stop.scheduledStopId)
        assertEquals("assigned", stop.assignedStopId)
        assertEquals("2", stop.platformCode)
        assertEquals(60, stop.departureDelaySeconds)
        assertEquals("api36", result.provenance.feedVersion)
    }
}
