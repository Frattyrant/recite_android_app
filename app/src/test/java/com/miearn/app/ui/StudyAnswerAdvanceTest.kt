package com.miearn.app.ui

import com.miearn.app.domain.LearningPhase
import com.miearn.app.domain.LearningSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudyAnswerAdvanceTest {
    @Test
    fun answeredCardAdvancesAfterOneBriefFeedbackWindow() = runTest {
        val answered = LearningSession.start(
            reviewIds = emptyList(),
            newIds = listOf("first", "second"),
        ).nextBrowse().nextBrowse().submitAnswer(firstCorrect = true)

        val deferred = async { StudyAnswerAdvance.afterFeedback(answered) }
        advanceTimeBy(StudyAnswerAdvance.FEEDBACK_WINDOW_MILLIS - 1)
        assertFalse(deferred.isCompleted)

        advanceTimeBy(1)
        val next = deferred.await()
        assertEquals("second", next.currentId)
        assertEquals(LearningPhase.CONSOLIDATE, next.phase)
    }
}
