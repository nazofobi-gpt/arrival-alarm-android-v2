package com.nazofobi.arrivalalarm

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import java.io.File

/**
 * On-disk size of the static offline index, including SQLite's active WAL/SHM files.
 * The downloaded ZIP is streamed into this index and is not retained as a second copy.
 */
internal fun offlineIndexStorageBytes(context: Context): Long {
    val database = context.getDatabasePath("nationwide_transit.db")
    return listOf(
        database,
        File("${database.path}-wal"),
        File("${database.path}-shm"),
    ).filter { it.isFile }
        .sumOf { it.length().coerceAtLeast(0L) }
}

/** Human-readable offline-pack metadata for the Settings readiness surface. */
internal fun offlinePackMetadataText(
    context: Context,
    state: NationwideDataState.Ready,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val bytes = offlineIndexStorageBytes(context)
    val size = if (bytes > 0L) {
        Formatter.formatShortFileSize(context, bytes)
    } else {
        context.getString(R.string.offline_pack_size_unknown)
    }

    val rawVersion = state.sourceVersion.trim().trim('"')
    // Provider-controlled feed tags must not inject line breaks, bidi overrides or
    // invisible formatting into the Settings metadata label. Fail closed.
    val unsafeVersion = rawVersion.any { char ->
        val category = Character.getType(char)
        Character.isISOControl(char) ||
            category == Character.FORMAT.toInt() ||
            category == Character.LINE_SEPARATOR.toInt() ||
            category == Character.PARAGRAPH_SEPARATOR.toInt()
    }
    // Kotlin String indexes UTF-16 code units. Reject incomplete surrogate pairs
    // before display and avoid truncating a valid pair into an invalid glyph.
    val malformedSurrogate = rawVersion.indices.any { index ->
        val char = rawVersion[index]
        (Character.isHighSurrogate(char) &&
            (index + 1 == rawVersion.length || !Character.isLowSurrogate(rawVersion[index + 1]))) ||
            (Character.isLowSurrogate(char) &&
                (index == 0 || !Character.isHighSurrogate(rawVersion[index - 1])))
    }
    val version = when {
        rawVersion.isBlank() || unsafeVersion || malformedSurrogate ||
            rawVersion.equals("unknown", ignoreCase = true) ->
            context.getString(R.string.offline_pack_version_unknown)
        rawVersion.length > 28 -> {
            val cutoff = if (Character.isHighSurrogate(rawVersion[26])) 26 else 27
            rawVersion.take(cutoff) + "…"
        }
        else -> rawVersion
    }

    // A corrupt/future persisted timestamp must not appear as a valid last update.
    // Bound before multiplying seconds to milliseconds to avoid Long overflow.
    val fetchedAtMillis = state.fetchedAtEpochSeconds
        .takeIf { it > 0L && nowMillis >= 0L && it <= nowMillis / 1_000L }
        ?.times(1_000L)
    val checked = if (fetchedAtMillis != null) {
        DateUtils.getRelativeTimeSpanString(
            fetchedAtMillis,
            nowMillis,
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } else {
        context.getString(R.string.offline_pack_updated_unknown)
    }

    return context.getString(R.string.offline_pack_metadata, size, version, checked)
}
