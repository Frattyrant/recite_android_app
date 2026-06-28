package com.miearn.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import com.miearn.app.data.local.WordEntity
import com.miearn.app.ui.EnglishVariants
import com.miearn.app.ui.theme.MIearnTheme
import org.junit.Rule
import org.junit.Test

class EnglishVariantsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyTokenIncludingSingleTokenUsesPlayableHighlightedStyle() {
        val single = word("fixture")
        composeRule.setContent {
            MIearnTheme {
                EnglishVariants(single, onPlayVariant = { _, _ -> })
            }
        }
        composeRule.onNode(hasContentDescription("播放 fixture")).assertIsDisplayed()
    }

    private fun word(english: String) = WordEntity(
        id = "test",
        category = "mechanical",
        categoryLabel = "机械专业词汇",
        sourceIndex = 1,
        kind = "TERM",
        section = "",
        english = english,
        primaryEnglish = english,
        phonetic = "",
        chinese = "测试",
        note = "",
        exampleEn = "",
        exampleZh = "",
        audioText = english,
        audioAsset = "",
    )
}
