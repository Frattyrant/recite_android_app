package com.miearn.app.ui

internal fun hasStudyCompletionActivity(newCount: Int, reviewCount: Int): Boolean =
    newCount > 0 || reviewCount > 0

internal fun studyCompletionTitle(newCount: Int, reviewCount: Int): String =
    if (hasStudyCompletionActivity(newCount, reviewCount)) "今日任务完成" else "今天没有待学词"

internal fun studyCompletionDescription(newCount: Int, reviewCount: Int): String =
    if (hasStudyCompletionActivity(newCount, reviewCount)) {
        ""
    } else {
        "当前词库的新词和到期复习都已完成，明天再来即可。"
    }
