package com.miearn.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes as AndroidAudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

interface AndroidAudioCallbacks {
    fun onTtsReady()

    fun onTtsUnavailable()

    fun onAssetStarted(token: Long)

    fun onAssetFinished(token: Long)

    fun onAssetError(token: Long)

    fun onTtsStarted(utteranceId: String)

    fun onTtsFinished(utteranceId: String)

    fun onTtsError(utteranceId: String)
}

class HandlerOwnerTaskDispatcher(
    looper: Looper,
) : OwnerTaskDispatcher {
    private val handler = Handler(looper)

    override fun post(task: () -> Unit): Boolean = handler.post(task)
}

class AndroidAudioPlaybackDriver(
    context: Context,
    private val callbacks: AndroidAudioCallbacks,
) : AudioPlaybackDriver {
    private val appContext = context.applicationContext
    private val ownerLooper = Looper.getMainLooper()
    private val ownerTasks = OwnerTaskQueue(
        HandlerOwnerTaskDispatcher(ownerLooper),
    )
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private val ttsSequenceParts = mutableMapOf<String, TtsSequencePart>()
    private val ttsLifecycle = OwnerTtsLifecycle<TextToSpeech>(
        postToOwner = ownerTasks::submit,
        configure = ::configureTts,
        shutdown = ::shutdownTts,
        onReady = {
            if (!ownerTasks.isClosed) {
                callbacks.onTtsReady()
            }
        },
        onUnavailable = {
            if (!ownerTasks.isClosed) {
                callbacks.onTtsUnavailable()
            }
        },
    )

    val assetManager: AssetManager
        get() = appContext.assets

    init {
        ownerTasks.submit {
            player = createPlayer()
        }
    }

    fun initializeTts() {
        ownerTasks.submit {
            ttsLifecycle.start { onInit ->
                TextToSpeech(appContext) { result ->
                    onInit(result)
                }
            }
        }
    }

    override fun stopPlayback() {
        ownerTasks.submit {
            detachPlayerListener()
            runCatching { player?.stop() }
            ttsSequenceParts.clear()
            runCatching { ttsLifecycle.current?.stop() }
        }
    }

    override fun playAsset(assetPath: String, token: Long) {
        val accepted = ownerTasks.submit {
            val activePlayer = player
            if (activePlayer == null) {
                if (!ownerTasks.isClosed) {
                    callbacks.onAssetError(token)
                }
                return@submit
            }
            detachPlayerListener()
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!ownerTasks.isClosed && isPlaying) {
                        callbacks.onAssetStarted(token)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!ownerTasks.isClosed && playbackState == Player.STATE_ENDED) {
                        callbacks.onAssetFinished(token)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!ownerTasks.isClosed) {
                        callbacks.onAssetError(token)
                    }
                }
            }
            playerListener = listener
            try {
                activePlayer.addListener(listener)
                activePlayer.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(token.toString())
                        .setUri("asset:///$assetPath")
                        .build(),
                )
                activePlayer.prepare()
                activePlayer.play()
            } catch (_: RuntimeException) {
                activePlayer.removeListener(listener)
                if (playerListener === listener) {
                    playerListener = null
                }
                if (!ownerTasks.isClosed) {
                    callbacks.onAssetError(token)
                }
            }
        }
        if (!accepted && !ownerTasks.isClosed) {
            callbacks.onAssetError(token)
        }
    }

    override fun speak(text: String, utteranceId: String): Boolean {
        return speakSegments(listOf(text), utteranceId)
    }

    override fun speakSegments(texts: List<String>, utteranceId: String): Boolean {
        val cleanTexts = texts.map(String::trim).filter(String::isNotEmpty)
        if (cleanTexts.isEmpty()) return false
        return ownerTasks.submit {
            val activeTts = ttsLifecycle.current
            if (activeTts == null) {
                if (!ownerTasks.isClosed) {
                    callbacks.onTtsError(utteranceId)
                }
                return@submit
            }
            ttsSequenceParts.entries.removeAll { it.value.parentId == utteranceId }
            var accepted = true
            cleanTexts.forEachIndexed { index, segmentText ->
                if (index > 0) {
                    val silenceResult = runCatching {
                        activeTts.playSilentUtterance(
                            TtsQueuePlan.pauseMillis,
                            TextToSpeech.QUEUE_ADD,
                            "$utteranceId-silence-$index",
                        )
                    }.getOrDefault(TextToSpeech.ERROR)
                    accepted = accepted && silenceResult == TextToSpeech.SUCCESS
                }
                val childId = "$utteranceId-part-$index"
                ttsSequenceParts[childId] = TtsSequencePart(
                    parentId = utteranceId,
                    isFirst = index == 0,
                    isLast = index == cleanTexts.lastIndex,
                )
                val result = runCatching {
                    activeTts.speak(
                        segmentText,
                        if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                        null,
                        childId,
                    )
                }.getOrDefault(TextToSpeech.ERROR)
                accepted = accepted && result == TextToSpeech.SUCCESS
            }
            if (!ownerTasks.isClosed && !accepted) {
                callbacks.onTtsError(utteranceId)
            }
        }
    }

    override fun release() {
        ownerTasks.close {
            detachPlayerListener()
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
            ttsSequenceParts.clear()
            ttsLifecycle.close()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(UnstableApi::class)
    private fun createPlayer(): ExoPlayer? {
        var created: ExoPlayer? = null
        return try {
            created = ExoPlayer.Builder(appContext)
                .setLooper(ownerLooper)
                .build()
            created.setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            created
        } catch (_: RuntimeException) {
            runCatching { created?.release() }
            null
        }
    }

    private fun configureTts(activeTts: TextToSpeech, result: Int): Boolean {
        if (result != TextToSpeech.SUCCESS || !configureVoice(activeTts)) {
            return false
        }
        if (!configureUtteranceListener(activeTts)) {
            return false
        }
        if (activeTts.setSpeechRate(SPEECH_RATE) != TextToSpeech.SUCCESS) {
            return false
        }
        if (activeTts.setPitch(SPEECH_PITCH) != TextToSpeech.SUCCESS) {
            return false
        }
        return activeTts.setAudioAttributes(
            AndroidAudioAttributes.Builder()
                .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        ) == TextToSpeech.SUCCESS
    }

    private fun configureVoice(activeTts: TextToSpeech): Boolean {
        val availableVoices = activeTts.voices.orEmpty()
        val voicesByCandidate = availableVoices.associateBy { voice ->
            voice.toCandidate()
        }
        val selected = VoiceSelectionPolicy.select(voicesByCandidate.keys)
        return if (selected != null) {
            activeTts.setVoice(voicesByCandidate.getValue(selected)) == TextToSpeech.SUCCESS
        } else {
            activeTts.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
        }
    }

    private fun configureUtteranceListener(activeTts: TextToSpeech): Boolean =
        activeTts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    ownerTasks.submit {
                        val part = ttsSequenceParts[utteranceId]
                        if (part?.isFirst == true) callbacks.onTtsStarted(part.parentId)
                    }
                }

                override fun onDone(utteranceId: String) {
                    ownerTasks.submit {
                        val part = ttsSequenceParts.remove(utteranceId)
                        if (part?.isLast == true) callbacks.onTtsFinished(part.parentId)
                    }
                }

                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String) {
                    ownerTasks.submit {
                        reportTtsPartError(utteranceId)
                    }
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    ownerTasks.submit {
                        reportTtsPartError(utteranceId)
                    }
                }
            },
        ) == TextToSpeech.SUCCESS

    private fun reportTtsPartError(childId: String) {
        val parentId = ttsSequenceParts.remove(childId)?.parentId ?: childId
        ttsSequenceParts.entries.removeAll { it.value.parentId == parentId }
        callbacks.onTtsError(parentId)
    }

    private fun shutdownTts(activeTts: TextToSpeech) {
        runCatching { activeTts.stop() }
        runCatching { activeTts.shutdown() }
    }

    private fun detachPlayerListener() {
        val listener = playerListener ?: return
        runCatching { player?.removeListener(listener) }
        playerListener = null
    }

    private fun Voice.toCandidate(): VoiceCandidate =
        VoiceCandidate(
            name = name,
            languageTag = locale.toLanguageTag(),
            requiresNetwork = isNetworkConnectionRequired,
        )

    private data class TtsSequencePart(
        val parentId: String,
        val isFirst: Boolean,
        val isLast: Boolean,
    )

    private companion object {
        const val SPEECH_RATE = 0.92f
        const val SPEECH_PITCH = 1.0f
    }
}
