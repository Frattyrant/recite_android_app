package com.miearn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miearn.app.audio.PronunciationStatus
import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.LearningPhase
import com.miearn.app.domain.LearningContentPolicy
import com.miearn.app.ui.theme.Danger
import com.miearn.app.ui.theme.Success
import com.miearn.app.ui.theme.Sunset
import kotlin.math.abs

@Composable
fun StudyScreen(
    state: StudyUiState,
    onClose: () -> Unit,
    onPlay: (WordEntity) -> Unit,
    onPlayVariant: (WordEntity, Int) -> Unit,
    onFavorite: (String) -> Unit,
    onToggleCard: () -> Unit,
    onPreviousBrowse: () -> Unit,
    onNextBrowse: () -> Unit,
    onAnswer: (String) -> Unit,
    onContinue: () -> Unit,
    onResolveSaved: (Boolean) -> Unit,
    onOpenTtsSettings: () -> Unit,
) {
    when (state) {
        StudyUiState.Idle -> Unit
        StudyUiState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is StudyUiState.ResumePrompt -> ResumePrompt(
            state = state,
            onContinueSaved = { onResolveSaved(true) },
            onStartToday = { onResolveSaved(false) },
            onClose = onClose,
        )

        is StudyUiState.Complete -> StudyComplete(state, onClose)
        is StudyUiState.Active -> ActiveStudy(
            state = state,
            onClose = onClose,
            onPlay = onPlay,
            onPlayVariant = onPlayVariant,
            onFavorite = onFavorite,
            onToggleCard = onToggleCard,
            onPreviousBrowse = onPreviousBrowse,
            onNextBrowse = onNextBrowse,
            onAnswer = onAnswer,
            onContinue = onContinue,
            onOpenTtsSettings = onOpenTtsSettings,
        )
    }
}

@Composable
private fun ResumePrompt(
    state: StudyUiState.ResumePrompt,
    onContinueSaved: () -> Unit,
    onStartToday: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("发现未完成的学习", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "上次任务仍保留在本机，可以继续原来的位置。",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinueSaved, modifier = Modifier.fillMaxWidth()) {
            Text("继续上次任务")
        }
        OutlinedButton(onClick = onStartToday, modifier = Modifier.fillMaxWidth()) {
            Text("开始今日任务")
        }
        TextButton(onClick = onClose) { Text("暂时退出") }
    }
}

@Composable
private fun StudyComplete(
    state: StudyUiState.Complete,
    onClose: () -> Unit,
) {
    val accuracy = if (state.answeredFirstTry == 0) {
        0
    } else {
        state.correctFirstTry * 100 / state.answeredFirstTry
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("今日任务完成", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("新学 ${state.newCount} · 复习 ${state.reviewCount}")
        Text("首次正确率 $accuracy%")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("返回学习页")
        }
    }
}

@Composable
private fun ActiveStudy(
    state: StudyUiState.Active,
    onClose: () -> Unit,
    onPlay: (WordEntity) -> Unit,
    onPlayVariant: (WordEntity, Int) -> Unit,
    onFavorite: (String) -> Unit,
    onToggleCard: () -> Unit,
    onPreviousBrowse: () -> Unit,
    onNextBrowse: () -> Unit,
    onAnswer: (String) -> Unit,
    onContinue: () -> Unit,
    onOpenTtsSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(
            top = 28.dp,
            start = 18.dp,
            end = 18.dp,
            bottom = 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudyHeader(state, onClose)
        if (state.phase == LearningPhase.BROWSE) {
            BrowseCard(
                state = state,
                onPlay = onPlay,
                onPlayVariant = onPlayVariant,
                onFavorite = onFavorite,
                onToggleCard = onToggleCard,
                onPrevious = onPreviousBrowse,
                onNext = onNextBrowse,
                modifier = Modifier.weight(1f),
            )
        } else {
            ChoiceCard(
                state = state,
                onPlay = onPlay,
                onPlayVariant = onPlayVariant,
                onFavorite = onFavorite,
                onAnswer = onAnswer,
                onContinue = onContinue,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.audioHelpVisible) {
            TextButton(
                onClick = onOpenTtsSettings,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("启用英语语音")
            }
        }
    }
}

@Composable
private fun StudyHeader(
    state: StudyUiState.Active,
    onClose: () -> Unit,
) {
    val phaseLabel = when (state.phase) {
        LearningPhase.REVIEW -> "到期复习"
        LearningPhase.BROWSE -> "第一遍 · 浏览"
        LearningPhase.CONSOLIDATE -> "第二遍 · 巩固"
        LearningPhase.REINFORCEMENT -> "错题强化"
        LearningPhase.COMPLETE -> "完成"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "退出")
        }
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(phaseLabel, fontWeight = FontWeight.SemiBold)
                Text("${state.index + 1}/${state.total}")
            }
            LinearProgressIndicator(
                progress = {
                    if (state.total == 0) 0f
                    else (state.index + 1) / state.total.toFloat()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun BrowseCard(
    state: StudyUiState.Active,
    onPlay: (WordEntity) -> Unit,
    onPlayVariant: (WordEntity, Int) -> Unit,
    onFavorite: (String) -> Unit,
    onToggleCard: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragTotal = 0f
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "浏览单词卡，点击展开释义" }
            .pointerInput(state.word.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragTotal += amount
                    },
                    onDragEnd = {
                        if (abs(dragTotal) > 80f) {
                            if (dragTotal < 0) onNext() else onPrevious()
                        }
                        dragTotal = 0f
                    },
                )
            }
            .clickable(onClick = onToggleCard),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EnglishVariants(
                word = state.word,
                onPlayVariant = onPlayVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onPlay(state.word) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "播放发音")
                }
                IconButton(onClick = { onFavorite(state.word.id) }) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "收藏")
                }
            }
            if (state.expanded) {
                if (state.word.phonetic.isNotBlank()) {
                    Text(
                        state.word.phonetic,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Text(
                    LearningContentPolicy.displayChinese(state.word.chinese),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                if (state.word.exampleEn.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        state.word.exampleEn,
                        modifier = Modifier.fillMaxWidth(),
                        fontStyle = FontStyle.Italic,
                    )
                    if (state.word.exampleZh.isNotBlank()) {
                        Text(
                            state.word.exampleZh,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                        )
                    }
                }
                if (state.word.note.isNotBlank()) {
                    Text(
                        "备注：${state.word.note}",
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(
                    "点击查看音标、释义与例句",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrevious, enabled = state.index > 0) {
            Text("上一个")
        }
        TextButton(onClick = onNext) {
            Text(if (state.index + 1 == state.total) "进入巩固" else "下一个")
        }
    }
}

@Composable
private fun ChoiceCard(
    state: StudyUiState.Active,
    onPlay: (WordEntity) -> Unit,
    onPlayVariant: (WordEntity, Int) -> Unit,
    onFavorite: (String) -> Unit,
    onAnswer: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EnglishVariants(
                    word = state.word,
                    onPlayVariant = onPlayVariant,
                )
                if (state.word.phonetic.isNotBlank()) {
                    Text(
                        state.word.phonetic,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                Row {
                    IconButton(onClick = { onPlay(state.word) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "播放发音")
                    }
                    IconButton(onClick = { onFavorite(state.word.id) }) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "收藏")
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        state.options.forEach { option ->
            val answered = state.firstCorrect != null
            val isCorrectOption =
                option == LearningContentPolicy.displayChinese(state.word.chinese)
            val isSelectedWrong = state.selectedAnswer == option && !isCorrectOption
            val container = when {
                answered && isCorrectOption -> Success.copy(alpha = .16f)
                answered && isSelectedWrong -> Danger.copy(alpha = .13f)
                else -> Color.Transparent
            }
            OutlinedButton(
                onClick = { onAnswer(option) },
                enabled = !answered,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(container, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(option, textAlign = TextAlign.Center)
            }
        }
        if (state.firstCorrect != null) {
            Text(
                if (state.firstCorrect) {
                    "回答正确"
                } else if (state.phase == LearningPhase.REINFORCEMENT) {
                    "正确答案：${LearningContentPolicy.displayChinese(state.word.chinese)}"
                } else {
                    "正确答案：${LearningContentPolicy.displayChinese(state.word.chinese)} · 已加入本轮强化"
                },
                color = if (state.firstCorrect) Success else Sunset,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 12.dp),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("继续")
            }
        }
    }
}
