package com.nazofobi.arrivalalarm

import java.io.BufferedReader
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsCsvRecordReaderTest {
    private fun records(csv: String, cap: Int = 1_048_576): List<String> =
        gtfsCsvRecords(BufferedReader(StringReader(csv)), cap).toList()

    @Test fun multilineStopNameAndFollowingRecordArePreserved() {
        val csv = records("stop_id,stop_name\n1,\"Bremen\nHbf\"\n2,Achim")
        assertEquals(3, csv.size)
        assertEquals(listOf("stop_id", "stop_name"), parseGtfsCsvHeader(csv[0]))
        assertEquals(listOf("1", "Bremen\nHbf"), parseCsvLine(csv[1]))
        assertEquals(listOf("2", "Achim"), parseCsvLine(csv[2]))
    }

    @Test fun escapedQuotesWithinMultilineFieldArePreserved() {
        val csv = records("1,\"A\"\"B\nC\"\n2,D")
        assertEquals(listOf("1", "A\"B\nC"), parseCsvLine(csv[0]))
        assertEquals(listOf("2", "D"), parseCsvLine(csv[1]))
    }

    @Test fun crlfInsideQuotedFieldsIsNormalizedToNewline() {
        assertEquals(
            listOf("id,name", "1,\"A\nB\"", "2,C"),
            records("id,name\r\n1,\"A\r\nB\"\r\n2,C\r\n"),
        )
    }

    @Test fun recordAtLengthLimitIsAccepted() {
        assertEquals(listOf("ab", "cd"), records("ab\ncd", 2))
    }

    @Test fun oversizedLogicalRecordIsRejected() {
        assertTrue(runCatching { records("1,\"long\nvalue\"", 8) }.isFailure)
        assertTrue(runCatching { records("abc", 2) }.isFailure)
    }

    @Test fun unterminatedQuotedFieldFailsClosed() {
        val failure = runCatching { records("1,\"unfinished\ncontinued") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("unterminated") == true)
    }

    @Test fun nulCharacterInHeaderOrQuotedAndUnquotedDataIsRejected() {
        for (csv in listOf(
            "i\u0000d,name\n1,Achim",
            "id,name\n1,Bre\u0000men",
            "id,name\n1,\"Bre\u0000men\"",
        )) {
            val failure = runCatching { records(csv) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("NUL") == true)
        }
    }

    @Test fun physicalLineOverLimitFailsBeforeReadingEntireUntrustedLine() {
        val counting = object : java.io.Reader() {
            private val input = StringReader("X".repeat(100_000))
            var charsRead = 0
            override fun read(target: CharArray, offset: Int, length: Int): Int {
                val count = input.read(target, offset, length)
                if (count > 0) charsRead += count
                return count
            }
            override fun close() = input.close()
        }
        val failure = runCatching {
            gtfsCsvRecords(BufferedReader(counting), maxRecordChars = 64).toList()
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("size limit") == true)
        assertTrue("Consumed entire overlong line before rejecting", counting.charsRead < 100_000)
    }

    @Test fun bareCrCrLfAndLfPreservePhysicalRecordBoundaries() {
        assertEquals(listOf("a", "b", "c", "d"), records("a\rb\r\nc\nd"))
        assertEquals(listOf("", "", ""), records("\n\r\r\n"))
    }

    @Test fun blankLineAndBomHeaderRemainDistinctRecords() {
        val csv = records("\uFEFFstop_id,stop_name\n\n1,Achim")
        assertEquals(listOf("stop_id", "stop_name"), parseGtfsCsvHeader(csv[0]))
        assertEquals("", csv[1])
        assertEquals(listOf("1", "Achim"), parseCsvLine(csv[2]))
    }

    @Test fun malformedQuoteInUnquotedFieldIsRejectedBeforeNextRecord() {
        val failure = runCatching { records("1,Bad\"name\n2,Achim") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("unquoted") == true)
    }

    @Test fun nonWhitespaceAfterClosingQuoteIsRejected() {
        val failure = runCatching { records("1,\"Bremen\"junk\n2,Achim") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("closing quote") == true)
    }

    @Test fun escapedQuotesAndOptionalWhitespaceStillEndAtCorrectRecord() {
        assertEquals(
            listOf("1,\"Bremen\"\" Hbf\" ,DE", "2,Achim,DE"),
            records("1,\"Bremen\"\" Hbf\" ,DE\n2,Achim,DE"),
        )
    }

    @Test fun utf8BomBeforeQuotedFirstHeaderIsAccepted() {
        assertEquals(
            listOf("\uFEFF\"stop_id\",stop_name", "1,Achim"),
            records("\uFEFF\"stop_id\",stop_name\n1,Achim"),
        )
    }
}
