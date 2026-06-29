package com.miearn.app.importing

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvVocabularyReaderTest {
    @Test fun readsBomQuotesCommasNewlinesAndEscapedQuotes() {
        val csv = "\uFEFFword,translation,note\r\nfixture,夹具,\"a, b\"\r\njig,治具,\"line 1\nline \"\"2\"\"\"\r\n"
        val rows = CsvVocabularyReader().rows(ByteArrayInputStream(csv.toByteArray())).toList()
        assertEquals(listOf("word", "translation", "note"), rows[0].cells)
        assertEquals("a, b", rows[1].cells[2])
        assertEquals("line 1\nline \"2\"", rows[2].cells[2])
    }

    @Test fun fallsBackToGb18030() {
        val bytes = "word,中文\r\nfixture,夹具".toByteArray(Charset.forName("GB18030"))
        assertEquals("夹具", CsvVocabularyReader().rows(ByteArrayInputStream(bytes)).toList()[1].cells[1])
    }

    @Test fun rejectsMoreThanLimit() {
        val csv = buildString { repeat(20_001) { append("word$it,释义\n") } }
        assertThrows(ImportLimitException::class.java) {
            CsvVocabularyReader().rows(ByteArrayInputStream(csv.toByteArray())).toList()
        }
    }
}