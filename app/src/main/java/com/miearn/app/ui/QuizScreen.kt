package com.miearn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import com.miearn.app.data.local.WordEntity
import com.miearn.app.ui.theme.Danger
import com.miearn.app.ui.theme.Success

@Composable
fun QuizScreen(
    state: QuizUiState,
    modifier: Modifier = Modifier,
    onStart: (QuizMode, Int, Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onPlayVariant: (WordEntity, Int) -> Unit,
    onPlayExample: ((String) -> Unit)? = null,
    onReset: () -> Unit,
    onRetryWrong: () -> Unit,
) {
    SoftPageBackground(modifier) {
        when (state) {
            QuizUiState.Setup -> QuizSetup(Modifier.fillMaxSize(), onStart)
            QuizUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is QuizUiState.Unavailable -> QuizUnavailable(
                message = state.message,
                modifier = Modifier.fillMaxSize(),
                onReset = onReset,
            )
            is QuizUiState.Active -> QuizQuestionView(
                state,
                Modifier.fillMaxSize(),
                onSubmit,
                onNext,
                onPlayVariant,
                onPlayExample,
            )
            is QuizUiState.Complete -> QuizComplete(state, Modifier.fillMaxSize(), onReset, onRetryWrong)
        }
    }
}

@Composable
private fun QuizUnavailable(
    message: String,
    modifier: Modifier,
    onReset: () -> Unit,
) {
    Column(
        modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SoftEmptyState("暂时无法生成选择题", message)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("重新选择测试方式")
        }
    }
}

@Composable
private fun QuizSetup(
    modifier: Modifier,
    onStart: (QuizMode, Int, Boolean) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(QuizMode.EN_TO_ZH) }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var learnedOnly by rememberSaveable { mutableStateOf(true) }
    BoxWithConstraints(modifier) {
        val columns = if (maxWidth < 380.dp) 1 else 2
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 18.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SoftPageHeader(
                        title = "能力测试",
                        subtitle = "选择一种方式，看看今天记住了多少",
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        QuizMode.entries.chunked(columns).forEach { rowModes ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                rowModes.forEach { item ->
                                    QuizModeCard(
                                        mode = item,
                                        selected = mode == item,
                                        onClick = { mode = item },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columns - rowModes.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("题目数量", style = MaterialTheme.typography.titleMedium)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(10, 20, 30).forEach { option ->
                                    val selected = count == option
                                    if (selected) {
                                        FilledTonalButton(
                                            onClick = { count = option },
                                            modifier = Modifier.weight(1f),
                                        ) { Text("$option") }
                                    } else {
                                        OutlinedButton(
                                            onClick = { count = option },
                                            modifier = Modifier.weight(1f),
                                        ) { Text("$option") }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("只测试已学词")
                                    Text(
                                        "关闭后从当前词库抽题",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = learnedOnly, onCheckedChange = { learnedOnly = it })
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onStart(mode, count, learnedOnly) },
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag("start-quiz-action"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("开始 ${mode.label}", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuizModeCard(
    mode: QuizMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(mode.symbol, style = MaterialTheme.typography.titleLarge)
            Text(mode.label, style = MaterialTheme.typography.titleMedium)
            Text(
                mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

private val QuizMode.symbol: String
    get() = when (this) {
        QuizMode.EN_TO_ZH -> "A / 中"
        QuizMode.ZH_TO_EN -> "中 / A"
        QuizMode.SPELLING -> "Aa"
        QuizMode.LISTENING -> "♪"
        QuizMode.FILL_BLANK -> "…"
    }

@Composable
private fun QuizQuestionView(
    state: QuizUiState.Active,
    modifier: Modifier,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onPlayVariant: (WordEntity, Int) -> Unit,
    onPlayExample: ((String) -> Unit)?,
) {
    val inputKey = quizInputKey(
        mode = state.question.mode,
        wordId = state.question.word.id,
        index = state.index,
    )
    var input by remember(inputKey) { mutableStateOf("") }
    val inputFocusRequester = remember(inputKey) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val usesTextInput = quizModeUsesTextInput(state.question.mode)
    LaunchedEffect(inputKey, usesTextInput) {
        if (usesTextInput) {
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.question.mode.label, style = MaterialTheme.typography.titleMedium)
            Text("${state.index + 1} / ${state.total}", color = MaterialTheme.colorScheme.primary)
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconButton(
                    onClick = {
                        onPlayVariant(
                            state.question.word,
                            listeningAudioVariantIndex(state.question.word),
                        )
                    },
                    modifier = Modifier.testTag("quiz-replay-audio"),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "重播主表达发音")
                }
                Text(
                    state.question.prompt,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (state.question.mode == QuizMode.SPELLING || state.question.mode == QuizMode.FILL_BLANK) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        enabled = state.feedbackCorrect == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocusRequester),
                        label = { Text("输入答案") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (input.isNotBlank() && state.feedbackCorrect == null) {
                                    onSubmit(input)
                                }
                            },
                        ),
                    )
                    Button(
                        onClick = { onSubmit(input) },
                        enabled = input.isNotBlank() && state.feedbackCorrect == null,
                    ) { Text("提交") }
                } else {
                    state.question.options.forEach { option ->
                        val isCorrectOption = option == state.question.expected
                        val isSubmittedOption = option == state.submittedAnswer
                        val optionColors = when {
                            state.feedbackCorrect != null && isCorrectOption ->
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = Success.copy(alpha = 0.14f),
                                    contentColor = Success,
                                )
                            state.feedbackCorrect == false && isSubmittedOption ->
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = Danger.copy(alpha = 0.14f),
                                    contentColor = Danger,
                                )
                            else -> ButtonDefaults.outlinedButtonColors()
                        }
                        OutlinedButton(
                            onClick = { onSubmit(option) },
                            enabled = state.feedbackCorrect == null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = optionColors,
                        ) { Text(option, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
        state.feedbackCorrect?.let { correct ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = (if (correct) Success else Danger).copy(alpha = 0.12f),
                ),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        if (correct) "回答正确" else "正确答案：${state.question.expected}",
                        color = if (correct) Success else Danger,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "记忆提示",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (state.question.word.phonetic.isNotBlank()) {
                        Text(
                            state.question.word.phonetic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ExampleList(
                        word = state.question.word,
                        onPlayExample = onPlayExample,
                    )
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("下一题") }
        }
    }
}

@Composable
private fun QuizComplete(
    state: QuizUiState.Complete,
    modifier: Modifier,
    onReset: () -> Unit,
    onRetryWrong: () -> Unit,
) {
    Column(
        modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val rate = if (state.total == 0) 0 else state.correct * 100 / state.total
        if (state.total == 0) {
            SoftEmptyState("当前范围暂无可测试词条", "先完成一轮学习，或关闭“只测试已学词”。")
        } else {
            Text("本轮完成", style = MaterialTheme.typography.titleMedium)
            Text(
                "$rate%",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("答对 ${state.correct} / ${state.total}")
            Text(
                "${state.wrongWordIds.size} 个错题已加入强化列表",
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.wrongWordIds.isNotEmpty()) {
            Button(onClick = onRetryWrong, modifier = Modifier.fillMaxWidth()) {
                Text("只重测错题")
            }
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("重新设置")
        }
    }
}
