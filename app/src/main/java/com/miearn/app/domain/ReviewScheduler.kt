package com.miearn.app.domain

import kotlin.math.roundToInt

object ReviewScheduler {
    fun grade(
        current: StudyProgress,
        quality: Int,
        todayEpochDay: Long,
    ): ReviewOutcome {
        require(quality in 0..5) { "quality must be between 0 and 5" }

        val qualityGap = 5 - quality
        val nextEase = maxOf(
            1.3,
            current.easeFactor + 0.1 -
                qualityGap * (0.08 + qualityGap * 0.02),
        )
        val nextRepetitions = if (quality < 3) {
            0
        } else {
            current.repetitions + 1
        }
        val nextInterval = when {
            quality < 3 -> 1
            nextRepetitions == 1 -> 1
            nextRepetitions == 2 -> 6
            else -> maxOf(1, (current.intervalDays * nextEase).roundToInt())
        }

        return ReviewOutcome(
            progress = current.copy(
                easeFactor = nextEase,
                intervalDays = nextInterval,
                repetitions = nextRepetitions,
                lapseCount = current.lapseCount + if (quality < 3) 1 else 0,
                nextReviewEpochDay = todayEpochDay + nextInterval,
                lastReviewedEpochDay = todayEpochDay,
                firstLearnedEpochDay = current.firstLearnedEpochDay ?: todayEpochDay,
                mastered = quality >= 3 &&
                    nextRepetitions >= 3 &&
                    nextInterval >= 21,
            ),
        )
    }
}
