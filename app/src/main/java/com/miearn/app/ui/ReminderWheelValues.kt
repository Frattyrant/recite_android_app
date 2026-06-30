package com.miearn.app.ui

internal object ReminderWheelValues {
    val hours: List<Int> = (0..23).toList()
    val minutes: List<Int> = (0..59).toList()

    fun label(value: Int): String = value.toString().padStart(2, '0')
}
