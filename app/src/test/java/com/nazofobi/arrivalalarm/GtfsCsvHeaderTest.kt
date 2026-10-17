package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class GtfsCsvHeaderTest {
    @Test
    fun utf8BomAtStartOfHeaderDoesNotCorruptFirstColumnName() {
        assertEquals(
            listOf("stop_id", "stop_name", "stop_lat"),
            parseGtfsCsvHeader("\uFEFFstop_id,stop_name,stop_lat"),
        )
    }

    @Test
    fun quotedHeaderAfterBomIsParsedNormally() {
        assertEquals(
            listOf("stop_id", "stop_name"),
            parseGtfsCsvHeader("\uFEFF\"stop_id\",stop_name"),
        )
    }

    @Test
    fun bomHandlingDoesNotChangeOrdinaryHeadersOrEmbeddedCharacters() {
        assertEquals(
            listOf("route_id", "route_short_name"),
            parseGtfsCsvHeader("route_id,route_short_name"),
        )
        assertEquals(
            listOf("route_id", "\uFEFFroute_short_name"),
            parseGtfsCsvHeader("route_id,\uFEFFroute_short_name"),
        )
    }
}
