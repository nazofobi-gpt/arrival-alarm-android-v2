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

/**
 * Decode metadata lets the future persistence adapter preserve an unreadable/corrupt snapshot
 * instead of treating a partial decode as permission to overwrite it.
 */
internal data class SavedRoutePresetDecodeResult(
    val presets: List<SavedRoutePreset>,
    val isSourceSafeToReplace: Boolean,
    val issues: Set<String>,
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
    const val MAX_PRESETS = 12

    private val idPattern = Regex("[A-Za-z0-9._-]{1,$MAX_ID_LENGTH}")

    /**
     * Returns null for invalid, duplicate or over-cap input. Callers must not replace an
     * existing preference value when encoding is rejected. An empty list intentionally clears.
     */
    fun encode(presets: List<SavedRoutePreset>): String? {
        if (presets.isEmpty()) return ""
        if (presets.size > MAX_PRESETS) return null

        val seenIds = HashSet<String>()
        if (presets.any { !isValid(it) || !seenIds.add(it.id) }) return null

        val encoded = buildString {
            append(HEADER)
            presets.forEach { preset ->
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
        return encoded.takeIf { it.length <= MAX_ENCODED_CHARS }
    }

    /** Convenience view for consumers that only need valid decoded entries. */
    fun decode(encoded: String?): List<SavedRoutePreset> = decodeWithStatus(encoded).presets

    /** Invalid rows are skipped independently, but make the source unsafe to overwrite. */
    fun decodeWithStatus(encoded: String?): SavedRoutePresetDecodeResult {
        if (encoded.isNullOrBlank()) {
            return SavedRoutePresetDecodeResult(emptyList(), isSourceSafeToReplace = true, issues = emptySet())
        }
        if (encoded.length > MAX_ENCODED_CHARS) {
            return SavedRoutePresetDecodeResult(
                emptyList(),
                isSourceSafeToReplace = false,
                issues = setOf("payload-too-large"),
            )
        }

        val lines = encoded.split('\n')
        if (lines.firstOrNull()?.trimEnd('\r') != HEADER) {
            return SavedRoutePresetDecodeResult(
                emptyList(),
                isSourceSafeToReplace = false,
                issues = setOf("unsupported-version"),
            )
        }

        val result = ArrayList<SavedRoutePreset>(MAX_PRESETS)
        val seenIds = HashSet<String>()
        val issues = linkedSetOf<String>()
        var scannedRecords = 0

        for (rawLine in lines.drop(1)) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) continue
            scannedRecords += 1
            if (scannedRecords > MAX_SCAN_RECORDS) {
                issues += "too-many-records"
                break
            }

            val fields = line.split(FIELD_SEPARATOR)
            if (fields.size != FIELD_COUNT) {
                issues += "corrupt-record"
                continue
            }

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
            }.getOrNull()

            if (preset == null || !isValid(preset)) {
                issues += "corrupt-record"
                continue
            }
            if (!seenIds.add(preset.id)) {
                issues += "duplicate-id"
                continue
            }
            if (result.size == MAX_PRESETS) {
                issues += "too-many-presets"
                continue
            }
            result += preset
        }

        return SavedRoutePresetDecodeResult(
            presets = result,
            isSourceSafeToReplace = issues.isEmpty(),
            issues = issues,
        )
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