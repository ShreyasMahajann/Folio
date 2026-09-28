package com.shreyas.pdfreader.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import javax.net.ssl.SSLException

/** A PDF found on the web, not downloaded yet. [referer] is the page that had the link. */
data class PdfLink(val url: String, val fileName: String, val userAgent: String?, val referer: String? = null)

class NotAPdfException : IOException("The link does not lead to a PDF file")

class HttpStatusException(val status: Int) : IOException("The server answered with status $status")

class TooLargeException(maxBytes: Long) : IOException("The file is larger than ${maxBytes / 1_000_000} MB")

/** The app has no permission for traffic without encryption. Most sites give the same file over https. */
fun secureUrl(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

/** True for a failure of the connection, which a second try can pass. False for an answer of the server. */
fun worthRetry(error: Throwable): Boolean =
    error is SocketException || error is SocketTimeoutException || error is SSLException || error is EOFException

class PdfDownloader(private val context: Context) {

    /**
     * Downloads [link] into [target] and returns the file. The caller deletes it.
     * [onProgress] gets 0..1, or null when the server does not tell the size.
     * A failed connection is tried again, [TRIES] times in total.
     *
     * @throws NotAPdfException when the content is not a PDF, for example a login page
     * @throws TooLargeException on files larger than [maxBytes]
     * @throws IOException on network errors
     */
    suspend fun download(
        link: PdfLink,
        target: File,
        maxBytes: Long = MAX_BYTES,
        onProgress: (Float?) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        var tries = 1
        var done = false
        while (!done) {
            try {
                // ponytail: each try starts the file again. Add a Range request when large files fail midway.
                fetch(link, target, maxBytes, onProgress)
                done = true
            } catch (e: Throwable) {
                target.delete()
                if (tries == TRIES || !worthRetry(e)) throw e
                onProgress(null)
                delay(1000L * tries)
                tries++
            }
        }
        target
    }

    private suspend fun fetch(
        link: PdfLink,
        target: File,
        maxBytes: Long,
        onProgress: (Float?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = connect(link)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw HttpStatusException(status)
            val total = connection.contentLengthLong
            if (total > maxBytes) throw TooLargeException(maxBytes)

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        // The PDF marker is in the first bytes. A web page is rejected before it is stored.
                        if (done == 0L && !String(buffer, 0, read, Charsets.ISO_8859_1).contains("%PDF-")) {
                            throw NotAPdfException()
                        }
                        output.write(buffer, 0, read)
                        done += read
                        if (done > maxBytes) throw TooLargeException(maxBytes)
                        onProgress(if (total > 0) done.toFloat() / total else null)
                    }
                    if (done == 0L) throw NotAPdfException()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Bytes of a file. [total] is the size of the whole file, null when the server gave no part. */
    class Part(val bytes: ByteArray, val total: Long?)

    /**
     * Reads the bytes [from]..[to] of [link] with a Range request. A server that does not know
     * Range requests answers with the whole file. Then nothing is read and [Part.total] is null.
     */
    fun part(link: PdfLink, from: Long, to: Long): Part {
        val connection = connect(link)
        try {
            connection.setRequestProperty("Range", "bytes=$from-$to")
            // The positions must be positions in the file, not in a compressed form of it.
            connection.setRequestProperty("Accept-Encoding", "identity")
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_OK) return Part(ByteArray(0), null)
            if (status != HttpURLConnection.HTTP_PARTIAL) throw HttpStatusException(status)
            // "bytes 0-131071/7654321"
            val total = connection.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull()
            val bytes = connection.inputStream.use { it.readBytes() }
            return Part(bytes, total)
        } finally {
            connection.disconnect()
        }
    }

    private fun connect(link: PdfLink): HttpURLConnection {
        val url = secureUrl(link.url)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        // The request must look like the request of the browser that found the link.
        // Some sites close the connection for other requests.
        link.userAgent?.let { connection.setRequestProperty("User-Agent", it) }
        link.referer?.let { connection.setRequestProperty("Referer", it) }
        connection.setRequestProperty("Accept", "application/pdf,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
        // Same session as the search page. Some sites give the file only with their cookies.
        CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
        return connection
    }

    companion object {
        const val MAX_BYTES = 500_000_000L
        const val TRIES = 3

        /** A file name that is safe inside one directory and ends with `.pdf`. */
        fun safeFileName(guessed: String): String {
            val cleaned = guessed.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.').take(120)
            val base = cleaned.ifEmpty { "Document" }
            return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
        }
    }
}
