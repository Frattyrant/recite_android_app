package com.miearn.app.ui

import com.miearn.app.data.local.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDetailModelsTest {
    @Test
    fun matchingPhoneticSegmentsUseSelectedIndex() {
        val word = word(
            english = "fixture；jig",
            phonetic = "/ˈfɪkstʃər/； /dʒɪɡ/",
        )

        val result = resolveVariantPhonetic(word, 1)

        assertEquals("/dʒɪɡ/", result?.text)
        assertFalse(checkNotNull(result).isWholeEntry)
    }

    @Test
    fun mismatchedSegmentCountFallsBackToWholePhonetic() {
        val word = word(
            english = "fixture；jig",
            phonetic = "/ˈfɪkstʃər/",
        )

        val result = resolveVariantPhonetic(word, 1)

        assertEquals("/ˈfɪkstʃər/", result?.text)
        assertTrue(checkNotNull(result).isWholeEntry)
    }

    @Test
    fun blankPhoneticReturnsNull() {
        assertNull(resolveVariantPhonetic(word(phonetic = ""), 0))
    }

    @Test
    fun invalidVariantIndexIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            wordDetailRequest(word(english = "fixture；jig"), 2)
        }
    }

    @Test
    fun singleExpressionUsesWholeTextWithoutFallbackLabel() {
        val request = wordDetailRequest(word(english = "fixture"), null)
        val phonetic = resolveVariantPhonetic(request.word, request.variantIndex)

        assertEquals("fixture", request.displayEnglish)
        assertFalse(checkNotNull(phonetic).isWholeEntry)
    }

    private fun word(
        english: String = "fixture",
        phonetic: String = "/ˈfɪkstʃər/",
    ) = WordEntity(
        id = "mec_0002",
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = 2,
        kind = "TERM",
        section = "",
        english = english,
        primaryEnglish = "fixture",
        phonetic = phonetic,
        chinese = "夹具",
        note = "机械设计",
        exampleEn = "The technician checked the fixture.",
        exampleZh = "技术员检查了夹具。",
        audioText = "fixture",
        audioAsset = "audio/mec_0002.ogg",
    )
}
