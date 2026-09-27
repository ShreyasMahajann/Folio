package com.shreyas.pdfreader.data

const val SEARCH_HISTORY_SIZE = 20

/**
 * The history after a search for [query], newest first.
 * A repeated query moves to the top. Upper and lower case count as the same query.
 */
fun updatedHistory(history: List<String>, query: String): List<String> {
    val cleaned = query.trim().replace('\n', ' ')
    if (cleaned.isEmpty()) return history
    return (listOf(cleaned) + history.filterNot { it.equals(cleaned, ignoreCase = true) }).take(SEARCH_HISTORY_SIZE)
}
