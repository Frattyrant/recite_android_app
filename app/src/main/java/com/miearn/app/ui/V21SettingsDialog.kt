package com.miearn.app.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miearn.app.data.settings.UserSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun V21SettingsDialog(
    settings: UserSettings,
    onDismiss: () -> Unit,
    onGoal: (Int) -> Unit,
    onAutoPlay: (Boolean) -> Unit,
    onReminderEnabled: (Boolean) -> Unit,
    onReminderTime: (Int, Int) -> Unit,
    reminderPermissionMessage: String? = null,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学习设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("每日新词")
                DailyGoalRuler(settings.dailyGoal, onGoal)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("卡片自动发音")
                    Switch(
                        checked = settings.autoPlay,
                        onCheckedChange = onAutoPlay,
                        modifier = Modifier.semantics {
                            contentDescription = "卡片自动发音"
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("学习任务提醒")
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = onReminderEnabled,
                        modifier = Modifier.semantics {
                            contentDescription = "学习任务提醒"
                        },
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> onReminderTime(hour, minute) },
                                settings.reminderHour,
                                settings.reminderMinute,
                                DateFormat.is24HourFormat(context),
                            ).show()
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("提醒时间")
                    Text(
                        "%02d:%02d".format(
                            settings.reminderHour,
                            settings.reminderMinute,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                reminderPermissionMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun DailyGoalRuler(
    selectedGoal: Int,
    onGoal: (Int) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().testTag("daily-goal-ruler"),
    ) {
        val itemWidth = 56.dp
        val padding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
        val selectedIndex = DailyGoalScale.values.indexOf(
            DailyGoalScale.snap(selectedGoal),
        )
        val listState = rememberLazyListState(selectedIndex)
        val scope = rememberCoroutineScope()
        val flingBehavior = rememberSnapFlingBehavior(listState)
        val centeredIndex by remember {
            derivedStateOf {
                val layout = listState.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                layout.visibleItemsInfo.minByOrNull {
                    abs((it.offset + it.size / 2) - center)
                }?.index ?: selectedIndex
            }
        }
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = padding),
        ) {
            itemsIndexed(DailyGoalScale.values) { index, goal ->
                val selected = index == centeredIndex
                Text(
                    text = "$goal",
                    modifier = Modifier
                        .width(itemWidth)
                        .clickable {
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                        .padding(vertical = 12.dp)
                        .testTag("daily-goal-$goal"),
                    style = if (selected) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress to centeredIndex }
                .distinctUntilChanged()
                .collect { (scrolling, index) ->
                    if (!scrolling) {
                        DailyGoalScale.values.getOrNull(index)?.let { goal ->
                            if (goal != selectedGoal) onGoal(goal)
                        }
                    }
                }
        }
    }
}
