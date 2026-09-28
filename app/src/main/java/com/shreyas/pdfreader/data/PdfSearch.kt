package com.shreyas.pdfreader.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The search engine takes the request for a robot. */
class SearchBlockedException : IOException("The search engines asked for a check. Try again in a minute.")

private val WRAPPED_ADDRESS = Regex("""^https?://(?:[^/]*duckduckgo\.com/l/.*[?&]uddg|[^/]*google\.[a-z.]+/url.*[?&]q)=([^&]+)""")

/**
 * The address of the file behind a link of a result page, or null for a link that stays on the search engine.
 * Both engines can wrap the address in a redirect: DuckDuckGo in "uddg", Google in "q".
 */
fun resultLink(raw: String): String? {
    fun host(link: String) = runCatching { URI(link).host }.getOrNull().orEmpty()
    fun ofEngine(host: String) = host.endsWith("duckduckgo.com") || Regex("""(^|\.)google\.[a-z.]+$""").containsMatchIn(host)

    var link = if (raw.startsWith("//")) "https:$raw" else raw
    if (ofEngine(host(link))) {
        val wrapped = WRAPPED_ADDRESS.find(link) ?: return null
        link = URLDecoder.decode(wrapped.groupValues[1], "UTF-8")
    }
    val host = host(link)
    // A link that stays on the search engine is an advertisement or a menu.
    return if (!link.startsWith("http") || host.isEmpty() || ofEngine(host)) null else secureUrl(link)
}

/** Equal for two links to the same file, so a file that both engines found shows one time. */
fun linkKey(url: String): String =
    url.substringAfter("://").removePrefix("www.").substringBefore('#').trimEnd('/')

/** Takes one link from each list in turn, so the best results of each engine come first. No link two times. */
fun mergeLinks(lists: List<List<String>>, known: Set<String> = emptySet()): List<String> {
    val keys = known.toMutableSet()
    return (0 until (lists.maxOfOrNull { it.size } ?: 0))
        .flatMap { index -> lists.mapNotNull { it.getOrNull(index) } }
        .filter { keys.add(linkKey(it)) }
}

/**
 * How to read the result pages of one search engine.
 * ponytail: selectors for the markup of two sites. Change them here when a site changes its page.
 *
 * @param links JavaScript expression for the list of link elements of the results
 * @param more JavaScript expression that is true when there is a next page
 * @param openNext opens the page after the page that is open. `page` counts from 1.
 */
enum class SearchEngine(
    val firstPage: String,
    val links: String,
    val more: String,
    val openNext: (view: WebView, query: String, page: Int) -> Unit,
) {
    DUCKDUCKGO(
        firstPage = "https://html.duckduckgo.com/html/?q=",
        links = "document.querySelectorAll('a.result__a')",
        more = "!!document.querySelector('input[value=\"Next\"]')",
        openNext = { view, _, _ -> view.evaluateJavascript("document.querySelector('input[value=\"Next\"]').form.submit()", null) },
    ),
    GOOGLE(
        firstPage = "https://www.google.com/search?q=",
        // The title of a result is a heading inside the link. The markup differs between page versions.
        links = "document.querySelectorAll('a:has(h3), a:has([role=heading]), a[href*=\".pdf\"]')",
        more = "found.length>0",
        openNext = { view, query, page -> view.loadUrl(GOOGLE.address(query) + "&start=${page * 10}") },
    );

    fun address(query: String): String = firstPage + Uri.encode("filetype:pdf ${query.trim()}")
}

/** One page of results. [more] is false on the last page. */
data class SearchPage(val links: List<String>, val more: Boolean)

/**
 * Reads the results of one search engine. The engines give results only to a browser, so a WebView
 * that is not on the screen loads the result page, and the links are read from it.
 * The page gets no access to files or to app code.
 * ponytail: a CAPTCHA cannot be solved here. The search then continues with the other engine.
 */
class SearchBrowser(private val context: Context, private val engine: SearchEngine) {

    private var webView: WebView? = null
    private var page = 0

    /**
     * The next page of results for [query]. [first] starts again at the first page.
     *
     * @throws SearchBlockedException when the engine asks for a CAPTCHA
     * @throws IOException when the page does not load
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun nextPage(query: String, first: Boolean): SearchPage = withContext(Dispatchers.Main) {
        val view = webView ?: WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // A page without a size does not draw its results.
            layout(0, 0, 1080, 1920)
            onResume()
            webView = this
        }
        if (first) page = 0
        val script = "(function(){var found=Array.from(${engine.links}).filter(Boolean);" +
            "return {links:found.map(function(a){return a.href}),more:${engine.more}," +
            "blocked:/anomaly|captcha/i.test(document.documentElement.innerHTML)}})()"
        // A timeout must count as a failure of this engine, not as the end of the search.
        val read = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<JSONObject> { continuation ->
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(finished: WebView, url: String) = read(finished, url, READS)

                    // The results can come into the page after the page is loaded. An empty page is read again.
                    private fun read(finished: WebView, url: String, readsLeft: Int) {
                        finished.evaluateJavascript(script) { answer ->
                            if (!continuation.isActive) return@evaluateJavascript
                            val read = runCatching { JSONObject(answer) }.getOrNull()
                            val blocked = url.contains("/sorry/")
                            val empty = (read?.optJSONArray("links")?.length() ?: 0) == 0
                            when {
                                empty && !blocked && readsLeft > 1 ->
                                    finished.postDelayed({ read(finished, url, readsLeft - 1) }, READ_AGAIN_MS)
                                empty && (blocked || read?.optBoolean("blocked") == true) ->
                                    continuation.resumeWithException(SearchBlockedException())
                                else -> continuation.resume(read ?: JSONObject())
                            }
                        }
                    }

                    override fun onReceivedError(failed: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame && continuation.isActive) {
                            Log.i(TAG, "$engine did not load: ${error.description}")
                            continuation.resumeWithException(IOException("$engine did not load"))
                        }
                    }

                    // No app links, no intents.
                    override fun shouldOverrideUrlLoading(loading: WebView, request: WebResourceRequest): Boolean =
                        request.url.scheme != "https"
                }
                continuation.invokeOnCancellation { view.post { view.stopLoading() } }
                if (page == 0) view.loadUrl(engine.address(query)) else engine.openNext(view, query, page)
            }
        } ?: throw IOException("$engine did not answer in time")

        val raw = read.optJSONArray("links")
        val userAgent = view.settings.userAgentString
        val links = withContext(Dispatchers.IO) {
            (0 until (raw?.length() ?: 0))
                .map { raw!!.getString(it) }
                .distinct()
                .map { async { resultLink(it) ?: redirectTarget(it, userAgent) } }
                .awaitAll()
                .filterNotNull()
                .distinct()
        }
        Log.i(TAG, "$engine page $page: ${links.size} links of ${raw?.length()}")
        page++
        SearchPage(links, read.optBoolean("more"))
    }

    /**
     * Google gives some links as a redirect with a code in place of the address.
     * The address is in the answer to a request for the redirect.
     */
    private fun redirectTarget(link: String, userAgent: String): String? {
        if (!link.contains("/goto?")) return null
        return runCatching {
            val connection = URL(link).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(link)?.let { connection.setRequestProperty("Cookie", it) }
                val status = connection.responseCode
                val target = connection.getHeaderField("Location")
                if (target == null) Log.i(TAG, "$engine redirect gave status $status and no address")
                target?.let(::resultLink)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** Call on the main thread. */
    fun close() {
        webView?.destroy()
        webView = null
    }

    private companion object {
        const val TAG = "FolioSearch"
        const val TIMEOUT_MS = 20_000L
        const val READS = 10
        const val READ_AGAIN_MS = 600L
    }
}
