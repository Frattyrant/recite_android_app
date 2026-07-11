package com.miearn.app.domain

import java.time.LocalDate
import java.time.YearMonth

enum class CalendarIntensity {
    NONE,
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromTotal(total: Int): CalendarIntensity = when {
            total <= 0 -> NONE
            total < 10 -> LOW
            total < 30 -> MEDIUM
            else -> HIGH
        }
    }
}

data class CalendarDaySummary(
    val epochDay: Long,
    val newCount: Int,
    val reviewCount: Int,
    val correctFirstTry: Int,
    val answeredFirstTry: Int,
) {
    val totalCount: Int
        get() = newCount + reviewCount

    val firstTryAccuracy: Float?
        get() = answeredFirstTry
            .takeIf { it > 0 }
            ?.let { correctFirstTry.toFloat() / it }
}

data class WeeklyLearningSummary(
    val totalCount: Int,
    val correctFirstTry: Int,
    val answeredFirstTry: Int,
    val streak: Int,
) {
    val firstTryAccuracy: Float?
        get() = answeredFirstTry
            .takeIf { it > 0 }
            ?.let { correctFirstTry.toFloat() / it }
}

data class CalendarDayUi(
    val date: LocalDate?,
    val summary: CalendarDaySummary?,
    val intensity: CalendarIntensity,
    val isFuture: Boolean,
)

data class CalendarMonthUi(
    val month: YearMonth,
    val days: List<CalendarDayUi>,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
)

object LearningCalendar {
    fun month(
        month: YearMonth,
        today: LocalDate,
        earliestEpochDay: Long?,
        summaries: List<CalendarDaySummary>,
    ): CalendarMonthUi {
        val summaryByDay = summaries.associateBy(CalendarDaySummary::epochDay)
        val firstDate = month.atDay(1)
        val leadingEmptyDays = firstDate.dayOfWeek.value - 1
        val days = buildList {
            repeat(leadingEmptyDays) { add(emptyDay()) }
            repeat(month.lengthOfMonth()) { offset ->
                val date = firstDate.plusDays(offset.toLong())
                val summary = summaryByDay[date.toEpochDay()]
                add(
                    CalendarDayUi(
                        date = date,
                        summary = summary,
                        intensity = CalendarIntensity.fromTotal(summary?.totalCount ?: 0),
                        isFuture = date > today,
                    ),
                )
            }
            while (size % 7 != 0) add(emptyDay())
        }
        val currentMonth = YearMonth.from(today)
        val earliestMonth = earliestEpochDay
            ?.let(LocalDate::ofEpochDay)
            ?.let(YearMonth::from)
            ?: currentMonth
        return CalendarMonthUi(
            month = month,
            days = days,
            canGoPrevious = month > earliestMonth,
            canGoNext = month < currentMonth,
        )
    }

    fun weekly(
        today: LocalDate,
        summaries: List<CalendarDaySummary>,
        streak: Int,
    ): WeeklyLearningSummary {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong()).toEpochDay()
        val included = summaries.filter { it.epochDay in monday..today.toEpochDay() }
        return WeeklyLearningSummary(
            totalCount = included.sumOf(CalendarDaySummary::totalCount),
            correctFirstTry = included.sumOf(CalendarDaySummary::correctFirstTry),
            answeredFirstTry = included.sumOf(CalendarDaySummary::answeredFirstTry),
            streak = streak,
        )
    }

    private fun emptyDay() = CalendarDayUi(
        date = null,
        summary = null,
        intensity = CalendarIntensity.NONE,
        isFuture = false,
    )
}
