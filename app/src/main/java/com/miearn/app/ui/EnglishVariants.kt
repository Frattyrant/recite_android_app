package com.miearn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.EnglishPresentation
import com.miearn.app.domain.EnglishVariantParser
import com.miearn.app.domain.from

enum class EnglishVariantsMode {
    COMPLETE,
    FOCUSED,
}

@Composable
fun EnglishVariants(
    word: WordEntity,
    onPlayVariant: (WordEntity, Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenVariant: (WordEntity, Int) -> Unit = onPlayVariant,
    mode: EnglishVariantsMode = EnglishVariantsMode.COMPLETE,
    allowExpansion: Boolean = true,
    onPlayAll: (() -> Unit)? = null,
) {
    if (mode == EnglishVariantsMode.FOCUSED) {
        FocusedEnglishVariants(
            word = word,
            onPlayVariant = onPlayVariant,
            onOpenVariant = onOpenVariant,
            allowExpansion = allowExpansion,
            onPlayAll = onPlayAll,
            modifier = modifier,
        )
        return
    }

    val variants = EnglishVariantParser.parse(word.english, word.kind)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        variants.forEachIndexed { index, variant ->
            EnglishVariantChip(
                text = variant,
                onOpen = { onOpenVariant(word, index) },
                onPlay = { onPlayVariant(word, index) },
                textStyle = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun FocusedEnglishVariants(
    word: WordEntity,
    onPlayVariant: (WordEntity, Int) -> Unit,
    onOpenVariant: (WordEntity, Int) -> Unit,
    allowExpansion: Boolean,
    onPlayAll: (() -> Unit)?,
    modifier: Modifier,
) {
    val presentation = EnglishPresentation.from(word)
    var expanded by rememberSaveable(word.id) { mutableStateOf(false) }
    val alternatives = presentation.alternatives

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EnglishVariantChip(
            text = presentation.primary.text,
            onOpen = { onOpenVariant(word, presentation.primary.index) },
            onPlay = { onPlayVariant(word, presentation.primary.index) },
            textStyle = MaterialTheme.typography.headlineSmall,
        )

        if (allowExpansion && alternatives.isNotEmpty()) {
            val relatedPhrases =
                presentation.alternativeKind ==
                    EnglishPresentation.AlternativeKind.RELATED_PHRASES
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    if (relatedPhrases) {
                        "另有 ${alternatives.size} 个相近说法"
                    } else {
                        "另有 ${alternatives.size} 种表达"
                    },
                )
            }
            if (expanded) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    alternatives.forEach { variant ->
                        EnglishVariantChip(
                            text = variant.text,
                            onOpen = { onOpenVariant(word, variant.index) },
                            onPlay = { onPlayVariant(word, variant.index) },
                            textStyle = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                onPlayAll?.let { playAll ->
                    TextButton(
                        onClick = playAll,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("播放全部")
                    }
                }
            }
        }
    }
}

@Composable
private fun EnglishVariantChip(
    text: String,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    textStyle: TextStyle,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(onClick = onPlay)
                    .semantics {
                        contentDescription = "播放 $text"
                    }
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                style = textStyle,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = onOpen,
                modifier = Modifier.semantics {
                    contentDescription = "查看 $text 详情"
                },
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                )
            }
        }
    }
}
