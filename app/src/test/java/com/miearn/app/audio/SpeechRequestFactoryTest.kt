package com.miearn.app.audio

import com.miearn.app.data.local.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRequestFactoryTest {
    private val fixture = WordEntity(
        id = "mec_0002",
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = 2,
        kind = "TERM",
        section = "",
        english = "fixture；jig",
        primaryEnglish = "fixture",
        phonetic = "/test/",
        chinese = "夹具",
        note = "",
        exampleEn = "Check the fixture.",
        exampleZh = "检查夹具。",
        audioText = "fixture, jig",
        audioAsset = "audio/mec_0002.ogg",
    )

    @Test
    fun fullRequestUsesCombinedAssetAndKeepsLogicalSegments() {
        val request = SpeechRequestFactory.full(fixture)

        assertEquals("audio/mec_0002.ogg", request.assetPath)
        assertEquals(listOf("fixture", "jig"), request.segments.map { it.text })
    }

    @Test
    fun variantRequestUsesOnlySelectedVariantAssetAndText() {
        val request = SpeechRequestFactory.variant(fixture, 1)

        assertEquals("jig", request.text)
        assertEquals("audio/variants/mec_0002_01.ogg", request.assetPath)
        assertEquals(listOf("jig"), request.segments.map { it.text })
    }

    @Test
    fun phraseRequestUsesSentenceSegmentsAndSanitizesSlashForTts() {
        val phrase = fixture.copy(
            id = "cus_0097",
            kind = "PHRASE",
            english = "Read at 300mm/s. Then inspect the result.",
            audioText = "Read at 300mm/s. Then inspect the result.",
        )

        val request = SpeechRequestFactory.full(phrase)

        assertEquals(
            listOf("Read at 300mm s.", "Then inspect the result."),
            request.segments.map { it.text },
        )
    }
}
