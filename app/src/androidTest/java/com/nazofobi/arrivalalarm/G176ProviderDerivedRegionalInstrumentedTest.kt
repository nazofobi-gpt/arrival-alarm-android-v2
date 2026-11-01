package com.nazofobi.arrivalalarm

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.transit.realtime.GtfsRealtime
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class G176ProviderDerivedRegionalInstrumentedTest {
    @Test
    fun retainedProviderSamplesTraverseProductionClientAndMatcher() {
        val samplePath = InstrumentationRegistry.getArguments().getString("providerSamplesPath").orEmpty()
        if (samplePath.isBlank()) {
            // The generic API36 suite may discover this data-dependent test.
            // The dedicated G176 Provider Sample Acceptance workflow always supplies
            // providerSamplesPath and keeps the PROVEN/static-matchable assertions below.
            return
        }
        val root = JSONObject(File(samplePath).readText())
        assertEquals("G176_PROVIDER_DERIVED_REGIONAL_SAMPLES_V1", root.getString("schema"))
        val alternatePaths = InstrumentationRegistry.getArguments()
            .getString("alternatePlatformSamplesPaths").orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val legacyAlternatePath = InstrumentationRegistry.getArguments()
            .getString("alternatePlatformSamplesPath").orEmpty()
        val allAlternatePaths = (alternatePaths + legacyAlternatePath).filter { it.isNotBlank() }.distinct()
        val platformEvidenceRoots = mutableListOf("Germany GTFS-RT" to root)
        allAlternatePaths.forEach { path ->
            val alternateRoot = JSONObject(File(path).readText())
            assertEquals("G176_PROVIDER_DERIVED_REGIONAL_SAMPLES_V1", alternateRoot.getString("schema"))
            val alternateRealtime = alternateRoot.getJSONObject("realtime")
            val alternateStatic = alternateRoot.getJSONObject("static")
            val endpoint = alternateRealtime.getString("endpoint")
            val sourceLabel = when (endpoint) {
                "https://production.gtfsrt.vbb.de/data" -> {
                    assertEquals("https://unternehmen.vbb.de/gtfs", alternateStatic.getString("source"))
                    assertEquals("CC-BY-4.0", alternateRealtime.getString("license"))
                    assertEquals("UNSPECIFIED", alternateStatic.getString("license"))
                    "VBB GTFS-RT"
                }
                "https://germany.motis-project.org/gtfsrt" -> {
                    assertEquals("https://gtfs-source-feeds.transit.land/delfi-germany.zip", alternateStatic.getString("source"))
                    assertEquals("CC-BY-SA-4.0", alternateRealtime.getString("license"))
                    assertEquals("CC-BY-4.0", alternateStatic.getString("license"))
                    "DELFI/MOTIS GTFS-RT"
                }
                "https://gtfs-datenstroeme.tech.deutschebahn.com/db-fernverkehr/gtfsrt_trip_updates.proto" -> {
                    assertEquals("https://gtfs-datenstroeme.tech.deutschebahn.com/db-fernverkehr/gtfs.zip", alternateStatic.getString("source"))
                    assertEquals("DB-API-TERMS", alternateRealtime.getString("license"))
                    assertEquals("DB-API-TERMS", alternateStatic.getString("license"))
                    "DB Fernverkehr GTFS-RT"
                }
                else -> throw AssertionError("Unexpected alternate provider endpoint: $endpoint")
            }
            platformEvidenceRoots += (sourceLabel to alternateRoot)
        }

        val realtime = root.getJSONObject("realtime")
        val header = GtfsRealtime.FeedHeader.parseFrom(decode(realtime.getString("headerPbBase64")))
        val feedTimestamp = header.timestamp
        val samples = root.getJSONObject("samples")
        val expectedRegions = listOf("bremen_vbn_area", "berlin")
        var matchedAlertCount = 0
        var validPlatformEvidenceCount = 0

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

            val parsed = GermanyGtfsRealtimeClient(
                clock = EpochClock { feedTimestamp },
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
            validPlatformEvidenceCount += validatePlatformEvidence(region, sample, entity, data, matched)
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

        platformEvidenceRoots.forEach { (sourceLabel, sourceRoot) ->
            val evidenceCounts = sourceRoot.getJSONObject("platformEvidenceCounts")
            println("G176_PROVIDER_EVIDENCE_COUNTS[$sourceLabel]=$evidenceCounts")
            val state = sourceRoot.getString("platformEvidenceState")
            val platformSample = sourceRoot.optJSONObject("platformEvidence")
            if (state == "PROVEN") {
                assertTrue("$sourceLabel PROVEN state must retain a platform evidence sample", platformSample != null)
                assertTrue(
                    "$sourceLabel has no protocol-valid platform reassignment trip",
                    evidenceCounts.getInt("globalProtocolValidPlatformTripCount") > 0,
                )
                assertTrue(
                    "$sourceLabel has no static-matchable platform reassignment trip",
                    evidenceCounts.getInt("globalStaticMatchablePlatformTripCount") > 0,
                )
            } else {
                assertEquals("$sourceLabel unavailable state must remain explicit", "PROVIDER_SAMPLE_UNAVAILABLE", state)
                assertTrue("$sourceLabel unavailable state must not retain a sample", platformSample == null)
                assertEquals(
                    "$sourceLabel unavailable state must not claim static-matchable platform trips",
                    0,
                    evidenceCounts.getInt("globalStaticMatchablePlatformTripCount"),
                )
            }

            if (platformSample != null) {
                val sourceRealtime = sourceRoot.getJSONObject("realtime")
                val sourceHeader = GtfsRealtime.FeedHeader.parseFrom(
                    decode(sourceRealtime.getString("headerPbBase64")),
                )
                val sourceFeedTimestamp = sourceHeader.timestamp
                val entity = GtfsRealtime.FeedEntity.parseFrom(decode(platformSample.getString("entityPbBase64")))
                val builder = GtfsRealtime.FeedMessage.newBuilder().setHeader(sourceHeader).addEntity(entity)
                val parsed = GermanyGtfsRealtimeClient(
                    clock = EpochClock { sourceFeedTimestamp },
                    streamLoader = { builder.build().toByteArray().inputStream() },
                ).fetch()
                assertTrue("$sourceLabel platform evidence protobuf did not parse", parsed is GermanyRealtimeFetchResult.Available)

                val data = JsonStaticData(platformSample)
                val matcher = GermanyRealtimeMatcher(data, freshWindowSeconds = 120)
                val fresh = matcher.match(parsed, sourceFeedTimestamp)
                assertTrue("$sourceLabel platform evidence did not match static IDs", fresh is GermanyRealtimeOverlayResult.Matched)
                val matched = fresh as GermanyRealtimeOverlayResult.Matched
                val platformCount = validatePlatformEvidence(sourceLabel, platformSample, entity, data, matched)
                assertTrue("$sourceLabel platform evidence was not static-matchable", platformCount > 0)
                validPlatformEvidenceCount += platformCount
            }
        }

        assertTrue(
            "No protocol-valid, static-matchable provider platform reassignment in any configured source; platform acceptance is not proven",
            validPlatformEvidenceCount > 0,
        )

        if (root.getJSONArray("associatedAlerts").length() > 0) {
            assertTrue("provider-associated alerts did not survive matcher application", matchedAlertCount > 0)
        }
    }

    private fun validatePlatformEvidence(
        label: String,
        sample: JSONObject,
        entity: GtfsRealtime.FeedEntity,
        data: GermanyRealtimeStaticData,
        matched: GermanyRealtimeOverlayResult.Matched,
    ): Int {
        val summary = sample.getJSONObject("realtimeSummary")
        val staticStopTimes = sample.getJSONArray("staticStopTimes")
        val staticStopIdsBySequence = mutableMapOf<Int, MutableList<String>>()
        repeat(staticStopTimes.length()) { index ->
            val row = staticStopTimes.getJSONObject(index)
            val sequence = row.getString("stop_sequence").toInt()
            staticStopIdsBySequence.getOrPut(sequence) { mutableListOf() }.add(row.getString("stop_id"))
        }
        val providerSequenceCounts = entity.tripUpdate.stopTimeUpdateList
            .filter { it.hasStopSequence() }
            .groupingBy { it.stopSequence }
            .eachCount()

        val assigned = entity.tripUpdate.stopTimeUpdateList.filter { stop ->
            stop.hasStopTimeProperties() && stop.stopTimeProperties.assignedStopId.isNotBlank()
        }
        assertEquals("$label raw assigned-stop count drifted", summary.getInt("assigned_count"), assigned.size)
        val protocolValidAssigned = assigned.filter { stop ->
            stop.hasStopSequence() &&
                (stop.stopId.isBlank() || stop.stopId == stop.stopTimeProperties.assignedStopId)
        }
        assertEquals(
            "$label protocol-valid assigned-stop count drifted",
            summary.getInt("protocol_valid_assigned_count"),
            protocolValidAssigned.size,
        )
        val matchableAssigned = protocolValidAssigned.filter { stop ->
            if (providerSequenceCounts[stop.stopSequence] != 1) return@filter false
            val scheduledIds = staticStopIdsBySequence[stop.stopSequence].orEmpty()
            if (scheduledIds.size != 1) return@filter false
            val scheduledStopId = scheduledIds.single()
            val assignedStopId = stop.stopTimeProperties.assignedStopId
            if (assignedStopId == scheduledStopId) return@filter false
            val scheduledParent = data.stop(scheduledStopId)?.parentStationId
                ?.takeIf { it.isNotBlank() } ?: return@filter false
            val assignedParent = data.stop(assignedStopId)?.parentStationId
                ?.takeIf { it.isNotBlank() } ?: return@filter false
            scheduledParent == assignedParent
        }
        assertEquals(
            "$label static-matchable assigned-stop count drifted",
            summary.getInt("static_matchable_assigned_count"),
            matchableAssigned.size,
        )
        matchableAssigned.forEach { stop ->
            val assignedStopId = stop.stopTimeProperties.assignedStopId
            val matchedStop = matched.tripOverlays.single().stopUpdates.singleOrNull {
                it.stopSequence == stop.stopSequence && it.assignedStopId == assignedStopId
            }
            assertTrue(
                "$label valid assigned stop was not retained at sequence ${stop.stopSequence}",
                matchedStop != null,
            )
            assertEquals(data.stop(assignedStopId)?.platformCode, matchedStop?.platformCode)
        }

        val stopIdCandidates = entity.tripUpdate.stopTimeUpdateList.filter { stop ->
            stop.hasStopSequence() && stop.stopId.isNotBlank() &&
                !(stop.hasStopTimeProperties() && stop.stopTimeProperties.assignedStopId.isNotBlank())
        }
        assertEquals(
            "$label stop_id + stop_sequence candidate count drifted",
            summary.getInt("stop_id_reassignment_candidate_count"),
            stopIdCandidates.size,
        )
        val protocolValidStopId = stopIdCandidates.filter { stop ->
            if (providerSequenceCounts[stop.stopSequence] != 1) return@filter false
            val scheduledIds = staticStopIdsBySequence[stop.stopSequence].orEmpty()
            scheduledIds.size == 1 && scheduledIds.single() != stop.stopId
        }
        assertEquals(
            "$label protocol-valid stop_id reassignment count drifted",
            summary.getInt("protocol_valid_stop_id_reassignment_count"),
            protocolValidStopId.size,
        )
        val matchableStopId = protocolValidStopId.filter { stop ->
            val scheduledStopId = staticStopIdsBySequence[stop.stopSequence].orEmpty().single()
            val scheduledParent = data.stop(scheduledStopId)?.parentStationId
                ?.takeIf { it.isNotBlank() } ?: return@filter false
            val realtimeParent = data.stop(stop.stopId)?.parentStationId
                ?.takeIf { it.isNotBlank() } ?: return@filter false
            scheduledParent == realtimeParent
        }
        assertEquals(
            "$label static-matchable stop_id reassignment count drifted",
            summary.getInt("static_matchable_stop_id_reassignment_count"),
            matchableStopId.size,
        )
        matchableStopId.forEach { stop ->
            val matchedStop = matched.tripOverlays.single().stopUpdates.singleOrNull {
                it.stopSequence == stop.stopSequence &&
                    it.realtimeStopId == stop.stopId &&
                    it.assignedStopId == null
            }
            assertTrue(
                "$label valid stop_id reassignment was not retained at sequence ${stop.stopSequence}",
                matchedStop != null,
            )
            assertEquals(data.stop(stop.stopId)?.platformCode, matchedStop?.platformCode)
        }

        val total = matchableAssigned.size + matchableStopId.size
        assertEquals(
            "$label combined static-matchable platform evidence count drifted",
            summary.getInt("static_matchable_platform_evidence_count"),
            total,
        )
        return total
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
