package com.nazofobi.arrivalalarm

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * A local route template. Route IDs and realtime observations are deliberately not stored:
 * a preset restores the endpoints and user preferences, then the current graph recalculates.
 */
internal data class SavedRoutePreset(
    val id: String,
    val name: String,
    val originLatitude: Double,
    val originLongitude: Double,
    val originLabel: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val destinationLabel: String,
    val directOnlyRoutes: Boolean,
    val fewerTransfersFirst: Boolean,
    val shorterWalkingFirst: Boolean,
    val atMostOneTransfer: Boolean,
    val savedAtEpochMillis: Long,
)

/** Versioned, bounded, fail-closed codec for device-local saved route presets. */
internal object SavedRoutePresetsCodec {
    private const val HEADER = "SAVED_ROUTE_PRESETS_V1"
    private const val FIELD_SEPARATOR = '\u001f'
    private const val FIELD_COUNT = 13
    private const val MAX_SCAN_RECORDS = 50
    private const val MAX_ENCODED_CHARS = 32_768
    private const val MAX_ID_LENGTH = 64
    private const val MAX_NAME_LENGTH = 80
    private const val MAX_LOCATION_LABEL_LENGTH = 120
    const val MAX_PRESETS = 10

    private val idPattern = Regex("[A-Za-z0-9._-]{1,$MAX_ID_LENGTH}")

    fun encode(presets: List<SavedRoutePreset>): String {
        val normalized = normalize(presets)
        if (normalized.isEmpty()) return ""

        val encoded = buildString {
            append(HEADER)
            normalized.forEach { preset ->
                append('\n')
                append(
                    listOf(
                        encodeText(preset.id),
                        encodeText(preset.name),
                        preset.originLatitude.toString(),
                        preset.originLongitude.toString(),
                        encodeText(preset.originLabel),
                        preset.destinationLatitude.toString(),
                        preset.destinationLongitude.toString(),
                        encodeText(preset.destinationLabel),
                        encodeBoolean(preset.directOnlyRoutes),
                        encodeBoolean(preset.fewerTransfersFirst),
                        encodeBoolean(preset.shorterWalkingFirst),
                        encodeBoolean(preset.atMostOneTransfer),
                        preset.savedAtEpochMillis.toString(),
                    ).joinToString(FIELD_SEPARATOR.toString())
                )
            }
        }
        return encoded.takeIf { it.length <= MAX_ENCODED_CHARS }.orEmpty()
    }

    /** Invalid rows are skipped independently; a corrupt preset never erases valid siblings. */
    fun decode(encoded: String?): List<SavedRoutePreset> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_CHARS) return emptyList()
        val lines = encoded.split('\n')
        if (lines.firstOrNull()?.trimEnd('\r') != HEADER) return emptyList()

        val result = ArrayList<SavedRoutePreset>(MAX_PRESETS)
        val seenIds = HashSet<String>()
        for (rawLine in lines.drop(1).take(MAX_SCAN_RECORDS)) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) continue
            val fields = line.split(FIELD_SEPARATOR)
            if (fields.size != FIELD_COUNT) continue

            val preset = runCatching {
                SavedRoutePreset(
                    id = decodeText(fields[0]),
                    name = decodeText(fields[1]),
                    originLatitude = fields[2].toDouble(),
                    originLongitude = fields[3].toDouble(),
                    originLabel = decodeText(fields[4]),
                    destinationLatitude = fields[5].toDouble(),
                    destinationLongitude = fields[6].toDouble(),
                    destinationLabel = decodeText(fields[7]),
                    directOnlyRoutes = decodeBoolean(fields[8]),
                    fewerTransfersFirst = decodeBoolean(fields[9]),
                    shorterWalkingFirst = decodeBoolean(fields[10]),
                    atMostOneTransfer = decodeBoolean(fields[11]),
                    savedAtEpochMillis = fields[12].toLong(),
                )
            }.getOrNull() ?: continue

            if (!isValid(preset) || !seenIds.add(preset.id)) continue
            result += preset
            if (result.size == MAX_PRESETS) break
        }
        return result
    }

    private fun normalize(presets: List<SavedRoutePreset>): List<SavedRoutePreset> {
        val seenIds = HashSet<String>()
        return presets.asSequence()
            .filter(::isValid)
            .filter { seenIds.add(it.id) }
            .take(MAX_PRESETS)
            .toList()
    }

    private fun isValid(preset: SavedRoutePreset): Boolean =
        idPattern.matches(preset.id) &&
            isValidText(preset.name, MAX_NAME_LENGTH) &&
            isValidCoordinate(preset.originLatitude, -90.0, 90.0) &&
            isValidCoordinate(preset.originLongitude, -180.0, 180.0) &&
            isValidText(preset.originLabel, MAX_LOCATION_LABEL_LENGTH) &&
            isValidCoordinate(preset.destinationLatitude, -90.0, 90.0) &&
            isValidCoordinate(preset.destinationLongitude, -180.0, 180.0) &&
            isValidText(preset.destinationLabel, MAX_LOCATION_LABEL_LENGTH) &&
            preset.savedAtEpochMillis >= 0L

    private fun isValidCoordinate(value: Double, minimum: Double, maximum: Double): Boolean =
        value.isFinite() && value in minimum..maximum

    private fun isValidText(value: String, maximumLength: Int): Boolean =
        value.isNotBlank() && value.length <= maximumLength && value.none(Character::isISOControl)

    private fun encodeText(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decodeText(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun encodeBoolean(value: Boolean): String = if (value) "1" else "0"

    private fun decodeBoolean(value: String): Boolean = when (value) {
        "1" -> true
        "0" -> false
        else -> throw IllegalArgumentException("Invalid saved-route boolean")
    }
}