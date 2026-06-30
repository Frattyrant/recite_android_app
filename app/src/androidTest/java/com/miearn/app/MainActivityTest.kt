package com.miearn.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun seededAppShowsMinimalThreeTabHome() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("MIearn").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("MIearn").assertIsDisplayed()
        composeRule.onNodeWithText("学习").assertIsDisplayed()
        composeRule.onNodeWithText("测试").assertIsDisplayed()
        composeRule.onNodeWithText("我的").assertIsDisplayed()
        composeRule.onNodeWithTag("primary-study-action").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("搜索全词库").assertIsDisplayed()
        composeRule.onNodeWithText("词库").assertDoesNotExist()
        composeRule.onNodeWithText("收藏").assertDoesNotExist()
        composeRule.onNodeWithText("错题").assertDoesNotExist()
        composeRule.onNodeWithText("数据").assertDoesNotExist()
    }

    @Test
    fun settingsOffersAllDailyGoals() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("MIearn").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithTag("daily-goal-ruler").assertIsDisplayed()
        composeRule.onNodeWithTag("daily-goal-20").assertIsDisplayed()
        composeRule.onNodeWithText("当前词库").assertDoesNotExist()
        composeRule.onNodeWithText("10:00").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("卡片自动发音").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("学习任务提醒").assertIsDisplayed()
    }

    @Test
    fun searchAndMineExposeMovedLibraryDestinations() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("MIearn").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("搜索全词库").performClick()
        composeRule.onNodeWithTag("global-search-field").performTextInput("fixture")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("fixture", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("收藏").assertIsDisplayed()
        composeRule.onNodeWithText("错题").assertIsDisplayed()
        composeRule.onNodeWithText("学习数据").assertIsDisplayed()
    }

    @Test
    fun learningInsightsExposeSevenAndThirtyDayViews() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("MIearn").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("数据").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("学习数据").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("✓ 7 天").assertIsDisplayed()
        composeRule.onNodeWithText("30 天").assertIsDisplayed()
        composeRule.onNodeWithText("首次正确率").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("预计保持率").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("未来 7 天待复习").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("近7天新学与复习趋势图")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("未来30天预计保持率曲线")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun newWordStartsWithBrowseInsteadOfSubjectiveRatings() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("MIearn").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("开始学习").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("第一遍 · 浏览").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("第一遍 · 浏览").assertIsDisplayed()
        composeRule.onNodeWithText("不认识").assertDoesNotExist()
        composeRule.onNodeWithText("模糊").assertDoesNotExist()
        composeRule.onNodeWithText("认识").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("浏览单词卡，点击展开释义")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription("播放发音").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("收藏").assertIsDisplayed()
    }
    @Test
    fun systemBackFromStudyReturnsToLearningHome() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("primary-study-action")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("primary-study-action").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("primary-study-action")
                .fetchSemanticsNodes()
                .isEmpty()
        }

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithTag("primary-study-action").assertIsDisplayed()
    }
}
