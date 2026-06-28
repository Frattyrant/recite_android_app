package com.miearn.app.domain

data class StudyProgress(
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapseCount: Int = 0,
    val nextReviewEpochDay: Long = 0,
    val lastReviewedEpochDay: Long? = null,
    val firstLearnedEpochDay: Long? = null,
    val mastered: Boolean = false,
)

data class ReviewOutcome(
    val progress: StudyProgress,
)
