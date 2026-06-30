package com.miearn.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.miearn.app.R

enum class AnswerFeedback {
    CORRECT,
    WRONG,
}


internal object AnswerFeedbackAudioPolicy {
    const val usage = AudioAttributes.USAGE_MEDIA
    const val contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION
}
class AnswerFeedbackPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AnswerFeedbackAudioPolicy.usage)
                .setContentType(AnswerFeedbackAudioPolicy.contentType)
                .build(),
        )
        .build()
    private val loadedGate = LoadedSoundGate { soundId ->
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }
    private val soundIds: Map<AnswerFeedback, Int>

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            loadedGate.onLoadComplete(soundId, status)
        }
        soundIds = mapOf(
            AnswerFeedback.CORRECT to soundPool.load(context, R.raw.answer_correct, 1),
            AnswerFeedback.WRONG to soundPool.load(context, R.raw.answer_wrong, 1),
        )
    }

    fun play(feedback: AnswerFeedback) {
        soundIds[feedback]?.let(loadedGate::request)
    }

    fun release() {
        loadedGate.close()
        soundPool.release()
    }
}