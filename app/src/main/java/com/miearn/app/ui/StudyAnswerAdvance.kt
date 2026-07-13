package com.miearn.app.ui

import com.miearn.app.domain.LearningSession
import kotlinx.coroutines.delay

object StudyAnswerAdvance {
    const val FEEDBACK_WINDOW_MILLIS = 320L

    suspend fun afterFeedback(answered: LearningSession): LearningSession {
        require(answered.pendingFirstCorrect != null) {
            "study answer must be recorded before automatic advancement"
        }
        delay(FEEDBACK_WINDOW_MILLIS)
        return answered.continueAfterAnswer()
    }
}
