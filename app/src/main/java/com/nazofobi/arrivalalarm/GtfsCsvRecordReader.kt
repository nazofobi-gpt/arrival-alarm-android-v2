package com.nazofobi.arrivalalarm

import java.io.BufferedReader

private enum class GtfsCsvFieldState { START, UNQUOTED, QUOTED, AFTER_QUOTE }

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
        val line = reader.readLine() ?: break
        if (hasLine) {
            check(record.length < maxRecordChars) { "GTFS CSV record exceeds size limit" }
            record.append('\n')
        }
        check(line.length <= maxRecordChars - record.length) {
            "GTFS CSV record exceeds size limit"
        }
        record.append(line)
        for ((index, character) in line.withIndex()) {
            // The UTF-8 BOM belongs to the first header, not to its first field.
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
                    '"' -> GtfsCsvFieldState.QUOTED // Escaped pair inside a quoted field.
                    ',' -> GtfsCsvFieldState.START
                    ' ', '\t' -> GtfsCsvFieldState.AFTER_QUOTE
                    else -> error("GTFS CSV record has text after a closing quote")
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
