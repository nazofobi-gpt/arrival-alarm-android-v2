package com.nazofobi.arrivalalarm

import java.io.ByteArrayInputStream
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GtfsStrictUtf8ReaderTest {
    @Test
    fun validMultibyteStopNamesArePreserved() {
        val csv = "stop_id,stop_name\n1,München Hauptbahnhof\n2,Köln\n"
        strictGtfsUtf8Reader(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))).use { reader ->
            assertEquals("stop_id,stop_name", reader.readLine())
            assertEquals("1,München Hauptbahnhof", reader.readLine())
            assertEquals("2,Köln", reader.readLine())
            assertEquals(null, reader.readLine())
        }
    }

    @Test
    fun invalidUtf8ContinuationFailsClosed() {
        val prefix = "stop_id,stop_name\n1,".toByteArray(StandardCharsets.UTF_8)
        assertMalformed(prefix + byteArrayOf(0xC3.toByte(), 0x28, 0x0A))
    }

    @Test
    fun truncatedUtf8AtEndOfTableFailsClosed() {
        val prefix = "stop_id,stop_name\n1,".toByteArray(StandardCharsets.UTF_8)
        assertMalformed(prefix + byteArrayOf(0xE2.toByte(), 0x82.toByte()))
    }

    private fun assertMalformed(bytes: ByteArray) {
        try {
            strictGtfsUtf8Reader(ByteArrayInputStream(bytes)).use { reader ->
                while (reader.readLine() != null) { /* drain the GTFS table */ }
            }
            fail("Malformed GTFS UTF-8 must not be silently replaced")
        } catch (_: MalformedInputException) {
            // The enclosing import transaction must roll back on this read failure.
        }
    }
}
