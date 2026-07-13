package com.miearn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.EnglishVariantParser

@Composable
fun EnglishVariants(
    word: WordEntity,
    onPlayVariant: (WordEntity, Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenVariant: (WordEntity, Int) -> Unit = onPlayVariant,
) {
    val variants = EnglishVariantParser.parse(word.english, word.kind)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        variants.forEachIndexed { index, variant ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = variant,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { onOpenVariant(word, index) }
                            .semantics {
                                contentDescription = "查看 $variant 详情"
                            }
                            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(
                        onClick = { onPlayVariant(word, index) },
                        modifier = Modifier.semantics {
                            contentDescription = "播放 $variant"
                        },
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}