package com.shreyas.pdfreader.data

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A PDF on a web server that is read in parts. The PDF reader asks only for the parts that it needs
 * for the page count and the first page, so the whole file is not downloaded.
 * A PDF has its table of contents at the end of the file. For that reason the start of the file is not enough.
 */
class RemotePdf private constructor(
    private val downloader: PdfDownloader,
    private val link: PdfLink,
    val sizeBytes: Long,
    first: ByteArray,
) : ProxyFileDescriptorCallback() {

    private val thread = HandlerThread("remote-pdf").apply { start() }
    private val blocks = hashMapOf(0L to first)
    private var loaded = first.size.toLong()

    override fun onGetSize(): Long = sizeBytes

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        var done = 0
        while (done < size && offset + done < sizeBytes) {
            val position = offset + done
            val index = position / BLOCK
            val block = blocks[index] ?: fetch(index)
            val from = (position - index * BLOCK).toInt()
            val count = minOf(size - done, block.size - from)
            if (count <= 0) break
            System.arraycopy(block, from, data, done, count)
            done += count
        }
        return done
    }

    override fun onRelease() {
        thread.quitSafely()
    }

    private fun fetch(index: Long): ByteArray {
        // A first page that needs more than this is not worth the data. The card then shows no picture.
        if (loaded >= MAX_BYTES) throw ErrnoException("read", OsConstants.EIO)
        val part = try {
            downloader.part(link, index * BLOCK, minOf(sizeBytes, (index + 1) * BLOCK) - 1)
        } catch (e: IOException) {
            throw ErrnoException("read", OsConstants.EIO, e)
        }
        if (part.total == null) throw ErrnoException("read", OsConstants.EIO)
        loaded += part.bytes.size
        blocks[index] = part.bytes
        return part.bytes
    }

    companion object {
        private const val BLOCK = 128 * 1024L
        private const val MAX_BYTES = 8_000_000L

        /**
         * Opens [link] for reading in parts. Returns null when the server gives only the whole file.
         * The caller closes the descriptor.
         *
         * @throws NotAPdfException when the content is not a PDF
         * @throws IOException on network errors
         */
        suspend fun open(context: Context, downloader: PdfDownloader, link: PdfLink): Pair<ParcelFileDescriptor, Long>? =
            withContext(Dispatchers.IO) {
                val first = downloader.part(link, 0, BLOCK - 1)
                val total = first.total ?: return@withContext null
                if (!String(first.bytes, Charsets.ISO_8859_1).contains("%PDF-")) throw NotAPdfException()
                val remote = RemotePdf(downloader, link, total, first.bytes)
                val storage = context.getSystemService(StorageManager::class.java)
                val descriptor = storage.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY,
                    remote,
                    Handler(remote.thread.looper),
                )
                descriptor to total
            }
    }
}
