package com.shreyas.pdfreader.data

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The meanings of a word in one language and one part of speech. */
data class Meaning(val language: String, val partOfSpeech: String, val definitions: List<String>)

private const val DEFINITION_API = "https://en.wiktionary.org/api/rest_v1/page/definition/"
private const val USER_AGENT = "Folio PDF reader (https://github.com/ShreyasMahajann/Folio)"

/** Word meanings from Wiktionary. Needs an internet connection. */
// ponytail: no cache, each lookup is a request. Store results when repeat lookups are slow.
object Dictionary {

    /**
     * The meanings of [word]. Empty when Wiktionary does not have the word.
     * Null when Wiktionary cannot be reached.
     */
    suspend fun define(word: String): List<Meaning>? = withContext(Dispatchers.IO) {
        try {
            // Wiktionary has "Apple" and "apple" as different pages. The word in a book can start a sentence.
            val pages = listOf(word, word.lowercase()).distinct()
            pages.firstNotNullOfOrNull { page -> fetch(page)?.takeIf { it.isNotEmpty() } }.orEmpty()
        } catch (e: IOException) {
            null
        }
    }

    /** Null when Wiktionary has no page for [word]. */
    private fun fetch(word: String): List<Meaning>? {
        val path = URLEncoder.encode(word, "UTF-8").replace("+", "%20")
        val connection = URL(DEFINITION_API + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            return when (connection.responseCode) {
                200 -> {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    parseDefinitions(json) { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString() }
                }
                404 -> null
                else -> throw IOException("Wiktionary answered ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Reads an answer of the Wiktionary definition API. English comes first. Definitions are HTML;
 * [clean] makes plain text of one. A text that is not such an answer gives an empty list.
 */
fun parseDefinitions(json: String, clean: (String) -> String): List<Meaning> = runCatching {
    val root = JSONObject(json)
    root.keys().asSequence().toList().sortedBy { it != "en" }.flatMap { code ->
        val entries = root.optJSONArray(code) ?: return@flatMap emptyList()
        (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val list = entry.optJSONArray("definitions") ?: return@mapNotNull null
            val definitions = (0 until list.length())
                .mapNotNull { list.optJSONObject(it)?.optString("definition") }
                .map { clean(it).trim() }
                .filter { it.isNotEmpty() }
            if (definitions.isEmpty()) return@mapNotNull null
            Meaning(entry.optString("language", code), entry.optString("partOfSpeech"), definitions)
        }
    }
}.getOrDefault(emptyList())

/** The word to look up for a piece of selected text: without the punctuation around it. */
fun lookupWord(selected: String): String = selected.trim().trim { !it.isLetterOrDigit() }
