package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LearningCalendarTest {
    @Test
    fun monthGridStartsOnMondayAndIncludesLeapDay() {
        val today = LocalDate.of(2026, 7, 10)
        val month = YearMonth.of(2024, 2)

        val result = LearningCalendar.month(
            month = month,
            today = today,
            earliestEpochDay = LocalDate.of(2024, 2, 3).toEpochDay(),
            summaries = emptyList(),
        )

        assertEquals(0, result.days.first().date?.dayOfWeek?.value?.minus(1) ?: 0)
        assertEquals(LocalDate.of(2024, 2, 1), result.days.first { it.date != null }.date)
        assertTrue(result.days.any { it.date == LocalDate.of(2024, 2, 29) })
        assertEquals(0, result.days.size % 7)
    }

    @Test
    fun monthNavigationStopsAtEarliestActivityAndCurrentMonth() {
        val today = LocalDate.of(2026, 7, 10)
        val earliest = LocalDate.of(2026, 5, 21).toEpochDay()

        val earliestMonth = LearningCalendar.month(
            month = YearMonth.of(2026, 5),
            today = today,
            earliestEpochDay = earliest,
            summaries = emptyList(),
        )
        val currentMonth = LearningCalendar.month(
            month = YearMonth.of(2026, 7),
            today = today,
            earliestEpochDay = earliest,
            summaries = emptyList(),
        )

        assertFalse(earliestMonth.canGoPrevious)
        assertTrue(earliestMonth.canGoNext)
        assertTrue(currentMonth.canGoPrevious)
        assertFalse(currentMonth.canGoNext)
    }

    @Test
    fun heatLevelUsesStableDailyThresholds() {
        assertEquals(CalendarIntensity.NONE, CalendarIntensity.fromTotal(0))
        assertEquals(CalendarIntensity.LOW, CalendarIntensity.fromTotal(1))
        assertEquals(CalendarIntensity.LOW, CalendarIntensity.fromTotal(9))
        assertEquals(CalendarIntensity.MEDIUM, CalendarIntensity.fromTotal(10))
        assertEquals(CalendarIntensity.MEDIUM, CalendarIntensity.fromTotal(29))
        assertEquals(CalendarIntensity.HIGH, CalendarIntensity.fromTotal(30))
    }

    @Test
    fun weeklySummaryUsesMondayThroughTodayAcrossMonthBoundary() {
        val today = LocalDate.of(2026, 7, 1)
        val included = listOf(
            summary(LocalDate.of(2026, 6, 29), newCount = 3, reviewCount = 2, correct = 4, answered = 5),
            summary(LocalDate.of(2026, 6, 30), newCount = 1, reviewCount = 4, correct = 3, answered = 5),
            summary(LocalDate.of(2026, 7, 1), newCount = 5, reviewCount = 0, correct = 0, answered = 0),
            summary(LocalDate.of(2026, 6, 28), newCount = 99, reviewCount = 99, correct = 1, answered = 1),
        )

        val result = LearningCalendar.weekly(
            today = today,
            summaries = included,
            streak = 3,
        )

        assertEquals(15, result.totalCount)
        assertEquals(7, result.correctFirstTry)
        assertEquals(10, result.answeredFirstTry)
        assertEquals(0.7f, result.firstTryAccuracy!!, 0.0001f)
        assertEquals(3, result.streak)
    }

    @Test
    fun weeklySummaryHasNoAccuracyWithoutAnswers() {
        val result = LearningCalendar.weekly(
            today = LocalDate.of(2026, 7, 10),
            summaries = listOf(
                summary(LocalDate.of(2026, 7, 10), newCount = 4),
            ),
            streak = 1,
        )

        assertEquals(4, result.totalCount)
        assertNull(result.firstTryAccuracy)
    }

    private fun summary(
        date: LocalDate,
        newCount: Int = 0,
        reviewCount: Int = 0,
        correct: Int = 0,
        answered: Int = 0,
    ) = CalendarDaySummary(
        epochDay = date.toEpochDay(),
        newCount = newCount,
        reviewCount = reviewCount,
        correctFirstTry = correct,
        answeredFirstTry = answered,
    )
}
