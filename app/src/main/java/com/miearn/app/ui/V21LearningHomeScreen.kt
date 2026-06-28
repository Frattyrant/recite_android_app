package com.miearn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun V21LearningHomeScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onStartStudy: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    var categoryMenu by rememberSaveable { mutableStateOf(false) }
    val active = state.activeStats
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
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
                        TextButton(onClick = { categoryMenu = true }) {
                            Text(active?.categoryLabel ?: "选择词库")
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = categoryMenu,
                            onDismissRequest = { categoryMenu = false },
                        ) {
                            state.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.categoryLabel) },
                                    onClick = {
                                        categoryMenu = false
                                        onSelectCategory(category.category)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索全词库")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            HomeMetric("新学", state.todayNew)
                            HomeMetric("复习", state.todayReview)
                            HomeMetric("连续", state.streak, "天")
                        }
                        val progress = if (active == null || active.total == 0) {
                            0f
                        } else {
                            active.learned / active.total.toFloat()
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${active?.learned ?: 0} / ${active?.total ?: 0} 已学习 · ${state.mastered} 已掌握",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                        )
                    }
                }
            }
        }
        Button(
            onClick = onStartStudy,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("primary-study-action"),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Text(
                "开始学习",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun HomeMetric(
    label: String,
    value: Int,
    suffix: String = "",
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value$suffix",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
