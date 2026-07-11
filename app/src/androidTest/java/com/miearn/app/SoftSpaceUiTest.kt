package com.miearn.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SoftSpaceUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mineShowsMonthlyCalendarWeeklySummaryAndDaySheet() {
        waitForHome()

        composeRule.onNodeWithText("我的").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("learning-calendar").fetchSemanticsNode()
            }.isSuccess
        }

        composeRule.onNodeWithTag("learning-calendar").assertIsDisplayed()
        composeRule.onNodeWithTag("mine-week-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-day-${LocalDate.now().toEpochDay()}").performClick()
        composeRule.onNodeWithText("这一天还没有学习记录").assertIsDisplayed()
    }

    @Test
    fun quizSetupKeepsFiveModesAndThumbReachStartAction() {
        waitForHome()

        composeRule.onNodeWithText("测试").performClick()

        composeRule.onNodeWithText("能力测试").assertIsDisplayed()
        composeRule.onNodeWithText("英选中").assertIsDisplayed()
        composeRule.onNodeWithText("中选英").assertIsDisplayed()
        composeRule.onNodeWithText("拼写").assertIsDisplayed()
        composeRule.onNodeWithText("听音选词").assertIsDisplayed()
        composeRule.onNodeWithText("例句填空").assertIsDisplayed()
        composeRule.onNodeWithTag("start-quiz-action").assertIsDisplayed()
    }

    private fun waitForHome() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("MIearn").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
