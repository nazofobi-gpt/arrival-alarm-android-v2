package com.nazofobi.arrivalalarm

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NationwideTransitDataTest {
    @Test fun csvParserHandlesQuotedCommaAndEscapedQuote() {
        val row = parseCsvLine("id,\"Berlin, Hauptbahnhof\",52.5,13.3,\"A \"\"quoted\"\" value\"")
        assertEquals("id", row[0])
        assertEquals("Berlin, Hauptbahnhof", row[1])
        assertEquals("A \"quoted\" value", row[4])
    }

    @Test fun journeyParserExposesLinesTransfersTimesAndWalking() {
        val json = JSONObject(
            """{"journeys":[{"legs":[
              {"departure":"2026-09-24T08:00:00+02:00","arrival":"2026-09-24T08:05:00+02:00","walking":true},
              {"departure":"2026-09-24T08:08:00+02:00","arrival":"2026-09-24T08:30:00+02:00","direction":"Bremen Hbf","line":{"name":"RE 1"},"tripId":"t1"},
              {"departure":"2026-09-24T08:35:00+02:00","arrival":"2026-09-24T08:50:00+02:00","direction":"Hamburg Hbf","line":{"name":"ICE 618"},"tripId":"t2"}
            ]}]}"""
        )
        val origin = MapPoint(52.66, 8.23, "Lohne")
        val destination = MapPoint(53.55, 9.99, "Hamburg")
        val option = GermanyLiveTransitApi().parseJourneys(json, origin, destination, 3).single()
        assertEquals("RE 1 → ICE 618", option.line)
        assertEquals("08:00", option.departure)
        assertEquals("08:50", option.arrival)
        assertEquals(5, option.walkingMinutes)
        assertEquals(1, option.transfers)
    }


    @Test fun journeyParserCarriesRealStopoversTripIdsAndFreshness() {
        val json = JSONObject(
            """{"journeys":[{"refreshToken":"refresh-1","legs":[
              {"departure":"2026-09-26T08:00:00+02:00","plannedDeparture":"2026-09-26T07:58:00+02:00",
               "arrival":"2026-09-26T08:45:00+02:00","plannedArrival":"2026-09-26T08:43:00+02:00",
               "direction":"Bremen Hbf","line":{"name":"RE 9"},"tripId":"trip-re9",
               "stopovers":[
                 {"stop":{"id":"8000235","name":"Lohne(Oldb)","location":{"latitude":52.665,"longitude":8.237}},
                  "departure":"2026-09-26T08:00:00+02:00","plannedDeparture":"2026-09-26T07:58:00+02:00"},
                 {"stop":{"id":"8000128","name":"Diepholz","location":{"latitude":52.607,"longitude":8.371}},
                  "arrival":"2026-09-26T08:15:00+02:00","departure":"2026-09-26T08:16:00+02:00",
                  "plannedArrival":"2026-09-26T08:13:00+02:00","plannedDeparture":"2026-09-26T08:14:00+02:00"},
                 {"stop":{"id":"8000050","name":"Bremen Hbf","location":{"latitude":53.083,"longitude":8.813}},
                  "arrival":"2026-09-26T08:45:00+02:00","plannedArrival":"2026-09-26T08:43:00+02:00"}
               ]}
            ]}]}"""
        )
        val option = GermanyLiveTransitApi(nowEpochSeconds = { 123456L }).parseJourneys(
            json,
            MapPoint(52.665, 8.237, "Lohne"),
            MapPoint(53.083, 8.813, "Bremen Hbf"),
            1,
        ).single()

        assertEquals(listOf("trip-re9"), option.tripIds)
        assertEquals("refresh-1", option.refreshToken)
        assertEquals(listOf("Lohne(Oldb)", "Diepholz", "Bremen Hbf"), option.stops.map { it.name })
        assertEquals("2026-09-26T08:16:00+02:00", option.stops[1].departure)
        assertEquals(123456L, option.sourceUpdatedAtEpochSeconds)
        assertTrue(option.stops[1].latitude != null)
    }

    @Test fun liveLocationParserKeepsStopsAddressesAndPois() {
        val json = JSONArray(
            """[
              {"type":"stop","id":"8000105","name":"Frankfurt(Main)Hbf",
               "location":{"latitude":50.1071,"longitude":8.6638}},
              {"type":"location","name":"Messe Frankfurt","poi":true,
               "location":{"latitude":50.1122,"longitude":8.6420}},
              {"type":"location","address":"Taunusanlage 12, Frankfurt",
               "latitude":50.1130,"longitude":8.6690}
            ]"""
        )

        val values = GermanyLiveTransitApi().parseLocations(json, 10)

        assertEquals(
            listOf(TransitLocationKind.STOP, TransitLocationKind.POI, TransitLocationKind.ADDRESS),
            values.map { it.kind },
        )
        assertEquals("db:8000105", values.first().stop?.id)
        assertEquals("Messe Frankfurt", values[1].label)
        assertEquals("Taunusanlage 12, Frankfurt", values[2].label)
    }

    @Test fun liveStopParserUsesActualProviderIdsAndCoordinates() {
        val json = JSONArray(
            """[
              {"type":"stop","id":"8000105","name":"Frankfurt(Main)Hbf",
               "location":{"type":"location","latitude":50.1071,"longitude":8.6638}},
              {"type":"address","id":"x","name":"not a stop",
               "location":{"latitude":50.0,"longitude":8.0}}
            ]"""
        )
        val values = GermanyLiveTransitApi().parseStops(json, 10)
        assertEquals(1, values.size)
        assertEquals("db:8000105", values.single().id)
        assertEquals("Frankfurt(Main)Hbf", values.single().name)
        assertTrue(values.single().latitude > 50.0)
    }
    @Test fun httpCacheFreshHitAvoidsSecondRequest() {
        var nowSeconds = 1_000L
        val exchange = RecordingExchange(
            httpResponse(200, "A", "Cache-Control" to "max-age=60", "ETag" to "\"v1\""),
        )
        val api = cacheApi(exchange, { nowSeconds })
        val url = "https://example.test/a"

        assertOutcomeBody("A", api.fetchJsonOutcome(url))
        nowSeconds += 10
        assertOutcomeBody("A", api.fetchJsonOutcome(url))
        assertEquals(1, exchange.requests.size)
    }

    @Test fun expiredEtagCacheRevalidatesWith304AndRefreshesFreshness() {
        var nowSeconds = 2_000L
        val exchange = RecordingExchange(
            httpResponse(200, "A", "Cache-Control" to "max-age=1", "ETag" to "\"v1\""),
            httpResponse(304, "", "Cache-Control" to "max-age=30", "ETag" to "\"v1\""),
        )
        val api = cacheApi(exchange, { nowSeconds })
        val url = "https://example.test/revalidate"

        assertOutcomeBody("A", api.fetchJsonOutcome(url))
        nowSeconds += 2
        val refreshed = api.fetchJsonOutcome(url)
        assertOutcomeBody("A", refreshed)
        assertEquals("\"v1\"", exchange.requests[1].headers["If-None-Match"])
        when (refreshed) {
            is TransitProviderOutcome.Results ->
                assertEquals(TransitProviderFreshnessState.FRESH, refreshed.freshness.state)
            else -> fail("Expected fresh Results but was " + refreshed)
        }

        nowSeconds += 5
        assertOutcomeBody("A", api.fetchJsonOutcome(url))
        assertEquals(2, exchange.requests.size)
    }

    @Test fun expiredCacheAcceptsNew200Representation() {
        var nowSeconds = 3_000L
        val exchange = RecordingExchange(
            httpResponse(200, "A", "Cache-Control" to "max-age=1", "ETag" to "\"v1\""),
            httpResponse(200, "B", "Cache-Control" to "max-age=60", "ETag" to "\"v2\""),
        )
        val api = cacheApi(exchange, { nowSeconds })
        val url = "https://example.test/new"

        assertOutcomeBody("A", api.fetchJsonOutcome(url))
        nowSeconds += 2
        assertOutcomeBody("B", api.fetchJsonOutcome(url))
        assertEquals("\"v1\"", exchange.requests[1].headers["If-None-Match"])
    }

    @Test fun noStoreIsNeverCachedAndNoCacheAlwaysRevalidates() {
        var nowSeconds = 4_000L
        val noStoreExchange = RecordingExchange(
            httpResponse(200, "A", "Cache-Control" to "no-store, max-age=60"),
            httpResponse(200, "B", "Cache-Control" to "no-store, max-age=60"),
        )
        val noStoreApi = cacheApi(noStoreExchange, { nowSeconds })
        val noStoreUrl = "https://example.test/no-store"
        assertOutcomeBody("A", noStoreApi.fetchJsonOutcome(noStoreUrl))
        assertOutcomeBody("B", noStoreApi.fetchJsonOutcome(noStoreUrl))
        assertEquals(2, noStoreExchange.requests.size)

        val noCacheExchange = RecordingExchange(
            httpResponse(200, "C", "Cache-Control" to "no-cache, max-age=60", "ETag" to "\"c1\""),
            httpResponse(304, "", "Cache-Control" to "no-cache, max-age=60"),
            httpResponse(304, "", "Cache-Control" to "no-cache, max-age=60"),
        )
        val noCacheApi = cacheApi(noCacheExchange, { nowSeconds })
        val noCacheUrl = "https://example.test/no-cache"
        assertOutcomeBody("C", noCacheApi.fetchJsonOutcome(noCacheUrl))
        assertOutcomeBody("C", noCacheApi.fetchJsonOutcome(noCacheUrl))
        assertOutcomeBody("C", noCacheApi.fetchJsonOutcome(noCacheUrl))
        assertEquals(3, noCacheExchange.requests.size)
        assertEquals("\"c1\"", noCacheExchange.requests[1].headers["If-None-Match"])
    }

    @Test fun invalidOrConflictingMaxAgeIsConservativelyRevalidated() {
        var nowSeconds = 5_000L
        val exchange = RecordingExchange(
            HttpTransportResponse(
                status = 200,
                headers = mapOf(
                    "Cache-Control" to listOf("max-age=60", "max-age=30"),
                    "ETag" to listOf("\"x\""),
                ),
                body = "A",
            ),
            httpResponse(304, "", "Cache-Control" to "max-age=10"),
        )
        val api = cacheApi(exchange, { nowSeconds })
        val url = "https://example.test/conflict"

        assertOutcomeBody("A", api.fetchJsonOutcome(url))
        assertOutcomeBody("A", api.fetchJsonOutcome(url))
        assertEquals(2, exchange.requests.size)
    }

    @Test fun rateLimitAndServerFailureAreNotStoredAsContent() {
        var nowSeconds = 6_000L
        val rateExchange = RecordingExchange(
            httpResponse(429, "rate-limited"),
            httpResponse(200, "ok", "Cache-Control" to "max-age=60"),
        )
        val rateApi = cacheApi(rateExchange, { nowSeconds })
        val rateUrl = "https://example.test/rate"
        val firstRate = rateApi.fetchJsonOutcome(rateUrl)
        assertTrue(firstRate is TransitProviderOutcome.Unavailable)
        assertEquals(
            TransitProviderUnavailableReason.RATE_LIMITED,
            (firstRate as TransitProviderOutcome.Unavailable).reason,
        )
        assertOutcomeBody("ok", rateApi.fetchJsonOutcome(rateUrl))
        assertEquals(2, rateExchange.requests.size)

        val serverExchange = RecordingExchange(
            httpResponse(503, "down"),
            httpResponse(200, "recovered", "Cache-Control" to "max-age=60"),
        )
        val serverApi = cacheApi(serverExchange, { nowSeconds })
        val serverUrl = "https://example.test/server"
        val firstServer = serverApi.fetchJsonOutcome(serverUrl)
        assertTrue(firstServer is TransitProviderOutcome.Unavailable)
        assertEquals(
            TransitProviderUnavailableReason.UPSTREAM_ERROR,
            (firstServer as TransitProviderOutcome.Unavailable).reason,
        )
        assertOutcomeBody("recovered", serverApi.fetchJsonOutcome(serverUrl))
        assertEquals(2, serverExchange.requests.size)
    }

    @Test fun expiredCacheBecomesTypedStaleOnNetworkFailure() {
        var nowSeconds = 7_000L
        val exchange = RecordingExchange(
            httpResponse(200, "cached", "Cache-Control" to "max-age=1", "ETag" to "\"v1\""),
            IOException("offline"),
        )
        val api = cacheApi(exchange, { nowSeconds })
        val url = "https://example.test/offline"

        assertOutcomeBody("cached", api.fetchJsonOutcome(url))
        nowSeconds += 2
        val stale = api.fetchJsonOutcome(url)
        when (stale) {
            is TransitProviderOutcome.Stale -> {
                assertEquals("cached", stale.value)
                assertEquals(TransitProviderFreshnessState.STALE, stale.freshness.state)
                assertEquals(2L, stale.freshness.ageSeconds)
            }
            else -> fail("Expected typed Stale but was " + stale)
        }
    }

    @Test fun cacheCapacityEvictsLeastRecentlyUsedEntry() {
        var nowSeconds = 8_000L
        val exchange = RecordingExchange(
            httpResponse(200, "A", "Cache-Control" to "max-age=60"),
            httpResponse(200, "B", "Cache-Control" to "max-age=60"),
            httpResponse(200, "C", "Cache-Control" to "max-age=60"),
            httpResponse(200, "A2", "Cache-Control" to "max-age=60"),
        )
        val api = cacheApi(exchange, { nowSeconds }, capacity = 2)

        assertOutcomeBody("A", api.fetchJsonOutcome("https://example.test/a"))
        assertOutcomeBody("B", api.fetchJsonOutcome("https://example.test/b"))
        assertOutcomeBody("C", api.fetchJsonOutcome("https://example.test/c"))
        assertOutcomeBody("A2", api.fetchJsonOutcome("https://example.test/a"))
        assertEquals(4, exchange.requests.size)
    }

    @Test fun typedStopSearchDistinguishesResultsNoResultAndInvalidResponse() {
        val resultApi = cacheApi(
            RecordingExchange(
                httpResponse(
                    200,
                    """[{"type":"stop","id":"8000105","name":"Frankfurt(Main)Hbf","location":{"latitude":50.1071,"longitude":8.6638}}]""",
                    "Cache-Control" to "max-age=60",
                ),
            ),
            { 9_000L },
        )
        val results = resultApi.searchStopsOutcome("Frankfurt", 5)
        assertTrue(results is TransitProviderOutcome.Results)
        assertEquals(
            listOf("db:8000105"),
            (results as TransitProviderOutcome.Results).value.map { it.id },
        )

        val emptyApi = cacheApi(
            RecordingExchange(
                httpResponse(200, "[]", "Cache-Control" to "max-age=60"),
            ),
            { 9_100L },
        )
        assertTrue(
            emptyApi.searchStopsOutcome("Nowhere", 5) is TransitProviderOutcome.NoResult,
        )

        val invalidApi = cacheApi(
            RecordingExchange(
                httpResponse(200, "{not-json", "Cache-Control" to "max-age=60"),
            ),
            { 9_200L },
        )
        val invalid = invalidApi.searchStopsOutcome("Broken", 5)
        assertTrue(invalid is TransitProviderOutcome.Unavailable)
        assertEquals(
            TransitProviderUnavailableReason.INVALID_RESPONSE,
            (invalid as TransitProviderOutcome.Unavailable).reason,
        )
    }

    @Test fun typedStopSearchRetainsParsedStaleCacheAndProviderFailure() {
        var nowSeconds = 10_000L
        val staleApi = cacheApi(
            RecordingExchange(
                httpResponse(
                    200,
                    """[{"type":"stop","id":"8000105","name":"Frankfurt(Main)Hbf","location":{"latitude":50.1071,"longitude":8.6638}}]""",
                    "Cache-Control" to "max-age=1",
                    "ETag" to "\\\"v1\\\"",
                ),
                IOException("offline"),
            ),
            { nowSeconds },
        )
        val fresh = staleApi.searchStopsOutcome("Frankfurt", 5)
        assertTrue(fresh is TransitProviderOutcome.Results)
        nowSeconds += 2
        val stale = staleApi.searchStopsOutcome("Frankfurt", 5)
        assertTrue(stale is TransitProviderOutcome.Stale)
        assertEquals(
            listOf("db:8000105"),
            (stale as TransitProviderOutcome.Stale).value.map { it.id },
        )
        assertEquals(2L, stale.freshness.ageSeconds)

        val unavailableApi = cacheApi(
            RecordingExchange(httpResponse(503, "down")),
            { 10_100L },
        )
        val unavailable = unavailableApi.searchStopsOutcome("Frankfurt", 5)
        assertTrue(unavailable is TransitProviderOutcome.Unavailable)
        assertEquals(
            TransitProviderUnavailableReason.UPSTREAM_ERROR,
            (unavailable as TransitProviderOutcome.Unavailable).reason,
        )
    }

    @Test fun stationDepartureParserPreservesRealtimePlatformCancellationAndRemarks() {
        val json = JSONArray(
            """[
              {"tripId":"trip-1","direction":"Bremen Hbf","line":{"name":"RE 9"},
               "when":"2026-09-26T18:06:00+02:00","plannedWhen":"2026-09-26T18:02:00+02:00",
               "platform":"2","plannedPlatform":"1","cancelled":false,
               "remarks":[{"code":"delay","text":"Verspätung wegen vorausfahrender Fahrt"}]},
              {"tripId":"trip-2","direction":"Osnabrück Hbf","line":{"name":"RE 18"},
               "when":"2026-09-26T18:10:00+02:00","plannedWhen":"2026-09-26T18:10:00+02:00",
               "plannedPlatform":"3","cancelled":true,"remarks":[]}
            ]"""
        )

        val snapshot = GermanyLiveTransitApi(nowEpochSeconds = { 999L })
            .parseDepartures(json, 8)

        assertEquals(2, snapshot.departures.size)
        assertTrue(snapshot.departures[0].isRealtime)
        assertEquals("2", snapshot.departures[0].platform)
        assertTrue(snapshot.departures[1].cancelled)
        assertEquals(1, snapshot.alerts.size)
        assertEquals("v6.db.transport.rest", snapshot.sourceLabel)
        assertEquals(999L, snapshot.fetchedAtEpochSeconds)
    }

    private fun cacheApi(
        exchange: RecordingExchange,
        nowEpochSeconds: () -> Long,
        capacity: Int = 64,
    ): GermanyLiveTransitApi {
        val transport = BoundedHttpTransport(
            exchange = exchange,
            clock = HttpTransportClock { nowEpochSeconds() * 1_000L },
            sleeper = HttpTransportSleeper { },
            maxRetries = 0,
        )
        return GermanyLiveTransitApi(
            nowEpochSeconds = nowEpochSeconds,
            transport = transport,
            cacheCapacity = capacity,
        )
    }

    private fun httpResponse(
        status: Int,
        body: String,
        vararg headers: Pair<String, String>,
    ): HttpTransportResponse = HttpTransportResponse(
        status = status,
        headers = headers.groupBy({ it.first }, { it.second }),
        body = body,
    )

    private fun assertOutcomeBody(expected: String, outcome: TransitProviderOutcome<String>) {
        when (outcome) {
            is TransitProviderOutcome.Results -> {
                assertEquals(expected, outcome.value)
                assertEquals(TransitProviderFreshnessState.FRESH, outcome.freshness.state)
            }
            else -> fail("Expected fresh Results but was " + outcome)
        }
    }

    private class RecordingExchange(vararg initialOutcomes: Any) : HttpExchange {
        private val outcomes = ArrayDeque<Any>().apply {
            initialOutcomes.forEach { addLast(it) }
        }
        val requests = mutableListOf<HttpTransportRequest>()

        override fun execute(
            request: HttpTransportRequest,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
        ): HttpTransportResponse {
            requests += request
            val next = outcomes.removeFirst()
            if (next is IOException) throw next
            return next as HttpTransportResponse
        }
    }

}
