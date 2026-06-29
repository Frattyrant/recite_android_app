package com.miearn.app.audio

import android.app.Application
import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.EnglishVariantParser
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SpeechAssetPlanTest {
    @Test
    fun runtimeSegmentsExactlyMatchEveryPackagedAudioSegment() {
        val assets = File("src/main/assets")
        val content = JSONObject(
            File(assets, "content/words_v1.json").readText(Charsets.UTF_8),
        )
        val manifest = JSONObject(
            File(assets, "content/audio_manifest_v1.json").readText(Charsets.UTF_8),
        ).getJSONObject("entries")
        val words = content.getJSONArray("words")

        repeat(words.length()) { wordIndex ->
            val item = words.getJSONObject(wordIndex)
            val word = item.toWordEntity()
            val displaySegments = EnglishVariantParser.parse(word.english, word.kind)
            val entry = manifest.getJSONObject(word.id)
            val packagedSegments = entry.optJSONArray("segments")

            if (displaySegments.size > 1) {
                requireNotNull(packagedSegments)
                assertEquals(word.id, displaySegments.size, packagedSegments.length())
                displaySegments.forEachIndexed { index, expected ->
                    val packaged = packagedSegments.getJSONObject(index)
                    assertEquals("$word.id:$index", expected, packaged.getString("text"))
                    assertTrue(
                        File(
                            assets,
                            packaged.getString("path"),
                        ).isFile,
                    )
                }
            } else {
                assertTrue(
                    "$word.id unexpectedly has stale segments",
                    packagedSegments == null || packagedSegments.length() == 0,
                )
            }

            val ttsSegments = SpeechRequestFactory.full(word).segments.map(SpeechSegment::text)
            assertEquals(
                word.id,
                displaySegments.map(EnglishVariantParser::toSpeechText),
                ttsSegments,
            )
            assertFalse(ttsSegments.any { '/' in it || '\\' in it })
        }
    }

    private fun JSONObject.toWordEntity() = WordEntity(
        id = getString("id"),
        category = getString("category"),
        categoryLabel = getString("categoryLabel"),
        sourceIndex = getInt("sourceIndex"),
        kind = getString("kind"),
        section = getString("section"),
        english = getString("english"),
        primaryEnglish = getString("primaryEnglish"),
        phonetic = getString("phonetic"),
        chinese = getString("chinese"),
        note = getString("note"),
        exampleEn = getString("exampleEn"),
        exampleZh = getString("exampleZh"),
        audioText = getString("audioText"),
        audioAsset = getString("audioAsset"),
    )
}
