package com.miearn.app.domain

import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningInsightsTest {
    @Test
    fun retentionUsesElapsedOverInterval() {
        assertEquals(exp(-0.5), LearningInsights.retention(5, 10), 0.0001)
        assertEquals(1.0, LearningInsights.retention(0, 10), 0.0001)
    }

    @Test
    fun dailySeriesFillsMissingDaysWithZero() {
        val points = LearningInsights.dailySeries(
            events = listOf(DayCount(epochDay = 100, count = 3)),
            endDay = 102,
            days = 7,
        )

        assertEquals(7, points.size)
        assertEquals(3, points.first { it.epochDay == 100L }.count)
        assertEquals(0, points.first { it.epochDay == 101L }.count)
    }

    @Test
    fun firstTryAccuracyHandlesEmptyHistory() {
        assertEquals(0f, LearningInsights.accuracy(correct = 0, total = 0))
        assertEquals(0.75f, LearningInsights.accuracy(correct = 3, total = 4))
    }

    @Test
    fun averageRetentionHandlesEmptyAndClampsElapsed() {
        assertEquals(1.0, LearningInsights.averageRetention(emptyList(), today = 100), 0.0001)
        assertEquals(
            1.0,
            LearningInsights.averageRetention(
                listOf(RetentionInput(lastReviewedEpochDay = 101, intervalDays = 10)),
                today = 100,
            ),
            0.0001,
        )
    }

    @Test
    fun retentionCurveStartsAtCurrentAndDecays() {
        val inputs = listOf(RetentionInput(lastReviewedEpochDay = 95, intervalDays = 10))

        val curve = LearningInsights.retentionCurve(inputs, today = 100, horizonDays = 7)

        assertEquals(8, curve.size)
        assertEquals(LearningInsights.retention(5, 10), curve.first(), 0.0001)
        assertEquals(true, curve.last() < curve.first())
    }
}
