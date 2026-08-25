package com.miearn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus

@Composable
fun V21LearningHomeScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onStartStudy: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onImportVocabulary: () -> Unit,
    importJob: ImportJobEntity? = null,
) {
    var categoryMenu by rememberSaveable { mutableStateOf(false) }
    val active = state.activeStats

    SoftPageBackground(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    HomeTopBar(
                        categoryLabel = active?.categoryLabel ?: "选择词库",
                        categories = state.categories.map { it.category to it.categoryLabel },
                        categoryMenu = categoryMenu,
                        onCategoryMenuChange = { categoryMenu = it },
                        onSelectCategory = onSelectCategory,
                        onImportVocabulary = onImportVocabulary,
                        onOpenSearch = onOpenSearch,
                        onOpenSettings = onOpenSettings,
                    )
                }
                item {
                    DailyTaskCard(state)
                }
                if (
                    importJob != null &&
                    shouldShowHomeImportJob(importJob)
                ) {
                    item {
                        Card(
                            onClick = onImportVocabulary,
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (importJob.status == ImportJobStatus.FAILED.name) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                },
                            ),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("词库导入", fontWeight = FontWeight.SemiBold)
                                Text(
                                    importHomeStatusText(importJob),
                                    color = if (importJob.status == ImportJobStatus.FAILED.name) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                if (importJob.status == ImportJobStatus.FAILED.name) {
                                    Text(
                                        "点击查看详情并重新选择",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onStartStudy,
                enabled = hasDailyStudyTask(state.todayNew, state.todayReview),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("primary-study-action"),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    dailyStudyActionLabel(state.todayNew, state.todayReview),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
@Composable
private fun HomeTopBar(
    categoryLabel: String,
    categories: List<Pair<String, String>>,
    categoryMenu: Boolean,
    onCategoryMenuChange: (Boolean) -> Unit,
    onSelectCategory: (String) -> Unit,
    onImportVocabulary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "MIearn",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Box {
                TextButton(onClick = { onCategoryMenuChange(true) }) {
                    Text(categoryLabel)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = categoryMenu,
                    onDismissRequest = { onCategoryMenuChange(false) },
                ) {
                    categories.forEach { (category, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onCategoryMenuChange(false)
                                onSelectCategory(category)
                            },
                        )
                    }
                }
            }
        }
        TextButton(onClick = onImportVocabulary) {
            Text("导入")
        }
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Default.Search, contentDescription = "搜索全词库")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "设置")
        }
    }
}
@Composable
private fun DailyTaskCard(state: DashboardUiState) {
    val active = state.activeStats
    val taskTotal = state.todayNew + state.todayReview
    val estimatedMinutes = estimateStudyMinutes(taskTotal)
    val progress = if (active == null || active.total == 0) {
        0f
    } else {
        active.learned / active.total.toFloat()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-daily-task"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (taskTotal > 0) "今天，只做一件事" else "今天的任务",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (taskTotal > 0) {
                        "继续学习 $taskTotal 个词"
                    } else {
                        "今天的任务已完成"
                    },
                    modifier = Modifier
                        .padding(end = 74.dp)
                        .testTag("home-task-summary"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    if (estimatedMinutes > 0) {
                        "预计 $estimatedMinutes 分钟完成"
                    } else {
                        "做得很好，明天继续"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeMetric(
                        label = "新学",
                        value = state.todayNew.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    HomeMetric(
                        label = "复习",
                        value = state.todayReview.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    HomeMetric(
                        label = "总进度",
                        value = "${(progress * 100).toInt()}%",
                        modifier = Modifier.weight(1f),
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp)),
                )
                Text(
                    "${active?.learned ?: 0} / ${active?.total ?: 0} 已学习 · " +
                        "${state.mastered} 已掌握",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StreakBadge(
                days = state.streak,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 18.dp)
                    .testTag("home-streak"),
            )
        }
    }
}

@Composable
private fun HomeMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StreakBadge(
    days: Int,
    modifier: Modifier = Modifier,
) {
    val flameSize = when (StreakFlameLevel.fromDays(days)) {
        StreakFlameLevel.NONE -> 0.dp
        StreakFlameLevel.SMALL -> 25.dp
        StreakFlameLevel.MEDIUM -> 31.dp
        StreakFlameLevel.LARGE -> 38.dp
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StreakFlame(
            days = days,
            modifier = Modifier.size(flameSize),
        )
        Text(
            "连续 $days 天",
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
