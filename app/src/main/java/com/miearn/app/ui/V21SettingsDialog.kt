package com.miearn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miearn.app.data.settings.UserSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SoftPageHeader(
                title = "\u5B66\u4E60\u8BBE\u7F6E",
                subtitle = "\u8C03\u6574\u6BCF\u5929\u7684\u8282\u594F\u4E0E\u63D0\u9192",
            )
            SettingsGroup(title = "\u6BCF\u65E5\u65B0\u8BCD") {
                DailyGoalRuler(settings.dailyGoal, onGoal)
            }
            SettingsGroup(title = "\u53D1\u97F3") {
                SettingSwitchRow(
                    label = "\u5361\u7247\u81EA\u52A8\u53D1\u97F3",
                    checked = settings.autoPlay,
                    onCheckedChange = onAutoPlay,
                )
            }
            SettingsGroup(title = "\u5B66\u4E60\u63D0\u9192") {
                SettingSwitchRow(
                    label = "\u5B66\u4E60\u4EFB\u52A1\u63D0\u9192",
                    checked = settings.reminderEnabled,
                    onCheckedChange = onReminderEnabled,
                )
                ReminderTimeWheelPicker(
                    hour = settings.reminderHour,
                    minute = settings.reminderMinute,
                    onTime = onReminderTime,
                )
                reminderPermissionMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("\u5B8C\u6210")
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
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
