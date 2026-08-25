package com.miearn.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.miearn.app.ui.importing.ImportWizardScreen

@Composable
fun MIearnApp(viewModel: MainViewModel) {
    val seedState by viewModel.seedState.collectAsStateWithLifecycle()
    val studyState by viewModel.studyState.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    if (seedState !is SeedUiState.Ready) {
        when (val state = seedState) {
            SeedUiState.Loading -> LoadingScreen()
            is SeedUiState.Error -> ErrorScreen(state.message, viewModel::retrySeed)
            is SeedUiState.Ready -> Unit
        }
        return
    }

    val wordDetail by viewModel.wordDetailRequest.collectAsStateWithLifecycle()
    if (wordDetail != null) {
        val request = checkNotNull(wordDetail)
        BackHandler(onBack = viewModel::closeWordDetail)
        WordDetailScreen(
            request = request,
            phonetic = resolveVariantPhonetic(request.word, request.variantIndex),
            onBack = viewModel::closeWordDetail,
            onPlay = viewModel::playWordDetail,
            onPlayExample = viewModel::pronounceText,
            onFavorite = { viewModel.toggleFavorite(request.word.id) },
            isFavorite = request.word.id in favoriteIds,
        )
        return
    }
    BackHandler(
        enabled = shouldHandleStudyBack(studyState),
        onBack = viewModel::closeStudy,
    )

    if (studyState !is StudyUiState.Idle) {
        StudyScreen(
            state = studyState,
            onClose = viewModel::closeStudy,
            onPlay = viewModel::pronounce,
            onPlayVariant = viewModel::pronounceVariant,
            onPlayExample = viewModel::pronounceText,
            onOpenWordDetail = viewModel::openWordDetail,
            onFavorite = viewModel::toggleFavorite,
            favoriteIds = favoriteIds,
            onToggleCard = viewModel::toggleStudyCard,
            onPreviousBrowse = viewModel::previousBrowseWord,
            onNextBrowse = viewModel::nextBrowseWord,
            onAnswer = viewModel::answerStudy,
            onResolveSaved = viewModel::resolveSavedStudy,
            onOpenTtsSettings = viewModel::openTtsSettings,
        )
        return
    }

    val browserDestination by viewModel.wordBrowserDestination.collectAsStateWithLifecycle()
    if (browserDestination != null) {
        BackHandler(onBack = viewModel::closeWordBrowser)
        val browserWords by viewModel.wordBrowserWords.collectAsStateWithLifecycle()
        val browserQuery by viewModel.wordBrowserQuery.collectAsStateWithLifecycle()
        WordBrowserScreen(
            destination = checkNotNull(browserDestination),
            query = browserQuery,
            words = browserWords,
            onBack = viewModel::closeWordBrowser,
            onQuery = { viewModel.wordBrowserQuery.value = it },
            onPlay = viewModel::pronounce,
            onPlayVariant = viewModel::pronounceVariant,
            onPlayExample = viewModel::pronounceText,
            onOpenWordDetail = viewModel::openWordDetail,
            onFavorite = viewModel::toggleFavorite,
            favoriteIds = favoriteIds,
        )
        return
    }

    val showInsights by viewModel.showInsights.collectAsStateWithLifecycle()
    val insightsState by viewModel.insightsState.collectAsStateWithLifecycle()
    if (showInsights) {
        BackHandler(onBack = viewModel::closeInsights)
        InsightsScreen(
            state = insightsState,
            onClose = viewModel::closeInsights,
            onRetry = viewModel::refreshInsights,
        )
        return
    }

    val showSourceManager by viewModel.showSourceManager.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    if (showSourceManager) {
        BackHandler(onBack = viewModel::closeSourceManager)
        SourceManagerScreen(
            sources = sources,
            onBack = viewModel::closeSourceManager,
            onRename = viewModel::renameSource,
            onDelete = viewModel::deleteSource,
        )
        return
    }
    val showImport by viewModel.showImport.collectAsStateWithLifecycle()
    val importJob by viewModel.importJob.collectAsStateWithLifecycle()
    val importUiError by viewModel.importUiError.collectAsStateWithLifecycle()
    if (showImport) {
        BackHandler(onBack = viewModel::closeImport)
        ImportWizardScreen(
            job = importJob,
            localError = importUiError,
            onBack = viewModel::closeImport,
            onCancel = viewModel::cancelImport,
            onUseSource = viewModel::useImportedSource,
            onFileSelected = viewModel::startImport,
            onMapping = viewModel::resumeImportWithMapping,
            onCommit = viewModel::commitImport,
            onClearError = viewModel::clearImportError,
            onRetry = viewModel::retryImport,
        )
        return
    }
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
        viewModel.refreshReminderStatus()
    }
    var permissionForPrompt by remember { mutableStateOf(false) }
    var reminderPermissionMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        reminderPermissionMessage = if (granted) {
            null
        } else {
            "通知权限未开启，学习提醒保持关闭。"
        }
        if (permissionForPrompt) {
            viewModel.resolveReminderPrompt(granted)
        } else {
            viewModel.setReminderEnabled(granted)
        }
        permissionForPrompt = false
    }
    fun requestReminder(enable: Boolean, fromPrompt: Boolean) {
        if (!enable) {
            reminderPermissionMessage = null
            if (fromPrompt) viewModel.resolveReminderPrompt(false)
            else viewModel.setReminderEnabled(false)
            return
        }
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            reminderPermissionMessage = null
            if (fromPrompt) viewModel.resolveReminderPrompt(true)
            else viewModel.setReminderEnabled(true)
        } else {
            permissionForPrompt = fromPrompt
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val mineState by viewModel.mineState.collectAsStateWithLifecycle()
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val showReminderPrompt by viewModel.showReminderPrompt.collectAsStateWithLifecycle()
    val reminderUiState by viewModel.reminderUiState.collectAsStateWithLifecycle()
    val reminderTestResult by viewModel.reminderTestResult.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                NavigationBar(
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 3.dp,
                ) {
                    MainTab.entries.forEach { item ->
                        val icon = when (item) {
                            MainTab.LEARNING -> Icons.Default.Home
                            MainTab.QUIZ -> Icons.Default.Check
                            MainTab.MINE -> Icons.Default.AccountCircle
                        }
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { viewModel.selectTab(item) },
                            icon = { Icon(icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (tab) {
            MainTab.LEARNING -> V21LearningHomeScreen(
                state = dashboard,
                modifier = Modifier.padding(padding),
                onStartStudy = viewModel::startStudy,
                onSelectCategory = viewModel::selectActiveCategory,
                onOpenSettings = viewModel::openSettings,
                onOpenSearch = {
                    viewModel.openWordBrowser(WordBrowserDestination.SEARCH)
                },
                onImportVocabulary = viewModel::openImport,
                importJob = importJob,
            )

            MainTab.QUIZ -> QuizScreen(
                state = quizState,
                modifier = Modifier.padding(padding),
                onStart = viewModel::startQuiz,
                onSubmit = viewModel::submitQuiz,
                onNext = viewModel::nextQuizQuestion,
                onPlayVariant = viewModel::pronounceVariant,
                onPlayExample = viewModel::pronounceText,
                onReset = viewModel::resetQuiz,
                onRetryWrong = viewModel::retryWrongQuiz,
            )

            MainTab.MINE -> MineScreen(
                state = mineState,
                modifier = Modifier.padding(padding),
                onPreviousMonth = viewModel::previousMineMonth,
                onNextMonth = viewModel::nextMineMonth,
                onSelectDay = viewModel::selectMineDay,
                onDismissDay = viewModel::closeMineDay,
                onRetry = viewModel::refreshMine,
                onFavorites = {
                    viewModel.openWordBrowser(WordBrowserDestination.FAVORITES)
                },
                onWrong = {
                    viewModel.openWordBrowser(WordBrowserDestination.WRONG)
                },
                onMastered = {
                    viewModel.openWordBrowser(WordBrowserDestination.MASTERED)
                },
                onInsights = viewModel::openInsights,
                onSources = viewModel::openSourceManager,
            )
        }
    }

    if (showSettings) {
        V21SettingsDialog(
            settings = dashboard.settings,
            onDismiss = { viewModel.showSettings.value = false },
            onGoal = viewModel::setDailyGoal,
            onAutoPlay = viewModel::setAutoPlay,
            onReminderEnabled = { requestReminder(it, false) },
            onReminderTime = viewModel::setReminderTime,
            reminderUiState = reminderUiState,
            reminderTestResult = reminderTestResult,
            onTestReminder = viewModel::testLearningReminder,
            onOpenNotificationSettings = viewModel::openReminderNotificationSettings,
            reminderPermissionMessage = reminderPermissionMessage,
        )
    }

    if (showReminderPrompt) {
        AlertDialog(
            onDismissRequest = { requestReminder(false, true) },
            title = { Text("每天提醒学习？") },
            text = { Text("默认每天上午 10:00 提醒一次，可在设置中修改。") },
            confirmButton = {
                TextButton(onClick = { requestReminder(true, true) }) {
                    Text("每天 10:00 提醒我")
                }
            },
            dismissButton = {
                TextButton(onClick = { requestReminder(false, true) }) {
                    Text("暂不")
                }
            },
        )
    }
}

internal fun shouldHandleStudyBack(state: StudyUiState): Boolean =
    state !is StudyUiState.Idle

@Composable
private fun LoadingScreen() {
    SoftPageBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
