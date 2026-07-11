package com.miearn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miearn.app.domain.CalendarDaySummary
import com.miearn.app.domain.CalendarDayUi
import com.miearn.app.domain.CalendarIntensity
import com.miearn.app.domain.CalendarMonthUi
import com.miearn.app.domain.WeeklyLearningSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    state: MineUiState,
    modifier: Modifier = Modifier,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onDismissDay: () -> Unit,
    onRetry: () -> Unit,
    onFavorites: () -> Unit,
    onWrong: () -> Unit,
    onInsights: () -> Unit,
    onSources: () -> Unit,
) {
    SoftPageBackground(modifier) {
        when (state) {
            MineUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is MineUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SoftErrorState(state.message, onRetry)
            }

            is MineUiState.Ready -> MineContent(
                state = state,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectDay = onSelectDay,
                onFavorites = onFavorites,
                onWrong = onWrong,
                onInsights = onInsights,
                onSources = onSources,
            )
        }
    }
    (state as? MineUiState.Ready)?.selectedDay?.let { day ->
        ModalBottomSheet(onDismissRequest = onDismissDay) {
            DaySummarySheet(day)
        }
    }
}

@Composable
private fun MineContent(
    state: MineUiState.Ready,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onFavorites: () -> Unit,
    onWrong: () -> Unit,
    onInsights: () -> Unit,
    onSources: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SoftPageHeader(
                title = "学习足迹",
                subtitle = "把每天的积累，安静地留在这里",
            )
        }
        item {
            LearningCalendarCard(
                calendar = state.snapshot.calendar,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectDay = onSelectDay,
            )
        }
        item { WeeklySummaryRow(state.snapshot.weekly) }
        item { SoftSectionTitle("我的内容", Modifier.padding(top = 8.dp)) }
        item {
            SoftEntryRow(
                title = "收藏词条",
                subtitle = "保存想要反复看的表达",
                icon = Icons.Default.FavoriteBorder,
                onClick = onFavorites,
            )
        }
        item {
            SoftEntryRow(
                title = "错题强化",
                subtitle = "集中复盘仍不稳定的内容",
                icon = Icons.Default.Warning,
                onClick = onWrong,
                iconColor = MaterialTheme.colorScheme.secondary,
            )
        }
        item { SoftSectionTitle("数据与工具", Modifier.padding(top = 8.dp)) }
        item {
            SoftEntryRow(
                title = "学习数据",
                subtitle = "正确率、保持率与复习趋势",
                icon = Icons.Default.Info,
                onClick = onInsights,
            )
        }
        item {
            SoftEntryRow(
                title = "自定义词库",
                subtitle = "管理从 CSV 或 Excel 导入的内容",
                icon = Icons.Default.Add,
                onClick = onSources,
                iconColor = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun LearningCalendarCard(
    calendar: CalendarMonthUi,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("learning-calendar"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPreviousMonth, enabled = calendar.canGoPrevious) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上个月")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${calendar.month.year} 年 ${calendar.month.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "颜色越深，学习越多",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNextMonth, enabled = calendar.canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下个月")
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            calendar.days.chunked(7).forEach { week ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    week.forEach { day ->
                        CalendarDayCell(
                            day = day,
                            modifier = Modifier.weight(1f),
                            onClick = onSelectDay,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDayUi,
    modifier: Modifier,
    onClick: (Long) -> Unit,
) {
    val date = day.date
    if (date == null) {
        Spacer(modifier.aspectRatio(1f))
        return
    }
    val colors = MaterialTheme.colorScheme
    val background = when (day.intensity) {
        CalendarIntensity.NONE -> colors.surfaceVariant.copy(alpha = 0.38f)
        CalendarIntensity.LOW -> colors.secondaryContainer.copy(alpha = 0.82f)
        CalendarIntensity.MEDIUM -> colors.primaryContainer
        CalendarIntensity.HIGH -> colors.primary
    }
    val contentColor = if (day.intensity == CalendarIntensity.HIGH) colors.onPrimary else colors.onSurface
    val enabled = !day.isFuture
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("calendar-day-${date.toEpochDay()}")
            .clip(RoundedCornerShape(10.dp))
            .background(background.copy(alpha = if (enabled) 1f else 0.32f))
            .clickable(enabled = enabled) { onClick(date.toEpochDay()) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = if (enabled) 1f else 0.42f),
            fontWeight = if (date == LocalDate.now()) FontWeight.ExtraBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun WeeklySummaryRow(summary: WeeklyLearningSummary) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("mine-week-summary"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SoftMetricCard("本周词条", "${summary.totalCount}", Modifier.weight(1f))
        SoftMetricCard(
            "首次正确",
            summary.firstTryAccuracy?.let { "${(it * 100).roundToInt()}%" } ?: "—",
            Modifier.weight(1f),
        )
        SoftMetricCard("连续天数", "${summary.streak}", Modifier.weight(1f))
    }
}

@Composable
private fun DaySummarySheet(day: CalendarDaySummary) {
    val date = LocalDate.ofEpochDay(day.epochDay)
    val formatter = DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.SIMPLIFIED_CHINESE)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(date.format(formatter), style = MaterialTheme.typography.headlineSmall)
        if (day.totalCount == 0) {
            SoftEmptyState(
                title = "这一天还没有学习记录",
                message = "空白也是节奏的一部分，下一次打开就从今天继续。",
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftMetricCard("新学", "${day.newCount}", Modifier.weight(1f))
                SoftMetricCard("复习", "${day.reviewCount}", Modifier.weight(1f))
                SoftMetricCard("总量", "${day.totalCount}", Modifier.weight(1f))
            }
            Text(
                "首次正确率  ${day.firstTryAccuracy?.let { "${(it * 100).roundToInt()}%" } ?: "—"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.padding(bottom = 10.dp))
    }
}
