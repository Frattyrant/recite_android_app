package com.miearn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.miearn.app.data.local.WordEntity

data class ExamplePairDisplay(
    val english: String,
    val chinese: String,
)

/**
 * New content stores parallel examples as newline-delimited text. Keeping the
 * parser here also makes imported/legacy one-line content render unchanged.
 */
fun examplePairs(word: WordEntity): List<ExamplePairDisplay> {
    val english = word.exampleEn
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    if (english.isEmpty()) return emptyList()
    val chinese = word.exampleZh
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    return english.mapIndexed { index, sentence ->
        ExamplePairDisplay(
            english = sentence,
            chinese = chinese.getOrNull(index)
                ?: chinese.singleOrNull().orEmpty(),
        )
    }
}

@Composable
fun ExampleList(
    word: WordEntity,
    modifier: Modifier = Modifier,
    onPlayExample: ((String) -> Unit)? = null,
) {
    val examples = examplePairs(word)
    if (examples.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        examples.forEachIndexed { index, example ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (examples.size == 1) "例句" else "例句 ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        example.english,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                    )
                    if (example.chinese.isNotBlank()) {
                        Text(
                            example.chinese,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                bottom = if (index == examples.lastIndex) 0.dp else 6.dp,
                            ),
                        )
                    }
                }
                onPlayExample?.let { play ->
                    IconButton(
                        onClick = { play(example.english) },
                        modifier = Modifier.semantics {
                            contentDescription = "播放例句 ${example.english}"
                        },
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                }
            }
        }
    }
}
