package com.nazofobi.arrivalalarm

import java.io.BufferedReader

private enum class GtfsCsvFieldState { START, UNQUOTED, QUOTED, AFTER_QUOTE, AFTER_QUOTE_WS }

/**
 * Reads a physical CSV line without first allocating an unbounded readLine() result.
 * Delimiter semantics match BufferedReader.readLine(): LF, CR, and CRLF.
 */
private fun readBoundedGtfsPhysicalLine(reader: BufferedReader, maxChars: Int): String? {
    val line = StringBuilder()
    while (true) {
        when (val next = reader.read()) {
            -1 -> return if (line.isEmpty()) null else line.toString()
            '\n'.code -> return line.toString()
            '\r'.code -> {
                reader.mark(1)
                if (reader.read() != '\n'.code) reader.reset()
                return line.toString()
            }
            else -> {
                check(line.length < maxChars) { "GTFS CSV record exceeds size limit" }
                line.append(next.toChar())
            }
        }
    }
}

/** Yields complete GTFS CSV records, including quoted newlines, without loading the feed. */
internal fun gtfsCsvRecords(
    reader: BufferedReader,
    maxRecordChars: Int = 1_048_576,
): Sequence<String> = sequence {
    require(maxRecordChars > 0)
    val record = StringBuilder()
    var fieldState = GtfsCsvFieldState.START
    var hasLine = false
    var firstRecord = true
    while (true) {
        val remainingChars = maxRecordChars - record.length - if (hasLine) 1 else 0
        check(remainingChars >= 0) { "GTFS CSV record exceeds size limit" }
        val line = readBoundedGtfsPhysicalLine(reader, remainingChars) ?: break
        if (hasLine) record.append('\n')
        record.append(line)
        for ((index, character) in line.withIndex()) {
            if (firstRecord && !hasLine && index == 0 && character == '\uFEFF') continue
            fieldState = when (fieldState) {
                GtfsCsvFieldState.START -> when (character) {
                    ',' -> GtfsCsvFieldState.START
                    '"' -> GtfsCsvFieldState.QUOTED
                    else -> GtfsCsvFieldState.UNQUOTED
                }
                GtfsCsvFieldState.UNQUOTED -> when (character) {
                    ',' -> GtfsCsvFieldState.START
                    '"' -> error("GTFS CSV record has a quote in an unquoted field")
                    else -> GtfsCsvFieldState.UNQUOTED
                }
                GtfsCsvFieldState.QUOTED -> when (character) {
                    '"' -> GtfsCsvFieldState.AFTER_QUOTE
                    else -> GtfsCsvFieldState.QUOTED
                }
                GtfsCsvFieldState.AFTER_QUOTE -> when (character) {
                    '"' -> GtfsCsvFieldState.QUOTED
                    ',' -> GtfsCsvFieldState.START
                    ' ', '\t' -> GtfsCsvFieldState.AFTER_QUOTE_WS
                    else -> error("GTFS CSV record has text after a closing quote")
                }
                GtfsCsvFieldState.AFTER_QUOTE_WS -> when (character) {
                    ',' -> GtfsCsvFieldState.START
                    ' ', '\t' -> GtfsCsvFieldState.AFTER_QUOTE_WS
                    else -> error("GTFS CSV record has text after whitespace following a closing quote")
                }
            }
        }
        hasLine = true
        if (fieldState != GtfsCsvFieldState.QUOTED) {
            yield(record.toString())
            record.setLength(0)
            hasLine = false
            firstRecord = false
            fieldState = GtfsCsvFieldState.START
        }
    }
    check(fieldState != GtfsCsvFieldState.QUOTED) {
        "GTFS CSV record has an unterminated quoted field"
    }
    if (hasLine) yield(record.toString())
}
