package com.nazofobi.arrivalalarm

import java.text.Normalizer
import java.util.Locale

/**
 * Bounded, opt-in fallback after an exact nationwide GTFS FTS lookup returns no stops.
 * Only one misspelled token is allowed; names and token order must otherwise agree.
 * This never chooses a stop or changes a route/alarm without user confirmation.
 */
internal object OfflineStopTypoMatcher {
    private val combiningMarks = Regex("\\p{M}+")
    private val tokenSeparator = Regex("[^\\p{L}\\p{N}]+")

    internal fun tokens(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace("ß", "ss")
            .split(tokenSeparator)
            .filter(String::isNotBlank)

    /** Do not use a broad nationwide scan for short or punctuation-only input. */
    internal fun eligible(queryTokens: List<String>): Boolean =
        queryTokens.size in 1..4 &&
            queryTokens.first().length >= 5 &&
            queryTokens.first().take(3).all { it in 'a'..'z' || it in '0'..'9' } &&
            queryTokens.all { it.length >= 2 }

    internal fun matchesOneTypo(queryTokens: List<String>, stopName: String): Boolean {
        if (!eligible(queryTokens)) return false
        val nameTokens = tokens(stopName)
        if (nameTokens.size < queryTokens.size) return false

        // Allow "Bremne" → "Bremen", or "Bremne Hbf" → "Bremen Hbf".
        // Require the query tokens to be adjacent and in the same order.
        for (start in 0..(nameTokens.size - queryTokens.size)) {
            var typoCount = 0
            var allTokensMatch = true
            for (i in queryTokens.indices) {
                val query = queryTokens[i]
                val actual = nameTokens[start + i]
                if (query == actual) continue
                if (query.length < 4 || !oneEditApart(query, actual)) {
                    allTokensMatch = false
                    break
                }
                typoCount++
                if (typoCount > 1) {
                    allTokensMatch = false
                    break
                }
            }
            if (allTokensMatch && typoCount == 1) return true
        }
        return false
    }

    private fun oneEditApart(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        if (left.length == right.length) {
            val mismatches = left.indices.filter { left[it] != right[it] }
            return mismatches.size == 1 ||
                (mismatches.size == 2 && mismatches[1] == mismatches[0] + 1 &&
                    left[mismatches[0]] == right[mismatches[1]] &&
                    left[mismatches[1]] == right[mismatches[0]])
        }
        val longer = if (left.length > right.length) left else right
        val shorter = if (left.length > right.length) right else left
        var shortIndex = 0
        var edits = 0
        for (character in longer) {
            if (shortIndex < shorter.length && character == shorter[shortIndex]) {
                shortIndex++
            } else {
                edits++
                if (edits > 1) return false
            }
        }
        return shortIndex == shorter.length && edits == 1
    }
}
