package com.shreyas.pdfreader.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A PDF found on the web, not downloaded yet. */
data class PdfLink(val url: String, val fileName: String, val userAgent: String?)

class NotAPdfException : IOException("The link does not lead to a PDF file")

class PdfDownloader(private val context: Context) {

    /**
     * Downloads [link] into the cache and returns the file. The caller imports it and deletes it.
     * [onProgress] gets 0..1, or null when the server does not tell the size.
     *
     * @throws NotAPdfException when the content is not a PDF, for example a login page
     * @throws IOException on network errors and on files larger than [MAX_BYTES]
     */
    suspend fun download(link: PdfLink, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "downloads")
        directory.deleteRecursively()
        directory.mkdirs()
        val target = File(directory, link.fileName)
        val connection = URL(link.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            link.userAgent?.let { connection.setRequestProperty("User-Agent", it) }
            // Same session as the search page. Some sites give the file only with their cookies.
            CookieManager.getInstance().getCookie(link.url)?.let { connection.setRequestProperty("Cookie", it) }

            val status = connection.responseCode
            if (status !in 200..299) throw IOException("The server answered with status $status")
            val total = connection.contentLengthLong
            if (total > MAX_BYTES) throw IOException("The file is larger than ${MAX_BYTES / 1_000_000} MB")

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
                        if (done > MAX_BYTES) throw IOException("The file is larger than ${MAX_BYTES / 1_000_000} MB")
                        onProgress(if (total > 0) done.toFloat() / total else null)
                    }
                    if (done == 0L) throw NotAPdfException()
                }
            }
            target
        } catch (e: Throwable) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MAX_BYTES = 500_000_000L

        /** A file name that is safe inside one directory and ends with `.pdf`. */
        fun safeFileName(guessed: String): String {
            val cleaned = guessed.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.').take(120)
            val base = cleaned.ifEmpty { "Document" }
            return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
        }
    }
}
