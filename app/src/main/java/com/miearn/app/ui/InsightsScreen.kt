package com.miearn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miearn.app.data.InsightDay
import com.miearn.app.data.InsightsSnapshot
import com.miearn.app.ui.theme.Purple
import com.miearn.app.ui.theme.Sunset
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("学习数据", style = MaterialTheme.typography.headlineSmall)
        }
        when (state) {
            InsightsUiState.Loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            is InsightsUiState.Error -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message)
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("重试")
                }
            }

            is InsightsUiState.Ready -> InsightsContent(state.snapshot)
        }
    }
}

@Composable
private fun InsightsContent(snapshot: InsightsSnapshot) {
    var range by rememberSaveable { mutableIntStateOf(7) }
    val days = snapshot.days.takeLast(range)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30).forEach { option ->
                AssistChip(
                    onClick = { range = option },
                    label = { Text(if (range == option) "✓ $option 天" else "$option 天") },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InsightMetric("已学习", snapshot.learned, Modifier.weight(1f))
            InsightMetric("已掌握", snapshot.mastered, Modifier.weight(1f))
            InsightMetric("错题", snapshot.wrong, Modifier.weight(1f))
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("学习与复习", style = MaterialTheme.typography.titleMedium)
                DailyBars(
                    days = days,
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp),
                )
                Text(
                    "紫色为新学，橙色为复习",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("首次正确率", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${(snapshot.firstTryAccuracy * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("预计保持率", style = MaterialTheme.typography.titleMedium)
                Text(
                    "当前 ${(snapshot.averageRetention * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                )
                RetentionCurve(
                    values = snapshot.retentionCurve,
                    modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 10.dp),
                )
                Text(
                    "根据当前 SM-2 间隔估算未来 30 天趋势",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("未来 7 天待复习", style = MaterialTheme.typography.titleMedium)
                (0L..6L).forEach { offset ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (offset == 0L) "今天" else "${offset} 天后")
                        Text("${snapshot.futureDue[offset + snapshot.days.lastOrNull()?.epochDay.orZero()]} 个")
                    }
                }
            }
        }
    }
}

private fun Long?.orZero(): Long = this ?: 0L

@Composable
private fun InsightMetric(
    label: String,
    value: Int,
    modifier: Modifier,
) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$value", style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DailyBars(
    days: List<InsightDay>,
    modifier: Modifier = Modifier,
) {
    val max = days.maxOfOrNull { it.newCount + it.reviewCount }?.coerceAtLeast(1) ?: 1
    Canvas(
        modifier.semantics {
            contentDescription = "近${days.size}天新学与复习趋势图"
        },
    ) {
        if (days.isEmpty()) return@Canvas
        val slot = size.width / days.size
        val barWidth = slot * .48f
        days.forEachIndexed { index, day ->
            val x = index * slot + (slot - barWidth) / 2
            val newHeight = size.height * day.newCount / max
            val reviewHeight = size.height * day.reviewCount / max
            drawRoundRect(
                color = Purple,
                topLeft = Offset(x, size.height - newHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, newHeight),
            )
            drawRoundRect(
                color = Sunset,
                topLeft = Offset(x, size.height - newHeight - reviewHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, reviewHeight),
            )
        }
    }
}

@Composable
private fun RetentionCurve(
    values: List<Double>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier.semantics {
            contentDescription = "未来30天预计保持率曲线"
        },
    ) {
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = size.height * (1f - value.coerceIn(0.0, 1.0).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
    }
}
