package com.miearn.app.ui

import com.miearn.app.audio.PronunciationStatus
import com.miearn.app.data.InsightsSnapshot
import com.miearn.app.data.local.CategoryStats
import com.miearn.app.data.local.WordEntity
import com.miearn.app.data.settings.UserSettings
import com.miearn.app.domain.LearningPhase

enum class MainTab(val label: String) {
    LEARNING("学习"),
    QUIZ("测试"),
    MINE("我的"),
}

enum class WordListMode(val label: String) {
    ALL("全部"),
    FAVORITES("收藏"),
    WRONG("错题"),
    MASTERED("已掌握"),
}

enum class WordBrowserDestination(val title: String) {
    SEARCH("搜索"),
    FAVORITES("收藏"),
    WRONG("错题"),
}

enum class QuizMode(val label: String, val description: String) {
    EN_TO_ZH("英选中", "看英文选择中文"),
    ZH_TO_EN("中选英", "看中文选择英文"),
    SPELLING("拼写", "输入正确英文"),
    LISTENING("听音选词", "听发音选择单词"),
    FILL_BLANK("例句填空", "补全专业例句"),
}

sealed interface SeedUiState {
    data object Loading : SeedUiState
    data class Ready(val total: Int) : SeedUiState
    data class Error(val message: String) : SeedUiState
}

data class DashboardUiState(
    val settings: UserSettings = UserSettings(),
    val categories: List<CategoryStats> = emptyList(),
    val todayNew: Int = 0,
    val todayReview: Int = 0,
    val mastered: Int = 0,
    val streak: Int = 0,
) {
    val activeStats: CategoryStats?
        get() = categories.firstOrNull { it.category == settings.activeCategory }
}

sealed interface StudyUiState {
    data object Idle : StudyUiState
    data object Loading : StudyUiState
    data class ResumePrompt(
        val savedEpochDay: Long,
        val category: String,
    ) : StudyUiState

    data class Active(
        val phase: LearningPhase,
        val word: WordEntity,
        val index: Int,
        val total: Int,
        val expanded: Boolean,
        val options: List<String>,
        val selectedAnswer: String? = null,
        val firstCorrect: Boolean? = null,
        val pronunciationStatus: PronunciationStatus = PronunciationStatus.INITIALIZING,
        val audioHelpVisible: Boolean = false,
    ) : StudyUiState

    data class Complete(
        val newCount: Int,
        val reviewCount: Int,
        val correctFirstTry: Int,
        val answeredFirstTry: Int,
    ) : StudyUiState
}

sealed interface InsightsUiState {
    data object Loading : InsightsUiState
    data class Ready(val snapshot: InsightsSnapshot) : InsightsUiState
    data class Error(val message: String) : InsightsUiState
}

data class QuizQuestion(
    val word: WordEntity,
    val mode: QuizMode,
    val prompt: String,
    val expected: String,
    val options: List<String>,
)

sealed interface QuizUiState {
    data object Setup : QuizUiState
    data object Loading : QuizUiState
    data class Active(
        val question: QuizQuestion,
        val index: Int,
        val total: Int,
        val correct: Int,
        val feedbackCorrect: Boolean? = null,
        val submittedAnswer: String = "",
    ) : QuizUiState

    data class Complete(
        val correct: Int,
        val total: Int,
        val wrongWordIds: List<String>,
    ) : QuizUiState
}
