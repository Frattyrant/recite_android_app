package com.miearn.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AudioPlaybackDriver {
    fun stopPlayback()

    fun playAsset(assetPath: String, token: Long)

    fun speak(text: String, utteranceId: String): Boolean

    fun speakSegments(texts: List<String>, utteranceId: String): Boolean =
        speak(
            texts.joinToString(separator = ", "),
            utteranceId,
        )

    fun release()
}

class PronunciationActionExecutor(
    private val driver: AudioPlaybackDriver,
    private val machine: PronunciationStateMachine = PronunciationStateMachine(),
) {
    private val machineLock = Any()
    private val mutableStatus = MutableStateFlow(machine.status)
    private var closed = false
    val status: StateFlow<PronunciationStatus> = mutableStatus.asStateFlow()

    fun play(request: SpeechRequest, assetAvailable: Boolean) {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            playLocked(request) { assetAvailable }
        }
    }

    fun play(request: SpeechRequest, assetAvailable: () -> Boolean) {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            playLocked(request, assetAvailable)
        }
    }

    private fun playLocked(request: SpeechRequest, assetAvailable: () -> Boolean) {
        runCatching { driver.stopPlayback() }
        val available = runCatching(assetAvailable).getOrDefault(false)
        execute(machine.request(request, available))
    }

    fun onTtsReady() {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            execute(machine.onTtsReady())
        }
    }

    fun onTtsUnavailable() {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            execute(machine.onTtsUnavailable())
        }
    }

    fun onAssetStarted(token: Long) {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            machine.onStarted(token)
            synchronizeStatus()
        }
    }

    fun onAssetFinished(token: Long) {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            machine.onFinished(token)
            synchronizeStatus()
        }
    }

    fun onAssetError(token: Long) {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            execute(machine.onAssetFailure(token))
        }
    }

    fun onTtsStarted(utteranceId: String) {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            tokenFromUtteranceId(utteranceId)?.let(machine::onStarted)
            synchronizeStatus()
        }
    }

    fun onTtsFinished(utteranceId: String) {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            tokenFromUtteranceId(utteranceId)?.let(machine::onFinished)
            synchronizeStatus()
        }
    }

    fun onTtsError(utteranceId: String) {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            val token = tokenFromUtteranceId(utteranceId)
            if (token != null) {
                execute(machine.onTtsFailure(token))
            } else {
                synchronizeStatus()
            }
        }
    }

    fun release() {
        synchronized(machineLock) {
            if (closed) {
                return
            }
            closed = true
            machine.close()
            synchronizeStatus()
            runCatching { driver.release() }
        }
    }

    private fun execute(initialAction: PlaybackAction) {
        var action = initialAction
        while (!closed && action != PlaybackAction.None) {
            synchronizeStatus()
            action = when (action) {
                is PlaybackAction.PlayAsset -> {
                    val accepted = runCatching {
                        driver.playAsset(action.request.assetPath, action.token)
                    }.isSuccess
                    if (accepted) {
                        PlaybackAction.None
                    } else {
                        machine.onAssetFailure(action.token)
                    }
                }

                is PlaybackAction.Speak -> {
                    val accepted = runCatching {
                        driver.speakSegments(
                            action.request.segments.map(SpeechSegment::text),
                            utteranceId(action.token),
                        )
                    }.getOrDefault(false)
                    if (accepted) {
                        PlaybackAction.None
                    } else {
                        machine.onTtsFailure(action.token)
                    }
                }

                PlaybackAction.MarkUnavailable -> PlaybackAction.None
                PlaybackAction.None -> PlaybackAction.None
            }
        }
        synchronizeStatus()
    }

    private fun synchronizeStatus() {
        mutableStatus.value = machine.status
    }

    companion object {
        private const val UTTERANCE_PREFIX = "pronunciation-"

        fun utteranceId(token: Long): String = "$UTTERANCE_PREFIX$token"

        fun tokenFromUtteranceId(utteranceId: String): Long? =
            utteranceId
                .takeIf { it.startsWith(UTTERANCE_PREFIX) }
                ?.removePrefix(UTTERANCE_PREFIX)
                ?.toLongOrNull()
    }
}
