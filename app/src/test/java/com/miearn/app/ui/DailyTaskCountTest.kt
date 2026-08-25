package com.miearn.app.ui

import com.miearn.app.domain.LearningPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTaskCountTest {
    @Test
    fun remainingNewCountSubtractsWordsLearnedToday() {
        assertEquals(15, remainingDailyNewCount(dailyGoal = 20, learnedToday = 5, unseen = 100))
        assertEquals(0, remainingDailyNewCount(dailyGoal = 20, learnedToday = 20, unseen = 100))
        assertEquals(3, remainingDailyNewCount(dailyGoal = 20, learnedToday = 5, unseen = 3))
    }

    @Test
    fun unfinishedSessionKeepsItsRemainingTaskVisibleOnHome() {
        assertEquals(
            DailyTaskCounts(newCount = 0, reviewCount = 3),
            remainingSessionTaskCounts(LearningPhase.REVIEW, index = 2, phaseTotal = 5),
        )
        assertEquals(
            DailyTaskCounts(newCount = 4, reviewCount = 0),
            remainingSessionTaskCounts(LearningPhase.CONSOLIDATE, index = 1, phaseTotal = 5),
        )
        assertEquals(
            DailyTaskCounts(newCount = 0, reviewCount = 2),
            remainingSessionTaskCounts(LearningPhase.REINFORCEMENT, index = 0, phaseTotal = 2),
        )
    }
}
