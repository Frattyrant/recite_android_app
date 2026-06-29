package com.miearn.app.domain

object LearningContentPolicy {
    const val missingChinese = "暂缺释义"

    fun displayChinese(chinese: String): String =
        chinese.trim().ifEmpty { missingChinese }

    fun hasChineseTranslation(chinese: String): Boolean = chinese.isNotBlank()

    fun canFillBlank(example: String, primaryEnglish: String): Boolean =
        primaryEnglish.isNotBlank() &&
            example.contains(primaryEnglish, ignoreCase = true)
}
