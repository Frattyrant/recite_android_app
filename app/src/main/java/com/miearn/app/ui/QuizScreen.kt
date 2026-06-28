package com.miearn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    onPlay: (WordEntity) -> Unit,
    onReset: () -> Unit,
    onRetryWrong: () -> Unit,
) {
    when (state) {
        QuizUiState.Setup -> QuizSetup(modifier, onStart)
        QuizUiState.Loading -> androidx.compose.foundation.layout.Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is QuizUiState.Active -> QuizQuestionView(state, modifier, onSubmit, onNext, onPlay)
        is QuizUiState.Complete -> QuizComplete(
            state,
            modifier,
            onReset,
            onRetryWrong,
        )
    }
}

@Composable
private fun QuizSetup(modifier: Modifier, onStart: (QuizMode, Int, Boolean) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(QuizMode.EN_TO_ZH) }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var learnedOnly by rememberSaveable { mutableStateOf(true) }
    LazyColumn(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("能力测试", style = MaterialTheme.typography.headlineMedium) }
        items(QuizMode.entries) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { mode = item },
                colors = CardDefaults.cardColors(
                    containerColor = if (mode == item) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(item.label, style = MaterialTheme.typography.titleLarge)
                    Text(item.description)
                }
            }
        }
        item {
            Text("题目数量", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30).forEach {
                    AssistChip(onClick = { count = it }, label = { Text(if (count == it) "✓ $it" else "$it") })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("只测试已学词")
                    Text("关闭后从当前词库抽题", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = learnedOnly, onCheckedChange = { learnedOnly = it })
            }
        }
        item {
            Button(onClick = { onStart(mode, count, learnedOnly) }, modifier = Modifier.fillMaxWidth()) {
                Text("开始 ${mode.label}")
            }
        }
    }
}

@Composable
private fun QuizQuestionView(
    state: QuizUiState.Active,
    modifier: Modifier,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onPlay: (WordEntity) -> Unit,
) {
    var input by rememberSaveable(state.index) { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("${state.index + 1} / ${state.total}", color = MaterialTheme.colorScheme.primary)
        Text(state.question.mode.label, style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.question.mode == QuizMode.LISTENING) {
                    IconButton(onClick = { onPlay(state.question.word) }) {
                        Icon(Icons.Rounded.Headphones, contentDescription = "重播发音")
                    }
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
                        singleLine = false,
                        label = { Text("输入答案") },
                    )
                    Button(onClick = { onSubmit(input) }, enabled = input.isNotBlank() && state.feedbackCorrect == null) {
                        Text("提交")
                    }
                } else {
                    state.question.options.forEach { option ->
                        OutlinedButton(
                            onClick = { onSubmit(option) },
                            enabled = state.feedbackCorrect == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
        state.feedbackCorrect?.let { correct ->
            Text(
                if (correct) "回答正确" else "正确答案：${state.question.expected}",
                color = if (correct) Success else Danger,
                style = MaterialTheme.typography.titleLarge,
            )
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
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val rate = if (state.total == 0) 0 else state.correct * 100 / state.total
        if (state.total == 0) {
            Text("当前范围暂无可测试词条", style = MaterialTheme.typography.titleLarge)
            Text("先完成一轮学习，或关闭“只测试已学词”。")
        } else {
            Text(
                "$rate%",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("答对 ${state.correct} / ${state.total}")
            Text(
                "错题 ${state.wrongWordIds.size} 个已加入错题本",
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        if (state.wrongWordIds.isNotEmpty()) {
            Button(
                onClick = onRetryWrong,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("只重测错题")
            }
        }
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("重新设置")
        }
    }
}
