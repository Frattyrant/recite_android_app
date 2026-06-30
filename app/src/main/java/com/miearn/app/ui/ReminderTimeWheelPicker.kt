package com.miearn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun ReminderTimeWheelPicker(
    hour: Int,
    minute: Int,
    onTime: (Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("\u63d0\u9192\u65f6\u95f4")
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeWheel(
                    values = ReminderWheelValues.hours,
                    selectedValue = hour,
                    label = "\u5c0f\u65f6",
                    testTag = "reminder-hour-wheel",
                    onSelected = { onTime(it, minute) },
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                TimeWheel(
                    values = ReminderWheelValues.minutes,
                    selectedValue = minute,
                    label = "\u5206\u949f",
                    testTag = "reminder-minute-wheel",
                    onSelected = { onTime(hour, it) },
                )
            }
        }
    }
}

@Composable
private fun TimeWheel(
    values: List<Int>,
    selectedValue: Int,
    label: String,
    testTag: String,
    onSelected: (Int) -> Unit,
) {
    val initialIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val scope = rememberCoroutineScope()
    val latestOnSelected by rememberUpdatedState(onSelected)
    val centeredIndex by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2) - center)
            }?.index ?: initialIndex
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .width(92.dp)
                    .height(132.dp)
                    .testTag(testTag),
                contentPadding = PaddingValues(vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(values) { index, value ->
                    val isCentered = index == centeredIndex
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch { listState.animateScrollToItem(index) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ReminderWheelValues.label(value),
                            style = if (isCentered) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = if (isCentered) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                            },
                            fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    LaunchedEffect(listState, selectedValue) {
        snapshotFlow { listState.isScrollInProgress to centeredIndex }
            .distinctUntilChanged()
            .collect { (scrolling, index) ->
                val value = values.getOrNull(index)
                if (!scrolling && value != null && value != selectedValue) {
                    latestOnSelected(value)
                }
            }
    }
}
