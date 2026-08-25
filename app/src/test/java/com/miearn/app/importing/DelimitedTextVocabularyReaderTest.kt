package com.miearn.app.importing

import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class DelimitedTextVocabularyReaderTest {
    @Test
    fun readsTsvAndPreservesQuotedTabs() {
        val text = "word\t中文\tnote\nfixture\t夹具\t\"a\tb\""

        val rows = DelimitedTextVocabularyReader(VocabularyFileType.TSV)
            .rows(text.byteInputStream())
            .toList()

        assertEquals(listOf("word", "中文", "note"), rows.first().cells)
        assertEquals("a\tb", rows[1].cells[2])
    }

    @Test
    fun treatsTxtWithoutStableTabsAsOneCompleteRowAndDoesNotSplitSemicolon() {
        val rows = DelimitedTextVocabularyReader(VocabularyFileType.TXT)
            .rows("fixture;jig\nactuator;drive".byteInputStream())
            .toList()

        assertEquals(listOf("fixture;jig"), rows.first().cells)
        assertEquals(listOf("actuator;drive"), rows[1].cells)
    }

    @Test
    fun infersStableCommaColumnsFromTxtWithoutTreatingSemicolonAsDelimiter() {
        val rows = DelimitedTextVocabularyReader(VocabularyFileType.TXT)
            .rows("word,中文\nfixture,夹具\nactuator,执行器".byteInputStream())
            .toList()

        assertEquals(listOf("word", "中文"), rows.first().cells)
        assertEquals(listOf("fixture", "夹具"), rows[1].cells)
    }

    @Test
    fun infersStablePipeColumnsFromTxt() {
        val rows = DelimitedTextVocabularyReader(VocabularyFileType.TXT)
            .rows("fixture|夹具\nactuator|执行器".byteInputStream())
            .toList()

        assertEquals(listOf("fixture", "夹具"), rows.first().cells)
        assertEquals(listOf("actuator", "执行器"), rows[1].cells)
    }

    @Test
    fun readsGb18030Text() {
        val bytes = "fixture\t夹具".toByteArray(Charset.forName("GB18030"))

        val rows = DelimitedTextVocabularyReader(VocabularyFileType.TSV)
            .rows(bytes.inputStream())
            .toList()

        assertEquals("夹具", rows.single().cells[1])
    }
}
