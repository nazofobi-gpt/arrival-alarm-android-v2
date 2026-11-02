package com.nazofobi.arrivalalarm

/**
 * Converts an explicit user-entered map coordinate into a selectable point.
 * Accepts decimal dots and decimal commas (German/Turkish keyboards) while
 * rejecting non-finite, out-of-range and malformed coordinates.
 */
internal object MapCoordinateInput {
    fun parse(latitude: String, longitude: String, label: String): MapPoint? {
        val lat = parseNumber(latitude) ?: return null
        val lon = parseNumber(longitude) ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return MapPoint(lat, lon, label)
    }

    private fun parseNumber(raw: String): Double? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 32) return null
        if (value.any { it != '+' && it != '-' && it != '.' && it != ',' && !it.isDigit() }) {
            return null
        }
        if (value.count { it == '.' || it == ',' } > 1) return null
        return value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
