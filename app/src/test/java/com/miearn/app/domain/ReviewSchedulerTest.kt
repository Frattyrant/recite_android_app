package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    @Test
    fun firstCorrectSchedulesTomorrow() {
        val result = ReviewScheduler.grade(
            current = StudyProgress(),
            quality = 5,
            todayEpochDay = 100,
        )

        assertEquals(1, result.progress.repetitions)
        assertEquals(1, result.progress.intervalDays)
        assertEquals(101, result.progress.nextReviewEpochDay)
    }

    @Test
    fun secondCorrectSchedulesSixDays() {
        val current = StudyProgress(
            repetitions = 1,
            intervalDays = 1,
            easeFactor = 2.5,
        )

        val result = ReviewScheduler.grade(current, quality = 5, todayEpochDay = 101)

        assertEquals(2, result.progress.repetitions)
        assertEquals(6, result.progress.intervalDays)
        assertEquals(107, result.progress.nextReviewEpochDay)
    }

    @Test
    fun laterCorrectUsesUpdatedEaseAndRoundedPreviousInterval() {
        val current = StudyProgress(
            repetitions = 2,
            intervalDays = 6,
            easeFactor = 2.5,
        )

        val result = ReviewScheduler.grade(current, quality = 5, todayEpochDay = 107)

        assertEquals(3, result.progress.repetitions)
        assertEquals(2.6, result.progress.easeFactor, 0.0001)
        assertEquals(16, result.progress.intervalDays)
        assertEquals(123, result.progress.nextReviewEpochDay)
    }

    @Test
    fun wrongAnswerResetsRepetitionsAddsLapseAndClearsMastered() {
        val current = StudyProgress(
            repetitions = 4,
            intervalDays = 25,
            lapseCount = 2,
            mastered = true,
        )

        val result = ReviewScheduler.grade(current, quality = 2, todayEpochDay = 200)

        assertEquals(0, result.progress.repetitions)
        assertEquals(1, result.progress.intervalDays)
        assertEquals(3, result.progress.lapseCount)
        assertEquals(201, result.progress.nextReviewEpochDay)
        assertFalse(result.progress.mastered)
    }

    @Test
    fun easeFactorNeverFallsBelowOnePointThree() {
        var progress = StudyProgress(easeFactor = 1.3)

        repeat(10) { index ->
            progress = ReviewScheduler.grade(
                current = progress,
                quality = 0,
                todayEpochDay = 300L + index,
            ).progress
        }

        assertEquals(1.3, progress.easeFactor, 0.0001)
    }

    @Test
    fun qualityMustBeBetweenZeroAndFive() {
        assertThrows(IllegalArgumentException::class.java) {
            ReviewScheduler.grade(StudyProgress(), quality = -1, todayEpochDay = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReviewScheduler.grade(StudyProgress(), quality = 6, todayEpochDay = 100)
        }
    }

    @Test
    fun firstLearnedDateIsSetOnceAndReviewDateTracksToday() {
        val first = ReviewScheduler.grade(
            current = StudyProgress(),
            quality = 5,
            todayEpochDay = 400,
        ).progress

        val second = ReviewScheduler.grade(
            current = first,
            quality = 5,
            todayEpochDay = 407,
        ).progress

        assertEquals(400L, first.firstLearnedEpochDay)
        assertEquals(400L, second.firstLearnedEpochDay)
        assertEquals(407L, second.lastReviewedEpochDay)
        assertEquals(413L, second.nextReviewEpochDay)
    }

    @Test
    fun thirdSuccessIsNotMasteredWhenIntervalIsBelowTwentyOneDays() {
        val current = StudyProgress(
            repetitions = 2,
            intervalDays = 6,
            easeFactor = 2.5,
        )

        val result = ReviewScheduler.grade(current, quality = 5, todayEpochDay = 500)

        assertEquals(3, result.progress.repetitions)
        assertEquals(16, result.progress.intervalDays)
        assertFalse(result.progress.mastered)
    }

    @Test
    fun successMastersOnlyAtThirdRepetitionAndTwentyOneDayInterval() {
        val current = StudyProgress(
            repetitions = 2,
            intervalDays = 8,
            easeFactor = 2.5,
        )

        val result = ReviewScheduler.grade(current, quality = 5, todayEpochDay = 500)

        assertEquals(3, result.progress.repetitions)
        assertEquals(21, result.progress.intervalDays)
        assertTrue(result.progress.mastered)
    }
}
