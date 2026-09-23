package com.nazofobi.arrivalalarm

import org.json.JSONArray
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
