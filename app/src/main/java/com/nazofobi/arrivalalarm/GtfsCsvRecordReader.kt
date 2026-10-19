package com.nazofobi.arrivalalarm

import java.io.BufferedReader

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
    var inQuotedField = false
    var hasLine = false
    while (true) {
        val remainingChars = maxRecordChars - record.length - if (hasLine) 1 else 0
        check(remainingChars >= 0) { "GTFS CSV record exceeds size limit" }
        val line = readBoundedGtfsPhysicalLine(reader, remainingChars) ?: break
        if (hasLine) record.append('\n')
        record.append(line)
        // Escaped double quotes occur in pairs; toggling twice preserves quote state.
        for (character in line) if (character == '"') inQuotedField = !inQuotedField
        hasLine = true
        if (!inQuotedField) {
            yield(record.toString())
            record.setLength(0)
            hasLine = false
        }
    }
    check(!inQuotedField) { "GTFS CSV record has an unterminated quoted field" }
    if (hasLine) yield(record.toString())
}
