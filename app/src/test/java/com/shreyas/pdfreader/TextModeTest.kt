package com.shreyas.pdfreader

import com.shreyas.pdfreader.data.Meaning
import com.shreyas.pdfreader.data.lookupWord
import com.shreyas.pdfreader.data.parseDefinitions
import com.shreyas.pdfreader.pdf.assembleText
import com.shreyas.pdfreader.pdf.findHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextModeTest {

    @Test
    fun linesOfABlockBecomeOneParagraph() {
        val blocks = listOf(listOf("The quick brown", " fox jumps "), listOf("Second block"))
        assertEquals("The quick brown fox jumps\n\nSecond block", assembleText(blocks))
    }

    @Test
    fun wordSplitAtLineEndBecomesOneWord() {
        assertEquals("an important word", assembleText(listOf(listOf("an impor-", "tant word"))))
        // A hyphen before a capital letter or after a space is part of the text.
        assertEquals("Anglo- Saxon", assembleText(listOf(listOf("Anglo-", "Saxon"))))
        assertEquals("a - b", assembleText(listOf(listOf("a -", "b"))))
    }

    @Test
    fun emptyLinesAndBlocksAreLeftOut() {
        assertEquals("", assembleText(emptyList()))
        assertEquals("text", assembleText(listOf(listOf(" ", ""), emptyList(), listOf("text"))))
    }

    @Test
    fun linesJoinWithoutSpaceWhenTheScriptHasNone() {
        assertEquals("日本語", assembleText(listOf(listOf("日本", "語")), lineJoin = ""))
    }

    @Test
    fun highlightIsFoundNearestToItsOldPlace() {
        val text = "one two one two one"
        assertEquals(8 to 11, findHighlight(text, "one", 8))
        // The text moved: the nearest occurrence wins.
        assertEquals(8 to 11, findHighlight(text, "one", 10))
        assertEquals(16 to 19, findHighlight(text, "one", 40))
        assertEquals(0 to 3, findHighlight(text, "one", -5))
    }

    @Test
    fun highlightOfMissingTextIsNotFound() {
        assertNull(findHighlight("one two", "three", 0))
        assertNull(findHighlight("one two", "", 0))
        assertNull(findHighlight("", "one", 0))
    }

    @Test
    fun definitionsAreReadWithEnglishFirst() {
        val json = """
            {
              "fr": [{"partOfSpeech": "Noun", "language": "French",
                      "definitions": [{"definition": "<b>pain</b> bread"}]}],
              "en": [{"partOfSpeech": "Noun", "language": "English",
                      "definitions": [{"definition": "<a href=\"x\">An ache</a>"}, {"definition": ""}]},
                     {"partOfSpeech": "Verb", "language": "English", "definitions": []}]
            }
        """.trimIndent()
        val withoutTags = { html: String -> html.replace(Regex("<[^>]*>"), "") }
        assertEquals(
            listOf(
                Meaning("English", "Noun", listOf("An ache")),
                Meaning("French", "Noun", listOf("pain bread")),
            ),
            parseDefinitions(json, withoutTags),
        )
    }

    @Test
    fun answerWithoutDefinitionsGivesNothing() {
        assertEquals(emptyList<Meaning>(), parseDefinitions("{}", { it }))
        assertEquals(emptyList<Meaning>(), parseDefinitions("not json", { it }))
        assertEquals(emptyList<Meaning>(), parseDefinitions("""{"title": "Not found."}""", { it }))
    }

    @Test
    fun lookupWordHasNoPunctuation() {
        assertEquals("word", lookupWord(" \"word,\" "))
        assertEquals("don't", lookupWord("don't."))
        assertEquals("", lookupWord(" ... "))
    }
}
