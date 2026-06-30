package com.miearn.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miearn.app.ui.importing.ImportWizardScreen

@Composable
fun MIearnApp(viewModel: MainViewModel) {
    val seedState by viewModel.seedState.collectAsStateWithLifecycle()
    val studyState by viewModel.studyState.collectAsStateWithLifecycle()

    if (seedState !is SeedUiState.Ready) {
        when (val state = seedState) {
            SeedUiState.Loading -> LoadingScreen()
            is SeedUiState.Error -> ErrorScreen(state.message, viewModel::retrySeed)
            is SeedUiState.Ready -> Unit
        }
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
            onFavorite = viewModel::toggleFavorite,
            onToggleCard = viewModel::toggleStudyCard,
            onPreviousBrowse = viewModel::previousBrowseWord,
            onNextBrowse = viewModel::nextBrowseWord,
            onAnswer = viewModel::answerStudy,
            onContinue = viewModel::continueStudy,
            onResolveSaved = viewModel::resolveSavedStudy,
            onOpenTtsSettings = viewModel::openTtsSettings,
        )
        return
    }

    val browserDestination by viewModel.wordBrowserDestination.collectAsStateWithLifecycle()
    if (browserDestination != null) {
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
            onFavorite = viewModel::toggleFavorite,
        )
        return
    }

    val showInsights by viewModel.showInsights.collectAsStateWithLifecycle()
    val insightsState by viewModel.insightsState.collectAsStateWithLifecycle()
    if (showInsights) {
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
        ImportWizardScreen(
            job = importJob,
            localError = importUiError,
            onBack = viewModel::closeImport,
            onFileSelected = viewModel::startImport,
            onMapping = viewModel::resumeImportWithMapping,
            onCommit = viewModel::commitImport,
            onClearError = viewModel::clearImportError,
        )
        return
    }
    val context = LocalContext.current
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
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val showReminderPrompt by viewModel.showReminderPrompt.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
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
        },
    ) { padding ->
        when (tab) {
            MainTab.LEARNING -> V21LearningHomeScreen(
                state = dashboard,
                modifier = Modifier.padding(padding),
                onStartStudy = viewModel::startStudy,
                onSelectCategory = viewModel::selectActiveCategory,
                onOpenSettings = { viewModel.showSettings.value = true },
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
                onPlay = viewModel::pronounce,
                onReset = viewModel::resetQuiz,
                onRetryWrong = viewModel::retryWrongQuiz,
            )

            MainTab.MINE -> MineScreen(
                modifier = Modifier.padding(padding),
                onFavorites = {
                    viewModel.openWordBrowser(WordBrowserDestination.FAVORITES)
                },
                onWrong = {
                    viewModel.openWordBrowser(WordBrowserDestination.WRONG)
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
