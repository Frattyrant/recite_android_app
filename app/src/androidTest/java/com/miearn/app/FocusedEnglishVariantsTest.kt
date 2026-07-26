package com.miearn.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.miearn.app.data.local.WordEntity
import com.miearn.app.ui.EnglishVariants
import com.miearn.app.ui.EnglishVariantsMode
import com.miearn.app.ui.theme.MIearnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FocusedEnglishVariantsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusedCardShowsOnlyPrimaryUntilAlternativesAreRequested() {
        composeRule.setContent {
            MIearnTheme {
                EnglishVariants(
                    word = word(),
                    mode = EnglishVariantsMode.FOCUSED,
                    onPlayVariant = { _, _ -> },
                    onPlayAll = {},
                )
            }
        }

        composeRule.onNodeWithText("fixture").assertIsDisplayed()
        composeRule.onNodeWithText("jig").assertDoesNotExist()
        composeRule.onNodeWithText("另有 2 种表达").performClick()
        composeRule.onNodeWithText("jig").assertIsDisplayed()
        composeRule.onNodeWithText("checking fixture").assertIsDisplayed()
        composeRule.onNodeWithText("播放全部").assertIsDisplayed()
    }

    @Test
    fun focusedOnlyCardNeverOffersAlternatives() {
        composeRule.setContent {
            MIearnTheme {
                EnglishVariants(
                    word = word(),
                    mode = EnglishVariantsMode.FOCUSED,
                    allowExpansion = false,
                    onPlayVariant = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("fixture").assertIsDisplayed()
        composeRule.onNodeWithText("另有 2 种表达").assertDoesNotExist()
        composeRule.onNodeWithText("jig").assertDoesNotExist()
    }

    @Test
    fun primaryPlayUsesOriginalVariantIndex() {
        var playedIndex = -1
        composeRule.setContent {
            MIearnTheme {
                EnglishVariants(
                    word = word(primaryEnglish = "jig"),
                    mode = EnglishVariantsMode.FOCUSED,
                    onPlayVariant = { _, index -> playedIndex = index },
                )
            }
        }

        composeRule.onNodeWithContentDescription("播放 jig").performClick()
        composeRule.runOnIdle {
            assertEquals(1, playedIndex)
        }
    }

    private fun word(primaryEnglish: String = "fixture") = WordEntity(
        id = "test",
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = 1,
        kind = "TERM",
        section = "",
        english = "fixture;jig;checking fixture",
        primaryEnglish = primaryEnglish,
        phonetic = "",
        chinese = "夹具",
        note = "",
        exampleEn = "",
        exampleZh = "",
        audioText = "fixture;jig;checking fixture",
        audioAsset = "",
    )
}
