package com.nazofobi.arrivalalarm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
