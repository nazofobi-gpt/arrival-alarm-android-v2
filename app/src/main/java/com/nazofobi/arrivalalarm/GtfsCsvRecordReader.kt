package com.nazofobi.arrivalalarm

import java.io.BufferedReader

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
        val line = reader.readLine() ?: break
        if (hasLine) {
            check(record.length < maxRecordChars) { "GTFS CSV record exceeds size limit" }
            record.append('\n')
        }
        check(line.length <= maxRecordChars - record.length) {
            "GTFS CSV record exceeds size limit"
        }
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
