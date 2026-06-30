package com.miearn.app.ui

internal enum class StreakFlameLevel {
    NONE,
    SMALL,
    MEDIUM,
    LARGE;

    companion object {
        fun fromDays(days: Int): StreakFlameLevel = when {
            days <= 0 -> NONE
            days < 3 -> SMALL
            days < 7 -> MEDIUM
            else -> LARGE
        }
    }
}
