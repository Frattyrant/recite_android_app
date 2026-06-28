package com.miearn.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationAndSettingsModelTest {
    @Test
    fun bottomNavigationHasLearningQuizAndMineOnly() {
        assertEquals(
            listOf("学习", "测试", "我的"),
            MainTab.entries.map(MainTab::label),
        )
    }

    @Test
    fun dailyGoalScaleRunsFromFiveToTwoHundredInFiveWordSteps() {
        assertEquals(5, DailyGoalScale.values.first())
        assertEquals(200, DailyGoalScale.values.last())
        assertEquals(40, DailyGoalScale.values.size)
        assertEquals(5, DailyGoalScale.snap(7))
        assertEquals(10, DailyGoalScale.snap(8))
        assertEquals(200, DailyGoalScale.snap(205))
    }
}
