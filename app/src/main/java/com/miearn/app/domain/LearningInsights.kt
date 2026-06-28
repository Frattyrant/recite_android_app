package com.miearn.app.domain

import kotlin.math.exp

data class DayCount(
    val epochDay: Long,
    val count: Int,
)

data class RetentionInput(
    val lastReviewedEpochDay: Long,
    val intervalDays: Int,
)

object LearningInsights {
    fun retention(elapsedDays: Int, intervalDays: Int): Double =
        exp(-elapsedDays.coerceAtLeast(0).toDouble() / maxOf(1, intervalDays))

    fun accuracy(correct: Int, total: Int): Float =
        if (total <= 0) 0f else correct.coerceIn(0, total).toFloat() / total

    fun dailySeries(
        events: List<DayCount>,
        endDay: Long,
        days: Int,
    ): List<DayCount> {
        require(days > 0)
        val counts = events.groupBy(DayCount::epochDay)
            .mapValues { (_, points) -> points.sumOf(DayCount::count) }
        return ((endDay - days + 1)..endDay).map { day ->
            DayCount(day, counts[day] ?: 0)
        }
    }

    fun averageRetention(
        inputs: List<RetentionInput>,
        today: Long,
    ): Double {
        if (inputs.isEmpty()) return 1.0
        return inputs.map {
            retention(
                elapsedDays = (today - it.lastReviewedEpochDay)
                    .coerceAtLeast(0)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt(),
                intervalDays = it.intervalDays,
            )
        }.average()
    }

    fun retentionCurve(
        inputs: List<RetentionInput>,
        today: Long,
        horizonDays: Int,
    ): List<Double> {
        require(horizonDays >= 0)
        if (inputs.isEmpty()) return List(horizonDays + 1) { 1.0 }
        return (0..horizonDays).map { horizon ->
            inputs.map {
                val elapsed = (today - it.lastReviewedEpochDay)
                    .coerceAtLeast(0) + horizon
                retention(
                    elapsedDays = elapsed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    intervalDays = it.intervalDays,
                )
            }.average()
        }
    }
}
