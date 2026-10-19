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

    @Test fun blankLineAndBomHeaderRemainDistinctRecords() {
        val csv = records("\uFEFFstop_id,stop_name\n\n1,Achim")
        assertEquals(listOf("stop_id", "stop_name"), parseGtfsCsvHeader(csv[0]))
        assertEquals("", csv[1])
        assertEquals(listOf("1", "Achim"), parseCsvLine(csv[2]))
    }
}
