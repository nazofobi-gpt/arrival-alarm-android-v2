package com.nazofobi.arrivalalarm

import java.text.Normalizer
import java.util.Locale

/** Search German/ASCII spellings without editing any locally saved route. */
internal fun savedRouteMatchesQuery(preset: SavedRoutePreset, query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return true
    val requested = savedRouteSearchForms(normalizedQuery)
    return listOf(preset.name, preset.originLabel, preset.destinationLabel).any { label ->
        savedRouteSearchForms(label).any { searchable ->
            requested.any { needle -> searchable.contains(needle) }
        }
    }
}

private fun savedRouteSearchForms(value: String): Set<String> {
    val lower = value.lowercase(Locale.ROOT).replace("ß", "ss")
    val german = lower.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return setOf(
        fold(lower),
        fold(german),
        fold(german.replace("ae", "a").replace("oe", "o").replace("ue", "u")),
    )
}
