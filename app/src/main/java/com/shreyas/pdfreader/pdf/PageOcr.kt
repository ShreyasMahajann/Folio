package com.shreyas.pdfreader.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Every script also reads Latin letters, so a book needs one script. */
enum class OcrScript(val label: String) {
    LATIN("Latin"),
    DEVANAGARI("Hindi"),
    CHINESE("Chinese"),
    JAPANESE("Japanese"),
    KOREAN("Korean");

    /** Chinese and Japanese put no space between words, so lines join without one. */
    val lineJoin: String get() = if (this == CHINESE || this == JAPANESE) "" else " "

    companion object {
        fun of(name: String): OcrScript = entries.firstOrNull { it.name == name } ?: LATIN
    }
}

/** Text recognition for one script, with the ML Kit model that is inside the APK. */
class PageOcr(val script: OcrScript) {

    private val recognizer: TextRecognizer = TextRecognition.getClient(
        when (script) {
            OcrScript.LATIN -> TextRecognizerOptions.DEFAULT_OPTIONS
            OcrScript.DEVANAGARI -> DevanagariTextRecognizerOptions.Builder().build()
            OcrScript.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
            OcrScript.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
            OcrScript.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
        },
    )

    /** The text of [bitmap]. Empty when the page has no text. ML Kit does the work on its own threads. */
    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val blocks = result.textBlocks.map { block -> block.lines.map { it.text } }
                continuation.resume(assembleText(blocks, script.lineJoin))
            }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    fun close() = recognizer.close()
}

/**
 * Makes running text from recognized [blocks] of lines. The lines of a block become one paragraph,
 * so the text can flow to the width of the screen. A word that was split with a hyphen at the end
 * of a line becomes one word again.
 */
// ponytail: block order is the order ML Kit gives. Pages with columns or tables can read out of order.
// Sort blocks by their bounding boxes if that becomes a problem.
fun assembleText(blocks: List<List<String>>, lineJoin: String = " "): String =
    blocks.map { lines ->
        lines.map { it.trim() }.filter { it.isNotEmpty() }.fold("") { text, line ->
            when {
                text.isEmpty() -> line
                text.endsWithSplitWord() && line.first().isLowerCase() -> text.dropLast(1) + line
                else -> text + lineJoin + line
            }
        }
    }.filter { it.isNotEmpty() }.joinToString("\n\n")

private fun String.endsWithSplitWord(): Boolean = length > 1 && last() == '-' && this[length - 2].isLetter()

enum class HighlightColor {
    YELLOW, GREEN, BLUE, PINK;

    companion object {
        fun of(name: String): HighlightColor = entries.firstOrNull { it.name == name } ?: YELLOW
    }
}

/**
 * Where a highlight of [text] is in [pageText]: the occurrence nearest to [position], as start and
 * end (exclusive). Null when the text is not on the page. The text of a page changes when the page
 * is recognized again, for example after a crop.
 */
fun findHighlight(pageText: String, text: String, position: Int): Pair<Int, Int>? {
    if (text.isEmpty()) return null
    var best = -1
    var index = pageText.indexOf(text)
    while (index >= 0) {
        if (best < 0 || kotlin.math.abs(index - position) < kotlin.math.abs(best - position)) best = index
        index = pageText.indexOf(text, index + 1)
    }
    return if (best < 0) null else best to best + text.length
}
