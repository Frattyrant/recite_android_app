package com.miearn.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.miearn.app.data.local.WordEntity
import com.miearn.app.ui.EnglishVariants
import com.miearn.app.ui.WordDetailScreen
import com.miearn.app.ui.resolveVariantPhonetic
import com.miearn.app.ui.theme.MIearnTheme
import com.miearn.app.ui.wordDetailRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WordDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun variantTextOpensDetailAndSpeakerOnlyPlaysSelectedVariant() {
        val word = word()
        var opened = -1
        var played = -1
        composeRule.setContent {
            MIearnTheme {
                EnglishVariants(
                    word = word,
                    onOpenVariant = { _, index -> opened = index },
                    onPlayVariant = { _, index -> played = index },
                )
            }
        }

        composeRule.onNodeWithText("jig").performClick()
        assertEquals(1, opened)
        assertEquals(-1, played)

        composeRule.onNodeWithContentDescription("播放 jig").performClick()
        assertEquals(1, played)
    }

    @Test
    fun fullScreenDetailShowsSelectedExpressionAndExistingLearningContent() {
        val word = word()
        val request = wordDetailRequest(word, 1)
        var backed = false
        var played = false
        var favorited = false
        composeRule.setContent {
            MIearnTheme {
                WordDetailScreen(
                    request = request,
                    phonetic = resolveVariantPhonetic(word, 1),
                    onBack = { backed = true },
                    onPlay = { played = true },
                    onFavorite = { favorited = true },
                )
            }
        }

        composeRule.onNodeWithText("jig").assertIsDisplayed()
        composeRule.onNodeWithText("/dʒɪɡ/").assertIsDisplayed()
        composeRule.onNodeWithText("夹具").assertIsDisplayed()
        composeRule.onNodeWithText("The technician checked the fixture.").assertIsDisplayed()
        composeRule.onNodeWithText("技术员检查了夹具。").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("播放 jig").performClick()
        composeRule.onNodeWithContentDescription("收藏词条").performClick()
        composeRule.onNodeWithContentDescription("返回学习").performClick()
        assertTrue(played)
        assertTrue(favorited)
        assertTrue(backed)
    }

    private fun word() = WordEntity(
        id = "mec_0002",
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = 2,
        kind = "TERM",
        section = "",
        english = "fixture；jig",
        primaryEnglish = "fixture",
        phonetic = "/ˈfɪkstʃər/； /dʒɪɡ/",
        chinese = "夹具",
        note = "机械设计",
        exampleEn = "The technician checked the fixture.",
        exampleZh = "技术员检查了夹具。",
        audioText = "fixture, jig",
        audioAsset = "audio/mec_0002.ogg",
    )
}
