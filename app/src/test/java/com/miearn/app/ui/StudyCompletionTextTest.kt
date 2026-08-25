package com.miearn.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyCompletionTextTest {
    @Test
    fun emptyCompletionExplainsThereIsNoWorkWithoutCallingItACompletedSession() {
        assertEquals("今天没有待学词", studyCompletionTitle(0, 0))
        assertEquals(
            "当前词库的新词和到期复习都已完成，明天再来即可。",
            studyCompletionDescription(0, 0),
        )
    }

    @Test
    fun completedSessionKeepsThePositiveSummary() {
        assertEquals("今日任务完成", studyCompletionTitle(1, 0))
        assertEquals("", studyCompletionDescription(1, 0))
    }
}
