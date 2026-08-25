package com.miearn.app.domain

import com.miearn.app.data.local.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizContentPolicyTest {
    @Test
    fun everyQuizModeCanUseOneCompletePrimaryExpression() {
        val word = WordEntity(
            id = "fixture",
            category = "mechanical",
            categoryLabel = "机械专业词汇",
            sourceIndex = 1,
            kind = "TERM",
            section = "",
            english = "fixture; jig; checking fixture",
            primaryEnglish = "jig",
            phonetic = "/dʒɪɡ/",
            chinese = "夹具",
            note = "",
            exampleEn = "Check the jig.",
            exampleZh = "检查夹具。",
            audioText = "fixture, jig, checking fixture",
            audioAsset = "",
        )

        assertEquals("jig", QuizContentPolicy.primaryEnglish(word))
    }
}
