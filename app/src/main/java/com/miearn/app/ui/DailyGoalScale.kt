package com.miearn.app.ui

import kotlin.math.roundToInt

object DailyGoalScale {
    const val minimum = 5
    const val maximum = 200
    const val step = 5

    val values: List<Int> = (minimum..maximum step step).toList()

    fun snap(value: Int): Int =
        ((value.coerceIn(minimum, maximum).toFloat() / step).roundToInt() * step)
            .coerceIn(minimum, maximum)
}
