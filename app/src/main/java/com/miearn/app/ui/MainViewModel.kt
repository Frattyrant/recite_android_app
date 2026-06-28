package com.miearn.app.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miearn.app.MIearnApplication
import com.miearn.app.audio.AnswerFeedback
import com.miearn.app.audio.PronunciationStatus
import com.miearn.app.audio.TtsInstallIntent
import com.miearn.app.data.MIearnRepository
import com.miearn.app.data.SavedLearningSession
import com.miearn.app.data.local.WordEntity
import com.miearn.app.data.settings.UserSettings
import com.miearn.app.domain.LearningPhase
import com.miearn.app.domain.LearningSession
import com.miearn.app.domain.QuizEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MIearnApplication).container
    private val repository = container.repository
    private val settingsRepository = container.settings
    private val launchEpochDay = MIearnRepository.todayEpochDay()

    val seedState = MutableStateFlow<SeedUiState>(SeedUiState.Loading)
    val selectedTab = MutableStateFlow(MainTab.LEARNING)
    val showSettings = MutableStateFlow(false)
    val showReminderPrompt = MutableStateFlow(false)
    val showInsights = MutableStateFlow(false)
    val insightsState = MutableStateFlow<InsightsUiState>(InsightsUiState.Loading)

    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings(),
    )

    private val activeCounts = settings.flatMapLatest { selected ->
        combine(
            repository.dueCount(selected.activeCategory, MIearnRepository.todayEpochDay()),
            repository.unseenCount(selected.activeCategory),
        ) { due, unseen -> due to minOf(unseen, selected.dailyGoal) }
    }

    val dashboard: StateFlow<DashboardUiState> = combine(
        settings,
        repository.categoryStats,
        repository.activities,
        repository.masteredCount,
        activeCounts,
    ) { selected, categories, activities, mastered, counts ->
        DashboardUiState(
            settings = selected,
            categories = categories,
            todayNew = counts.second,
            todayReview = counts.first,
            mastered = mastered,
            streak = MIearnRepository.calculateStreak(
                activities,
                MIearnRepository.todayEpochDay(),
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState(),
    )

    val wordBrowserDestination = MutableStateFlow<WordBrowserDestination?>(null)
    val wordBrowserQuery = MutableStateFlow("")

    val wordBrowserWords = combine(
        wordBrowserDestination,
        wordBrowserQuery,
    ) { destination, query -> destination to query.trim() }
        .flatMapLatest { (destination, query) ->
            val source = when (destination) {
                WordBrowserDestination.SEARCH -> repository.searchAll(query)
                WordBrowserDestination.FAVORITES -> repository.favorites()
                WordBrowserDestination.WRONG -> repository.wrongWords()
                null -> repository.searchAll("")
            }
            source.map { words ->
                if (destination == WordBrowserDestination.SEARCH || query.isBlank()) {
                    words
                } else {
                    words.filter {
                        it.english.contains(query, ignoreCase = true) ||
                            it.chinese.contains(query)
                    }
                }
            }
        }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private var learningSession: LearningSession? = null
    private var sessionWords: Map<String, WordEntity> = emptyMap()
    private var sessionChoicePool: List<WordEntity> = emptyList()
    private var activeSessionEpochDay: Long = launchEpochDay
    private var activeSessionCategory: String = ""
    private var pendingResume: SavedLearningSession? = null
    private var currentWordShownAtMillis: Long = 0
    private var audioHelpLatched = false
    val studyState = MutableStateFlow<StudyUiState>(StudyUiState.Idle)

    private var quizPool: List<WordEntity> = emptyList()
    private var quizItems: List<WordEntity> = emptyList()
    private var quizIndex = 0
    private var quizCorrect = 0
    private val quizWrongIds = mutableListOf<String>()
    private var quizMode = QuizMode.EN_TO_ZH
    val quizState = MutableStateFlow<QuizUiState>(QuizUiState.Setup)

    init {
        retrySeed()
        viewModelScope.launch {
            settings.collect { selected ->
                if (
                    selected.lastCompletedEpochDay != null &&
                    !selected.reminderPromptShown
                ) {
                    showReminderPrompt.value = true
                }
            }
        }
        viewModelScope.launch {
            container.audio.status.collect { pronunciationStatus ->
                val active = studyState.value as? StudyUiState.Active ?: return@collect
                if (pronunciationStatus == PronunciationStatus.UNAVAILABLE) {
                    audioHelpLatched = true
                }
                studyState.value = active.copy(pronunciationStatus = pronunciationStatus, audioHelpVisible = audioHelpLatched)
            }
        }
    }

    fun retrySeed() {
        viewModelScope.launch {
            seedState.value = SeedUiState.Loading
            seedState.value = runCatching { container.seeder.ensureSeeded() }
                .fold(
                    onSuccess = { SeedUiState.Ready(it) },
                    onFailure = { SeedUiState.Error(it.message ?: "词库导入失败") },
                )
        }
    }

    fun selectTab(tab: MainTab) {
        container.audio.stop()
        selectedTab.value = tab
    }

    fun stopAudio() {
        container.audio.stop()
    }

    fun selectActiveCategory(category: String) {
        viewModelScope.launch {
            settingsRepository.setActiveCategory(category)
        }
    }

    fun openWordBrowser(destination: WordBrowserDestination) {
        wordBrowserQuery.value = ""
        wordBrowserDestination.value = destination
    }

    fun closeWordBrowser() {
        container.audio.stop()
        wordBrowserDestination.value = null
        wordBrowserQuery.value = ""
    }

    fun setDailyGoal(goal: Int) {
        viewModelScope.launch { settingsRepository.setDailyGoal(goal) }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoPlay(enabled) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            container.reminderScheduler.apply(
                settings.value.copy(reminderEnabled = enabled),
            )
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(hour, minute)
            container.reminderScheduler.apply(
                settings.value.copy(
                    reminderHour = hour,
                    reminderMinute = minute,
                ),
            )
        }
    }

    fun resolveReminderPrompt(enable: Boolean) {
        showReminderPrompt.value = false
        viewModelScope.launch {
            settingsRepository.markReminderPromptShown()
            if (enable) {
                settingsRepository.setReminderEnabled(true)
                container.reminderScheduler.apply(
                    settings.value.copy(reminderEnabled = true),
                )
            }
        }
    }

    fun openInsights() {
        showInsights.value = true
        refreshInsights()
    }

    fun closeInsights() {
        showInsights.value = false
    }

    fun refreshInsights() {
        viewModelScope.launch {
            insightsState.value = InsightsUiState.Loading
            insightsState.value = runCatching {
                repository.insightsSnapshot(MIearnRepository.todayEpochDay())
            }.fold(
                onSuccess = { InsightsUiState.Ready(it) },
                onFailure = { InsightsUiState.Error(it.message ?: "学习数据读取失败") },
            )
        }
    }

    fun toggleFavorite(wordId: String) {
        viewModelScope.launch { repository.toggleFavorite(wordId) }
    }

    fun pronounce(word: WordEntity) {
        container.audio.play(word)
    }

    fun pronounceVariant(word: WordEntity, index: Int) {
        container.audio.playVariant(word, index)
    }

    fun startStudy() {
        audioHelpLatched = false
        viewModelScope.launch {
            studyState.value = StudyUiState.Loading
            val selected = settings.value
            val saved = repository.loadSavedSessionRecord()
            if (saved != null && !saved.session.isComplete) {
                if (
                    saved.epochDay == MIearnRepository.todayEpochDay() &&
                    saved.category == selected.activeCategory
                ) {
                    activateSession(saved)
                } else {
                    pendingResume = saved
                    studyState.value = StudyUiState.ResumePrompt(
                        savedEpochDay = saved.epochDay,
                        category = saved.category,
                    )
                }
            } else {
                beginNewSession(selected)
            }
        }
    }

    fun resolveSavedStudy(continueSaved: Boolean) {
        viewModelScope.launch {
            val saved = pendingResume
            pendingResume = null
            if (continueSaved && saved != null) {
                activateSession(saved)
            } else {
                repository.clearSavedSession()
                beginNewSession(settings.value)
            }
        }
    }

    fun answerStudy(answer: String) {
        val session = learningSession ?: return
        val active = studyState.value as? StudyUiState.Active ?: return
        if (session.pendingFirstCorrect != null || active.options.isEmpty()) return
        val firstCorrect = answer == active.word.chinese
        container.audio.stop()
        container.answerFeedback.play(if (firstCorrect) AnswerFeedback.CORRECT else AnswerFeedback.WRONG)
        val answered = session.submitAnswer(firstCorrect)
        val responseMillis = (System.currentTimeMillis() - currentWordShownAtMillis)
            .coerceAtLeast(0)
        viewModelScope.launch {
            if (
                session.phase == LearningPhase.REVIEW ||
                session.phase == LearningPhase.CONSOLIDATE
            ) {
                repository.recordFirstAnswer(
                    word = active.word,
                    phase = session.phase,
                    firstCorrect = firstCorrect,
                    responseMillis = responseMillis,
                    today = activeSessionEpochDay,
                    session = answered,
                )
            } else {
                repository.recordReinforcementAnswer(answered)
            }
            learningSession = answered
            renderStudy(autoplay = false, selectedAnswer = answer)
        }
    }

    fun continueStudy() {
        val session = learningSession ?: return
        if (session.pendingFirstCorrect == null) return
        viewModelScope.launch {
            val next = session.continueAfterAnswer()
            learningSession = next
            if (next.isComplete) {
                finishStudy(next)
            } else {
                repository.saveSession(
                    next,
                    activeSessionEpochDay,
                    activeSessionCategory,
                )
                renderStudy(autoplay = true)
            }
        }
    }

    fun nextBrowseWord() {
        updateBrowseSession { it.nextBrowse() }
    }

    fun previousBrowseWord() {
        updateBrowseSession { it.previousBrowse() }
    }

    fun browseToWord(index: Int) {
        updateBrowseSession { it.browseTo(index) }
    }

    fun toggleStudyCard() {
        updateBrowseSession(autoplay = false) { it.toggleCardExpanded() }
    }

    fun closeStudy() {
        container.audio.stop()
        studyState.value = StudyUiState.Idle
    }

    fun openTtsSettings() {
        runCatching {
            getApplication<Application>().startActivity(TtsInstallIntent.create())
        }.recoverCatching { error ->
            if (error !is ActivityNotFoundException) throw error
            getApplication<Application>().startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private suspend fun beginNewSession(selected: UserSettings) {
        activeSessionEpochDay = MIearnRepository.todayEpochDay()
        activeSessionCategory = selected.activeCategory
        val session = repository.createSession(
            selected.activeCategory,
            selected.dailyGoal,
            activeSessionEpochDay,
        )
        activateSession(
            SavedLearningSession(
                epochDay = activeSessionEpochDay,
                category = activeSessionCategory,
                session = session,
            ),
        )
    }

    private suspend fun activateSession(saved: SavedLearningSession) {
        activeSessionEpochDay = saved.epochDay
        activeSessionCategory = saved.category
        learningSession = saved.session
        val allIds = (
            saved.session.reviewIds +
                saved.session.newIds +
                saved.session.reinforcementIds
            ).distinct()
        sessionWords = repository.wordsByIds(allIds)
        sessionChoicePool = repository.quizWords(
            saved.category,
            learnedOnly = false,
            limit = 300,
        )
        if (saved.session.isComplete) {
            finishStudy(saved.session)
        } else {
            renderStudy(autoplay = true)
        }
    }

    private fun updateBrowseSession(
        autoplay: Boolean = true,
        transform: (LearningSession) -> LearningSession,
    ) {
        viewModelScope.launch {
            val current = learningSession ?: return@launch
            if (current.phase != LearningPhase.BROWSE) return@launch
            val next = transform(current)
            if (next == current) return@launch
            learningSession = next
            repository.saveSession(
                next,
                activeSessionEpochDay,
                activeSessionCategory,
            )
            renderStudy(autoplay = autoplay)
        }
    }

    private suspend fun renderStudy(
        autoplay: Boolean,
        selectedAnswer: String? = null,
    ) {
        val session = learningSession ?: return
        val word = session.currentId?.let(sessionWords::get)
        if (session.isComplete || word == null) {
            finishStudy(session)
            return
        }
        val options = if (session.phase == LearningPhase.BROWSE) {
            emptyList()
        } else {
            QuizEngine.chineseOptions(
                answer = word.chinese,
                candidates = sessionChoicePool.map(WordEntity::chinese),
                seed = word.id.hashCode() xor session.phase.ordinal,
            )
        }
        studyState.value = StudyUiState.Active(
            phase = session.phase,
            word = word,
            index = session.index,
            total = session.phaseTotal,
            expanded = session.cardExpanded,
            options = options,
            selectedAnswer = selectedAnswer,
            firstCorrect = session.pendingFirstCorrect,
            pronunciationStatus = container.audio.status.value,
            audioHelpVisible = audioHelpLatched,
        )
        currentWordShownAtMillis = System.currentTimeMillis()
        if (autoplay && settings.value.autoPlay) pronounce(word)
    }

    private suspend fun finishStudy(session: LearningSession) {
        repository.clearSavedSession()
        settingsRepository.setLastCompletedEpochDay(activeSessionEpochDay)
        if (!settings.value.reminderPromptShown) {
            showReminderPrompt.value = true
        }
        learningSession = null
        studyState.value = StudyUiState.Complete(
            newCount = session.completedNew,
            reviewCount = session.completedReview,
            correctFirstTry = session.correctFirstTry,
            answeredFirstTry = session.answeredFirstTry,
        )
    }

    fun startQuiz(mode: QuizMode, count: Int, learnedOnly: Boolean) {
        viewModelScope.launch {
            quizState.value = QuizUiState.Loading
            quizMode = mode
            val requestedPool = repository.quizWords(
                settings.value.activeCategory,
                learnedOnly,
                maxOf(count, 30),
            )
            quizPool = requestedPool
            quizItems = quizPool.shuffled().take(count)
            quizIndex = 0
            quizCorrect = 0
            quizWrongIds.clear()
            showQuizQuestion()
        }
    }

    fun submitQuiz(answer: String) {
        val active = quizState.value as? QuizUiState.Active ?: return
        if (active.feedbackCorrect != null) return
        val isCorrect = when (active.question.mode) {
            QuizMode.SPELLING, QuizMode.FILL_BLANK ->
                QuizEngine.isSpellingCorrect(answer, active.question.expected)
            else -> answer == active.question.expected
        }
        container.audio.stop()
        container.answerFeedback.play(if (isCorrect) AnswerFeedback.CORRECT else AnswerFeedback.WRONG)
        if (isCorrect) {
            quizCorrect += 1
        } else {
            quizWrongIds += active.question.word.id
            viewModelScope.launch { repository.markWrong(active.question.word.id) }
        }
        quizState.value = active.copy(
            correct = quizCorrect,
            feedbackCorrect = isCorrect,
            submittedAnswer = answer,
        )
    }

    fun nextQuizQuestion() {
        quizIndex += 1
        showQuizQuestion()
    }

    fun resetQuiz() {
        container.audio.stop()
        quizState.value = QuizUiState.Setup
    }

    fun retryWrongQuiz() {
        val retryIds = quizWrongIds.distinct()
        if (retryIds.isEmpty()) {
            resetQuiz()
            return
        }
        val byId = quizPool.associateBy(WordEntity::id)
        quizItems = retryIds.mapNotNull(byId::get)
        quizIndex = 0
        quizCorrect = 0
        quizWrongIds.clear()
        showQuizQuestion()
    }

    private fun showQuizQuestion() {
        if (quizIndex >= quizItems.size) {
            quizState.value = QuizUiState.Complete(
                correct = quizCorrect,
                total = quizItems.size,
                wrongWordIds = quizWrongIds.toList(),
            )
            return
        }
        val word = quizItems[quizIndex]
        val distractors = quizPool.filter { it.id != word.id }.shuffled()
        val question = when (quizMode) {
            QuizMode.EN_TO_ZH -> QuizQuestion(
                word,
                quizMode,
                word.english,
                word.chinese,
                QuizEngine.choiceOptions(
                    answer = word.chinese,
                    candidates = distractors.map(WordEntity::chinese),
                    seed = word.id.hashCode() xor quizIndex,
                ),
            )
            QuizMode.ZH_TO_EN -> QuizQuestion(
                word,
                quizMode,
                word.chinese,
                word.primaryEnglish,
                QuizEngine.choiceOptions(
                    answer = word.primaryEnglish,
                    candidates = distractors.map(WordEntity::primaryEnglish),
                    seed = word.id.hashCode() xor quizIndex,
                ),
            )
            QuizMode.LISTENING -> QuizQuestion(
                word,
                quizMode,
                "听发音，选择正确的英文",
                word.primaryEnglish,
                QuizEngine.choiceOptions(
                    answer = word.primaryEnglish,
                    candidates = distractors.map(WordEntity::primaryEnglish),
                    seed = word.id.hashCode() xor quizIndex,
                ),
            )
            QuizMode.SPELLING -> QuizQuestion(
                word,
                quizMode,
                word.chinese,
                word.primaryEnglish,
                emptyList(),
            )
            QuizMode.FILL_BLANK -> QuizQuestion(
                word,
                quizMode,
                QuizEngine.blankExample(word.exampleEn, word.primaryEnglish),
                word.primaryEnglish,
                emptyList(),
            )
        }
        quizState.value = QuizUiState.Active(
            question = question,
            index = quizIndex,
            total = quizItems.size,
            correct = quizCorrect,
        )
        if (quizMode == QuizMode.LISTENING) pronounce(word)
    }
}
