package com.shreyas.pdfreader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A published version of the app. */
data class Release(val version: String, val url: String)

const val RELEASES_PAGE = "https://github.com/ShreyasMahajann/Folio/"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/ShreyasMahajann/Folio/releases/latest"

class UpdateChecker(private val context: Context) {

    val currentVersion: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** The newest release on GitHub, or null when it cannot be read. A failed check is not an error for the user. */
    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode != 200) return@runCatching null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val release = Release(json.getString("tag_name").removePrefix("v"), json.getString("html_url"))
                // The link opens in the browser. Accept only a link to the release pages of this app.
                release.takeIf { it.url.startsWith(RELEASES_PAGE) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

/**
 * True when version [latest] is higher than [current]. Versions are numbers with dots, such as 1.2.0.
 * A leading "v" and a suffix after "-" are ignored. Missing parts count as 0. A version that is not
 * a number is never newer.
 */
fun isNewer(latest: String, current: String): Boolean {
    val new = versionParts(latest) ?: return false
    val old = versionParts(current) ?: return false
    for (index in 0 until maxOf(new.size, old.size)) {
        val difference = new.getOrElse(index) { 0 } - old.getOrElse(index) { 0 }
        if (difference != 0) return difference > 0
    }
    return false
}

private fun versionParts(version: String): List<Int>? {
    val parts = version.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() }
    return if (parts.any { it == null }) null else parts.filterNotNull()
}
