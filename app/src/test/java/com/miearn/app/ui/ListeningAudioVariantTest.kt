package com.miearn.app.ui

import com.miearn.app.data.local.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningAudioVariantTest {
    @Test
    fun listeningModeUsesThePrimaryExpressionIndex() {
        val word = WordEntity(
            id = "multi",
            category = "mechanical",
            categoryLabel = "机械专业词汇",
            sourceIndex = 1,
            kind = "TERM",
            section = "",
            english = "fixture；jig",
            primaryEnglish = "jig",
            phonetic = "",
            chinese = "夹具",
            note = "",
            exampleEn = "",
            exampleZh = "",
            audioText = "fixture, jig",
            audioAsset = "audio/multi.ogg",
        )

        assertEquals(1, listeningAudioVariantIndex(word))
    }
}
