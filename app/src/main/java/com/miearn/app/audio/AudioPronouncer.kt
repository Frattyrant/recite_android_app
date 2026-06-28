package com.miearn.app.audio

import android.content.Context
import com.miearn.app.data.local.WordEntity
import kotlinx.coroutines.flow.StateFlow

class AudioPronouncer(context: Context) {
    private lateinit var executor: PronunciationActionExecutor
    private val driver: AndroidAudioPlaybackDriver

    init {
        val appContext = context.applicationContext
        driver = AndroidAudioPlaybackDriver(
            appContext,
            object : AndroidAudioCallbacks {
                override fun onTtsReady() {
                    executor.onTtsReady()
                }

                override fun onTtsUnavailable() {
                    executor.onTtsUnavailable()
                }

                override fun onAssetStarted(token: Long) {
                    executor.onAssetStarted(token)
                }

                override fun onAssetFinished(token: Long) {
                    executor.onAssetFinished(token)
                }

                override fun onAssetError(token: Long) {
                    executor.onAssetError(token)
                }

                override fun onTtsStarted(utteranceId: String) {
                    executor.onTtsStarted(utteranceId)
                }

                override fun onTtsFinished(utteranceId: String) {
                    executor.onTtsFinished(utteranceId)
                }

                override fun onTtsError(utteranceId: String) {
                    executor.onTtsError(utteranceId)
                }
            },
        )
        executor = PronunciationActionExecutor(driver)
        driver.initializeTts()
    }

    val status: StateFlow<PronunciationStatus>
        get() = executor.status

    fun play(word: WordEntity) {
        playRequest(SpeechRequestFactory.full(word))
    }

    fun playVariant(word: WordEntity, index: Int) {
        playRequest(SpeechRequestFactory.variant(word, index))
    }

    fun stop() {
        executor.stop()
    }

    private fun playRequest(request: SpeechRequest) {
        executor.play(request) {
            AssetAvailability.exists(
                driver.assetManager,
                request.assetPath,
            )
        }
    }

    fun release() {
        executor.release()
    }
}
