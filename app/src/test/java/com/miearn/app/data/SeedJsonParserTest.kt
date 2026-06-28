package com.miearn.app.data

import android.app.Application
import com.miearn.app.data.seed.SeedJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SeedJsonParserTest {
    @Test
    fun parsesVersionAndWordFields() {
        val json = """
            {
              "contentVersion":"2026.06.27",
              "words":[{
                "id":"mec_0001",
                "category":"mechanical",
                "categoryLabel":"机械专业词汇",
                "sourceIndex":1,
                "kind":"TERM",
                "section":"机械设计",
                "english":"limit switch",
                "primaryEnglish":"limit switch",
                "phonetic":"/test/",
                "chinese":"限位开关",
                "note":"",
                "exampleEn":"The technician checked the limit switch.",
                "exampleZh":"技术员检查了限位开关。",
                "audioText":"limit switch",
                "audioAsset":"audio/mec_0001.ogg"
              }]
            }
        """.trimIndent()

        val result = SeedJsonParser.parse(json)

        assertEquals("2026.06.27", result.contentVersion)
        assertEquals("limit switch", result.words.single().primaryEnglish)
        assertEquals("限位开关", result.words.single().chinese)
    }

    @Test
    fun rejectsMissingRequiredFields() {
        val json = """{"contentVersion":"1","words":[{"id":"broken"}]}"""

        assertThrows(IllegalArgumentException::class.java) {
            SeedJsonParser.parse(json)
        }
    }
}
