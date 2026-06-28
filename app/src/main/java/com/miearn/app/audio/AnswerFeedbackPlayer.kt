package com.miearn.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.miearn.app.R

enum class AnswerFeedback {
    CORRECT,
    WRONG,
}

class AnswerFeedbackPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundIds = mapOf(
        AnswerFeedback.CORRECT to soundPool.load(context, R.raw.answer_correct, 1),
        AnswerFeedback.WRONG to soundPool.load(context, R.raw.answer_wrong, 1),
    )

    fun play(feedback: AnswerFeedback) {
        soundIds[feedback]?.let { soundPool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun release() {
        soundPool.release()
    }
}
