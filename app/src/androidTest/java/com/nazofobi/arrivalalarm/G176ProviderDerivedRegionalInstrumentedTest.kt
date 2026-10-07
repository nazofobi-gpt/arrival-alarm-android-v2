package com.nazofobi.arrivalalarm

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.transit.realtime.GtfsRealtime
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G176ProviderDerivedRegionalInstrumentedTest {
    @Test
    fun retainedProviderSamplesTraverseProductionClientAndMatcher() {
        val samplePath = InstrumentationRegistry.getArguments().getString("providerSamplesPath").orEmpty()
        assumeTrue("providerSamplesPath instrumentation argument is required", samplePath.isNotBlank())
        val root = JSONObject(File(samplePath).readText())
        assertEquals("G176_PROVIDER_DERIVED_REGIONAL_SAMPLES_V1", root.getString("schema"))

        val realtime = root.getJSONObject("realtime")
        val header = GtfsRealtime.FeedHeader.parseFrom(decode(realtime.getString("headerPbBase64")))
        val feedTimestamp = header.timestamp
        val samples = root.getJSONObject("samples")
        val expectedRegions = listOf("bremen_vbn_area", "berlin")
        var matchedAlertCount = 0
        var validAssignedStopEvidenceCount = 0

        expectedRegions.forEach { region ->
            assertTrue("missing provider-derived region $region", samples.has(region))
            val sample = samples.getJSONObject(region)
            val entity = GtfsRealtime.FeedEntity.parseFrom(decode(sample.getString("entityPbBase64")))
            val builder = GtfsRealtime.FeedMessage.newBuilder().setHeader(header).addEntity(entity)
            val alerts = root.getJSONArray("associatedAlerts")
            repeat(alerts.length()) { index ->
                builder.addEntity(
                    GtfsRealtime.FeedEntity.parseFrom(
                        decode(alerts.getJSONObject(index).getString("entityPbBase64")),
                    ),
                )
            }

            val fetchedAt = feedTimestamp
            val parsed = GermanyGtfsRealtimeClient(
                clock = EpochClock { fetchedAt },
                streamLoader = { builder.build().toByteArray().inputStream() },
            ).fetch()
            assertTrue("$region retained provider protobuf did not parse", parsed is GermanyRealtimeFetchResult.Available)

            val data = JsonStaticData(sample)
            val matcher = GermanyRealtimeMatcher(data, freshWindowSeconds = 120)
            val fresh = matcher.match(parsed, feedTimestamp)
            assertTrue("$region provider sample did not match static IDs", fresh is GermanyRealtimeOverlayResult.Matched)
            val matched = fresh as GermanyRealtimeOverlayResult.Matched
            assertEquals(GermanyRealtimeOverlayFreshnessState.FRESH, matched.freshness)
            assertEquals(sample.getJSONObject("staticTrip").getString("trip_id"), matched.tripOverlays.single().staticTrip.id)

            val summary = sample.getJSONObject("realtimeSummary")
            if (summary.getInt("delay_count") > 0) {
                assertTrue("$region delay evidence was lost", matched.tripOverlays.single().stopUpdates.any {
                    it.arrivalDelaySeconds != null || it.departureDelaySeconds != null
                })
            }
            if (summary.getInt("assigned_count") > 0) {
                // Raw provider assignments are not necessarily protocol-valid or matchable
                // to this static timetable. Rejecting malformed records is intentional.
                val staticSequenceCounts = mutableMapOf<Int, Int>()
                val staticStopTimes = sample.getJSONArray("staticStopTimes")
                repeat(staticStopTimes.length()) { index ->
                    val sequence = staticStopTimes.getJSONObject(index)
                        .getString("stop_sequence").toInt()
                    staticSequenceCounts[sequence] = (staticSequenceCounts[sequence] ?: 0) + 1
                }
                val assigned = entity.tripUpdate.stopTimeUpdateList.filter { stop ->
                    stop.hasStopTimeProperties() &&
                        stop.stopTimeProperties.assignedStopId.isNotBlank()
                }
                assertEquals("$region raw assignment count drifted from protobuf",
                    summary.getInt("assigned_count"), assigned.size)
                val matchable = assigned.filter { stop ->
                    stop.hasStopSequence() &&
                        staticSequenceCounts[stop.stopSequence] == 1 &&
                        (stop.stopId.isBlank() ||
                            stop.stopId == stop.stopTimeProperties.assignedStopId)
                }
                matchable.forEach { stop ->
                    assertTrue("$region valid assigned stop was not retained at sequence ${stop.stopSequence}",
                        matched.tripOverlays.single().stopUpdates.any {
                            it.stopSequence == stop.stopSequence &&
                                it.assignedStopId == stop.stopTimeProperties.assignedStopId
                        })
                }
                validAssignedStopEvidenceCount += matchable.size
            }
            matchedAlertCount += matched.serviceAlerts.size

            val stale = matcher.match(parsed, feedTimestamp + 121)
            assertTrue("$region stale sample stopped matching", stale is GermanyRealtimeOverlayResult.Matched)
            assertEquals(GermanyRealtimeOverlayFreshnessState.STALE, (stale as GermanyRealtimeOverlayResult.Matched).freshness)

            val noMatch = GermanyRealtimeMatcher(JsonStaticData.empty()).match(parsed, feedTimestamp)
            assertTrue("$region empty static graph must fail closed as no-match", noMatch is GermanyRealtimeOverlayResult.NoMatch)

            val unavailable = matcher.match(
                GermanyRealtimeFetchResult.Unavailable(GermanyRealtimeUnavailableReason.NETWORK, "fixture-offline"),
                feedTimestamp,
            )
            assertTrue("$region unavailable state was not preserved", unavailable is GermanyRealtimeOverlayResult.Unavailable)
        }

        assertTrue(
            "No protocol-valid, static-matchable provider assigned stop in selected samples; platform acceptance is not proven",
            validAssignedStopEvidenceCount > 0,
        )

        if (root.getJSONArray("associatedAlerts").length() > 0) {
            assertTrue("provider-associated alerts did not survive matcher application", matchedAlertCount > 0)
        }
    }

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.DEFAULT)

    private class JsonStaticData private constructor(
        private val trips: Map<String, GtfsScheduleTrip>,
        private val routes: Map<String, GtfsScheduleRoute>,
        private val stops: Map<String, GtfsScheduleStop>,
        private val stopTimes: Map<String, List<GtfsScheduleStopTime>>,
    ) : GermanyRealtimeStaticData {
        constructor(sample: JSONObject) : this(
            trips = buildTrips(sample),
            routes = buildRoutes(sample),
            stops = buildStops(sample),
            stopTimes = buildStopTimes(sample),
        )

        override fun trip(tripId: String) = trips[tripId]
        override fun route(routeId: String) = routes[routeId]
        override fun stop(stopId: String) = stops[stopId]
        override fun stopTimesForTrip(tripId: String, limit: Int) = stopTimes[tripId].orEmpty().take(limit)
        override fun frequenciesForTrip(tripId: String, limit: Int) = emptyList<GtfsScheduleFrequency>()
        override fun tripsForRouteDirection(routeId: String, directionId: Int, limit: Int) =
            trips.values.filter { it.routeId == routeId && it.directionId == directionId }.take(limit)
        override fun routesMatching(agencyId: String?, routeType: Int?, limit: Int) =
            routes.values.filter { (agencyId == null || it.agencyId == agencyId) && (routeType == null || it.routeType == routeType) }.take(limit)
        override fun tripsServingStop(stopId: String, limit: Int) =
            trips.values.filter { trip -> stopTimes[trip.id].orEmpty().any { it.stopId == stopId } }.take(limit)
        override fun isServiceActive(serviceId: String, serviceDate: GtfsServiceDate) = true

        companion object {
            fun empty() = JsonStaticData(emptyMap(), emptyMap(), emptyMap(), emptyMap())

            private fun buildTrips(sample: JSONObject): Map<String, GtfsScheduleTrip> {
                val row = sample.getJSONObject("staticTrip")
                val id = row.getString("trip_id")
                return mapOf(id to GtfsScheduleTrip(
                    id = id,
                    routeId = row.getString("route_id"),
                    serviceId = row.optString("service_id", "fixture-service"),
                    headsign = row.nullableString("trip_headsign"),
                    directionId = row.nullableInt("direction_id"),
                    shapeId = row.nullableString("shape_id"),
                    wheelchairAccessible = row.nullableInt("wheelchair_accessible"),
                    bikesAllowed = row.nullableInt("bikes_allowed"),
                ))
            }

            private fun buildRoutes(sample: JSONObject): Map<String, GtfsScheduleRoute> {
                val row = sample.optJSONObject("staticRoute") ?: return emptyMap()
                val id = row.getString("route_id")
                return mapOf(id to GtfsScheduleRoute(
                    id = id,
                    agencyId = row.nullableString("agency_id"),
                    shortName = row.nullableString("route_short_name"),
                    longName = row.nullableString("route_long_name"),
                    routeType = row.nullableInt("route_type"),
                ))
            }

            private fun buildStops(sample: JSONObject): Map<String, GtfsScheduleStop> {
                val obj = sample.getJSONObject("staticStops")
                return obj.keys().asSequence().mapNotNull { id ->
                    val row = obj.optJSONObject(id) ?: return@mapNotNull null
                    id to GtfsScheduleStop(
                        id = id,
                        name = row.optString("stop_name", id),
                        latitude = row.optString("stop_lat", "0").toDoubleOrNull() ?: 0.0,
                        longitude = row.optString("stop_lon", "0").toDoubleOrNull() ?: 0.0,
                        parentStationId = row.nullableString("parent_station"),
                        locationType = row.nullableInt("location_type"),
                        platformCode = row.nullableString("platform_code"),
                        wheelchairBoarding = row.nullableInt("wheelchair_boarding"),
                    )
                }.toMap()
            }

            private fun buildStopTimes(sample: JSONObject): Map<String, List<GtfsScheduleStopTime>> {
                val rows = sample.getJSONArray("staticStopTimes")
                val out = mutableMapOf<String, MutableList<GtfsScheduleStopTime>>()
                repeat(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    val tripId = row.getString("trip_id")
                    out.getOrPut(tripId) { mutableListOf() }.add(GtfsScheduleStopTime(
                        tripId = tripId,
                        stopSequence = row.getString("stop_sequence").toInt(),
                        stopId = row.getString("stop_id"),
                        arrivalTime = row.nullableString("arrival_time"),
                        departureTime = row.nullableString("departure_time"),
                        pickupType = row.nullableInt("pickup_type"),
                        dropOffType = row.nullableInt("drop_off_type"),
                        shapeDistanceTraveled = row.optString("shape_dist_traveled", "").toDoubleOrNull(),
                        timepoint = row.nullableInt("timepoint"),
                    ))
                }
                return out
            }

            private fun JSONObject.nullableString(key: String): String? =
                if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

            private fun JSONObject.nullableInt(key: String): Int? =
                nullableString(key)?.toIntOrNull()
        }
    }
}
