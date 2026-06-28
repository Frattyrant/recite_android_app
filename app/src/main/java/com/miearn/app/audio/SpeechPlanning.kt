package com.miearn.app.audio

import com.miearn.app.data.local.WordEntity
import com.miearn.app.domain.EnglishVariantParser

data class SpeechSegment(
    val text: String,
    val assetPath: String,
)

object SpeechRequestFactory {
    fun full(word: WordEntity): SpeechRequest {
        val variants = EnglishVariantParser.parse(word.english)
        return SpeechRequest(
            id = word.id,
            text = word.audioText,
            assetPath = word.audioAsset,
            segments = variants.mapIndexed { index, text ->
                SpeechSegment(
                    text = text,
                    assetPath = if (variants.size == 1) {
                        word.audioAsset
                    } else {
                        variantAssetPath(word.id, index)
                    },
                )
            },
        )
    }

    fun variant(word: WordEntity, index: Int): SpeechRequest {
        val variants = EnglishVariantParser.parse(word.english)
        val text = variants.getOrElse(index) {
            throw IndexOutOfBoundsException("No English variant $index for ${word.id}")
        }
        val assetPath = if (variants.size == 1) {
            word.audioAsset
        } else {
            variantAssetPath(word.id, index)
        }
        return SpeechRequest(
            id = "${word.id}#$index",
            text = text,
            assetPath = assetPath,
            segments = listOf(SpeechSegment(text, assetPath)),
        )
    }

    private fun variantAssetPath(wordId: String, index: Int): String =
        "audio/variants/${wordId}_${index.toString().padStart(2, '0')}.ogg"
}

sealed interface TtsQueueItem {
    data class Speak(
        val text: String,
        val isFirst: Boolean,
        val isFinal: Boolean,
    ) : TtsQueueItem

    data class Silence(val durationMillis: Long) : TtsQueueItem
}

object TtsQueuePlan {
    const val pauseMillis = 500L

    fun create(texts: List<String>): List<TtsQueueItem> = buildList {
        texts.filter(String::isNotBlank).forEachIndexed { index, text ->
            if (index > 0) add(TtsQueueItem.Silence(pauseMillis))
            add(
                TtsQueueItem.Speak(
                    text = text,
                    isFirst = index == 0,
                    isFinal = index == texts.lastIndex,
                ),
            )
        }
    }
}
