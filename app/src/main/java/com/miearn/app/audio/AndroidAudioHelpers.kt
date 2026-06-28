package com.miearn.app.audio

import android.content.Intent
import android.content.res.AssetManager
import android.speech.tts.TextToSpeech

object AssetAvailability {
    fun exists(assetManager: AssetManager, assetPath: String): Boolean =
        runCatching {
            assetManager.open(assetPath, AssetManager.ACCESS_STREAMING).use { stream ->
                stream.read()
            }
        }.isSuccess
}

object TtsInstallIntent {
    fun create(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
