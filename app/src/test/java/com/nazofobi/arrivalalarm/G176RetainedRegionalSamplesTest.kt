package com.nazofobi.arrivalalarm

import com.google.protobuf.CodedOutputStream
import com.google.transit.realtime.GtfsRealtime
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class G176RetainedRegionalSamplesTest {
    private val date = GtfsServiceDate(2026, 10, 5)
    private val data = RegionalStaticData(
        trips = mapOf(
            "t-hb-6-1200" to trip("t-hb-6-1200", "r-hb-6", "svc-hb"),
            "t-be-s5-1205" to trip("t-be-s5-1205", "r-be-s5", "svc-be"),
        ),
        routes = mapOf(
            "r-hb-6" to GtfsScheduleRoute("r-hb-6", "delfi", "6", "Flughafen", 3),
            "r-be-s5" to GtfsScheduleRoute("r-be-s5", "vbb", "S5", "Strausberg Nord", 2),
        ),
        stops = mapOf(
            "de:hb:hbf" to stop("de:hb:hbf", "1"),
            "de:hb:hbf:platform:7" to stop("de:hb:hbf:platform:7", "7"),
            "de:hb:airport" to stop("de:hb:airport", "2"),
            "de:be:hbf" to stop("de:be:hbf", "11"),
            "de:be:alex" to stop("de:be:alex", "3"),
        ),
        stopTimes = mapOf(
            "t-hb-6-1200" to listOf(
                st("t-hb-6-1200", 1, "de:hb:hbf", "12:00:00"),
                st("t-hb-6-1200", 2, "de:hb:airport", "12:20:00"),
            ),
            "t-be-s5-1205" to listOf(
                st("t-be-s5-1205", 1, "de:be:hbf", "12:05:00"),
                st("t-be-s5-1205", 2, "de:be:alex", "12:10:00"),
            ),
        ),
    )

    @Test
    fun retainedBremenVbnSampleMatchesStaticIdsAndCarriesCancellationDelayPlatformAndAlert() {
        val update = GermanyRealtimeTripUpdate(
            entityId = "retained-vbn-bremen",
            tripId = "t-hb-6-1200",
            routeId = "r-hb-6",
            startDate = "20261005",
            scheduleRelationship = "CANCELED",
            cancelled = true,
            stops = listOf(
                GermanyRealtimeStopUpdate(
                    stopSequence = 1,
                    stopId = "de:hb:hbf",
                    arrivalDelaySeconds = null,
                    departureDelaySeconds = 180,
                    arrivalTimeEpochSeconds = null,
                    departureTimeEpochSeconds = null,
                    assignedStopId = "de:hb:hbf:platform:7",
                ),
            ),
            directionId = null,
            selectorValidity = GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
            updateTimestampEpochSeconds = 1_000L,
        )
        val alert = GermanyRealtimeAlert(
            entityId = "retained-vbn-alert",
            cause = "TECHNICAL_PROBLEM",
            effect = "SIGNIFICANT_DELAYS",
            header = "VBN/Bremen retained sample",
            description = "Synthetic retained contract sample; not a claim about current agency state.",
            selectors = listOf(
                GermanyRealtimeAlertSelector(
                    agencyId = "delfi",
                    routeId = "r-hb-6",
                    routeType = 3,
                    directionId = null,
                    stopId = "de:hb:hbf",
                    trip = null,
                    validity = GermanyRealtimeAlertSelectorValidity.VALID,
                ),
            ),
            activePeriods = emptyList(),
            severityLevel = "WARNING",
        )

        val result = matcher().match(snapshot(1_000L, listOf(update), listOf(alert)), 1_010L)
        val matched = result as GermanyRealtimeOverlayResult.Matched
        assertEquals(GermanyRealtimeOverlayFreshnessState.FRESH, matched.freshness)
        assertEquals("t-hb-6-1200", matched.tripOverlays.single().staticTrip.id)
        assertTrue(matched.tripOverlays.single().cancelled)
        val stop = matched.tripOverlays.single().stopUpdates.single()
        assertEquals("de:hb:hbf", stop.scheduledStopId)
        assertEquals(180, stop.departureDelaySeconds)
        assertEquals("7", stop.platformCode)
        assertEquals(listOf("retained-vbn-alert"), matched.serviceAlerts.map { it.entityId })
    }

    @Test
    fun retainedBerlinSecondRegionSampleMatchesStaticTripRouteAndStopIds() {
        val update = GermanyRealtimeTripUpdate(
            entityId = "retained-vbb-berlin",
            tripId = "t-be-s5-1205",
            routeId = "r-be-s5",
            startDate = "20261005",
            scheduleRelationship = "SCHEDULED",
            cancelled = false,
            stops = listOf(
                GermanyRealtimeStopUpdate(
                    stopSequence = 2,
                    stopId = "de:be:alex",
                    arrivalDelaySeconds = 60,
                    departureDelaySeconds = null,
                    arrivalTimeEpochSeconds = null,
                    departureTimeEpochSeconds = null,
                ),
            ),
            selectorValidity = GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
            updateTimestampEpochSeconds = 2_000L,
        )

        val result = matcher().match(snapshot(2_000L, listOf(update)), 2_030L)
        val matched = result as GermanyRealtimeOverlayResult.Matched
        assertEquals(GermanyRealtimeOverlayFreshnessState.FRESH, matched.freshness)
        assertEquals("t-be-s5-1205", matched.tripOverlays.single().staticTrip.id)
        assertEquals("r-be-s5", matched.tripOverlays.single().staticTrip.routeId)
        assertEquals("de:be:alex", matched.tripOverlays.single().stopUpdates.single().scheduledStopId)
        assertEquals(60, matched.tripOverlays.single().stopUpdates.single().arrivalDelaySeconds)
    }

    @Test
    fun retainedMatrixKeepsFreshStaleNoMatchAndUnavailableDistinct() {
        val missing = GermanyRealtimeTripUpdate(
            entityId = "retained-missing",
            tripId = "missing-trip",
            routeId = null,
            startDate = "20261005",
            scheduleRelationship = "SCHEDULED",
            cancelled = false,
            stops = emptyList(),
            selectorValidity = GermanyRealtimeTripSelectorValidity.VALID_ID_BASED,
        )

        val freshNoMatch = matcher().match(snapshot(3_000L, listOf(missing)), 3_010L)
            as GermanyRealtimeOverlayResult.NoMatch
        assertEquals(GermanyRealtimeOverlayFreshnessState.FRESH, freshNoMatch.freshness)

        val staleNoMatch = matcher().match(snapshot(3_000L, listOf(missing)), 3_300L)
            as GermanyRealtimeOverlayResult.NoMatch
        assertEquals(GermanyRealtimeOverlayFreshnessState.STALE, staleNoMatch.freshness)
        assertEquals(300L, staleNoMatch.ageSeconds)

        val unavailable = matcher().match(
            GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.NETWORK,
                "retained-offline",
            ),
            3_010L,
        )
        assertTrue(unavailable is GermanyRealtimeOverlayResult.Unavailable)
        assertEquals(
            GermanyRealtimeUnavailableReason.NETWORK,
            (unavailable as GermanyRealtimeOverlayResult.Unavailable).reason,
        )
    }

    @Test
    fun incrementalParserHandlesLargeIgnoredEntityStreamWithoutFeedGraphMaterialization() {
        val header = GtfsRealtime.FeedHeader.newBuilder()
            .setGtfsRealtimeVersion("2.0")
            .setTimestamp(4_000L)
            .build()
        val ignoredEntity = GtfsRealtime.FeedEntity.newBuilder()
            .setId("ignored-" + "x".repeat(4_096))
            .build()
        val headerChunk = framedMessage(
            GtfsRealtime.FeedMessage.HEADER_FIELD_NUMBER,
            header.toByteArray(),
        )
        val entityChunk = framedMessage(
            GtfsRealtime.FeedMessage.ENTITY_FIELD_NUMBER,
            ignoredEntity.toByteArray(),
        )
        val repeatCount = 8_192
        val wireBytes = headerChunk.size.toLong() + entityChunk.size.toLong() * repeatCount
        assertTrue("synthetic payload must exceed 32 MiB", wireBytes > 32L * 1024L * 1024L)
        assertTrue(
            "synthetic payload must stay inside production input bound",
            wireBytes < GERMANY_REALTIME_DEFAULT_MAX_BYTES.toLong(),
        )

        val result = GermanyGtfsRealtimeClient(
            clock = EpochClock { 4_005L },
            streamLoader = {
                RepeatingFeedInputStream(
                    headerChunk = headerChunk,
                    entityChunk = entityChunk,
                    repeatCount = repeatCount,
                )
            },
        ).fetch()

        assertTrue(result is GermanyRealtimeFetchResult.Available)
        val snapshot = (result as GermanyRealtimeFetchResult.Available).snapshot
        assertEquals(4_000L, snapshot.feedTimestampEpochSeconds)
        assertEquals("2.0", snapshot.gtfsRealtimeVersion)
        assertTrue(snapshot.tripUpdates.isEmpty())
        assertTrue(snapshot.serviceAlerts.isEmpty())
    }

    private fun framedMessage(fieldNumber: Int, bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        CodedOutputStream.newInstance(output).also { coded ->
            coded.writeByteArray(fieldNumber, bytes)
            coded.flush()
        }
        return output.toByteArray()
    }

    private class RepeatingFeedInputStream(
        private val headerChunk: ByteArray,
        private val entityChunk: ByteArray,
        private val repeatCount: Int,
    ) : InputStream() {
        private var servingHeader = true
        private var entitiesServed = 0
        private var offset = 0

        override fun read(): Int {
            val chunk = currentChunk() ?: return -1
            val value = chunk[offset].toInt() and 0xff
            advance(1, chunk.size)
            return value
        }

        override fun read(buffer: ByteArray, offsetInBuffer: Int, length: Int): Int {
            require(offsetInBuffer >= 0 && length >= 0 && offsetInBuffer + length <= buffer.size)
            if (length == 0) return 0
            val chunk = currentChunk() ?: return -1
            val count = minOf(length, chunk.size - offset)
            System.arraycopy(chunk, offset, buffer, offsetInBuffer, count)
            advance(count, chunk.size)
            return count
        }

        private fun currentChunk(): ByteArray? = when {
            servingHeader -> headerChunk
            entitiesServed < repeatCount -> entityChunk
            else -> null
        }

        private fun advance(count: Int, chunkSize: Int) {
            offset += count
            if (offset == chunkSize) {
                offset = 0
                if (servingHeader) {
                    servingHeader = false
                } else {
                    entitiesServed += 1
                }
            }
        }
    }

    private fun matcher() = GermanyRealtimeMatcher(data, freshWindowSeconds = 120)

    private fun snapshot(
        feedTimestamp: Long,
        tripUpdates: List<GermanyRealtimeTripUpdate>,
        serviceAlerts: List<GermanyRealtimeAlert> = emptyList(),
    ) = GermanyRealtimeSnapshot(
        source = GermanyGtfsRealtimeProvenance.PROVIDER,
        license = GermanyGtfsRealtimeProvenance.LICENSE,
        feedTimestampEpochSeconds = feedTimestamp,
        fetchedAtEpochSeconds = feedTimestamp + 5,
        tripUpdates = tripUpdates,
        serviceAlerts = serviceAlerts,
        gtfsRealtimeVersion = "2.0",
        incrementality = "FULL_DATASET",
        feedVersion = "g176-retained-regional-v1",
    )

    private fun trip(id: String, routeId: String, serviceId: String) = GtfsScheduleTrip(
        id = id,
        routeId = routeId,
        serviceId = serviceId,
        headsign = null,
        directionId = null,
        shapeId = null,
        wheelchairAccessible = null,
        bikesAllowed = null,
    )

    private fun stop(id: String, platform: String) =
        GtfsScheduleStop(id, id, 52.0, 8.0, null, 0, platform, null)

    private fun st(tripId: String, sequence: Int, stopId: String, time: String) =
        GtfsScheduleStopTime(tripId, sequence, stopId, time, time, null, null, null, null)

    private data class RegionalStaticData(
        val trips: Map<String, GtfsScheduleTrip>,
        val routes: Map<String, GtfsScheduleRoute>,
        val stops: Map<String, GtfsScheduleStop>,
        val stopTimes: Map<String, List<GtfsScheduleStopTime>>,
    ) : GermanyRealtimeStaticData {
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
        override fun isServiceActive(serviceId: String, serviceDate: GtfsServiceDate) =
            serviceDate == GtfsServiceDate(2026, 10, 5)
    }
}
