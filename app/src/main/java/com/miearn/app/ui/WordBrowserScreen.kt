package com.miearn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miearn.app.data.local.WordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBrowserScreen(
    destination: WordBrowserDestination,
    query: String,
    words: List<WordEntity>,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onPlay: (WordEntity) -> Unit,
    onPlayVariant: (WordEntity, Int) -> Unit,
    onFavorite: (String) -> Unit,
) {
    var selectedWord by remember { mutableStateOf<WordEntity?>(null) }
    val focusRequester = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(destination.title, style = MaterialTheme.typography.headlineSmall)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("global-search-field"),
            placeholder = {
                Text(
                    if (destination == WordBrowserDestination.SEARCH) {
                        "搜索整个词库的英文或中文"
                    } else {
                        "在${destination.title}中搜索"
                    },
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        if (destination == WordBrowserDestination.SEARCH && query.isBlank()) {
            Text(
                "输入英文或中文开始搜索",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 36.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (words.isEmpty()) {
            Text(
                "暂无结果",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 36.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(words, key = WordEntity::id) { word ->
                    WordBrowserRow(
                        word = word,
                        onClick = { selectedWord = word },
                        onPlay = { onPlay(word) },
                        onFavorite = { onFavorite(word.id) },
                    )
                }
            }
        }
    }
    LaunchedEffect(destination) {
        if (destination == WordBrowserDestination.SEARCH) focusRequester.requestFocus()
    }
    selectedWord?.let { word ->
        ModalBottomSheet(onDismissRequest = { selectedWord = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EnglishVariants(word, onPlayVariant)
                Text(word.phonetic, color = MaterialTheme.colorScheme.primary)
                Text(word.chinese, style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = { onPlay(word) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "播放完整发音")
                    }
                    IconButton(onClick = { onFavorite(word.id) }) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "收藏")
                    }
                }
                Text(word.exampleEn, modifier = Modifier.fillMaxWidth())
                Text(
                    word.exampleZh,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (word.note.isNotBlank()) {
                    Text("备注：${word.note}", modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WordBrowserRow(
    word: WordEntity,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    word.english,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    word.chinese,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    word.categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "播放")
            }
            IconButton(onClick = onFavorite) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = "收藏")
            }
        }
    }
}
