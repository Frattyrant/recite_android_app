package com.miearn.app.ui

import com.miearn.app.data.local.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamplePairsTest {
    @Test
    fun newlineDelimitedExamplesStayAligned() {
        val pairs = examplePairs(word(
            exampleEn = "Check the fixture.\nReplace the worn fixture.",
            exampleZh = "检查夹具。\n更换磨损的夹具。",
        ))

        assertEquals(
            listOf(
                ExamplePairDisplay("Check the fixture.", "检查夹具。"),
                ExamplePairDisplay("Replace the worn fixture.", "更换磨损的夹具。"),
            ),
            pairs,
        )
    }

    @Test
    fun legacySingleExampleRemainsOnePair() {
        assertEquals(
            listOf(ExamplePairDisplay("Check the fixture.", "检查夹具。")),
            examplePairs(word("Check the fixture.", "检查夹具。")),
        )
    }

    private fun word(
        exampleEn: String,
        exampleZh: String,
    ) = WordEntity(
        id = "example-test",
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = 1,
        kind = "TERM",
        section = "",
        english = "fixture",
        primaryEnglish = "fixture",
        phonetic = "/ˈfɪkstʃər/",
        chinese = "夹具",
        note = "",
        exampleEn = exampleEn,
        exampleZh = exampleZh,
        audioText = "fixture",
        audioAsset = "audio/example-test.ogg",
    )
}
