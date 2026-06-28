package com.miearn.app.audio

data class SpeechRequest(
    val id: String,
    val text: String,
    val assetPath: String,
    val segments: List<SpeechSegment> = listOf(SpeechSegment(text, assetPath)),
)

sealed interface PlaybackAction {
    data object None : PlaybackAction
    data object MarkUnavailable : PlaybackAction
    data class PlayAsset(val request: SpeechRequest, val token: Long) : PlaybackAction
    data class Speak(
        val request: SpeechRequest,
        val retry: Boolean,
        val token: Long,
    ) : PlaybackAction
}

enum class PronunciationStatus {
    INITIALIZING,
    READY,
    SPEAKING,
    UNAVAILABLE,
}

class PronunciationStateMachine {
    var status = PronunciationStatus.INITIALIZING
        private set

    private var pendingTts: SpeechRequest? = null
    private var ttsFailures = 0
    private var ttsReady = false
    private var ttsUnavailable = false
    private var nextAttemptToken = 0L
    private var activeAttempt: ActiveAttempt? = null
    private var closed = false
    private var overallUnavailable = false

    fun request(request: SpeechRequest, assetAvailable: Boolean): PlaybackAction {
        if (closed) {
            return PlaybackAction.None
        }
        ttsFailures = 0
        pendingTts = null
        overallUnavailable = false
        invalidateActiveAttempt()
        return when {
            assetAvailable -> playAsset(request)

            !ttsReady && !ttsUnavailable -> {
                pendingTts = request
                PlaybackAction.None
            }

            ttsReady -> speak(request, retry = false)

            else -> markUnavailable()
        }
    }

    fun onTtsReady(): PlaybackAction {
        if (closed || ttsUnavailable) {
            return PlaybackAction.None
        }
        ttsReady = true
        if (status != PronunciationStatus.SPEAKING) {
            status = PronunciationStatus.READY
        }
        val pending = pendingTts
        pendingTts = null
        return pending?.let { speak(it, retry = false) } ?: PlaybackAction.None
    }

    fun onTtsUnavailable(): PlaybackAction {
        if (closed) {
            return PlaybackAction.None
        }
        ttsReady = false
        ttsUnavailable = true
        val hadPendingRequest = pendingTts != null
        pendingTts = null
        return if (hadPendingRequest) {
            markUnavailable()
        } else {
            if (status != PronunciationStatus.SPEAKING) {
                status = PronunciationStatus.READY
            }
            PlaybackAction.None
        }
    }

    fun onAssetFailure(token: Long): PlaybackAction {
        if (closed) {
            return PlaybackAction.None
        }
        val current = activeRequest(token, AttemptKind.ASSET) ?: return PlaybackAction.None
        invalidateActiveAttempt()
        return when {
            !ttsReady && !ttsUnavailable -> {
                pendingTts = current
                PlaybackAction.None
            }

            ttsReady -> speak(current, retry = false)
            else -> markUnavailable()
        }
    }

    fun onTtsFailure(token: Long): PlaybackAction {
        if (closed) {
            return PlaybackAction.None
        }
        val current = activeRequest(token, AttemptKind.TTS) ?: return PlaybackAction.None
        invalidateActiveAttempt()
        ttsFailures += 1
        return if (ttsFailures == 1) {
            speak(current, retry = true)
        } else {
            ttsReady = false
            ttsUnavailable = true
            pendingTts = null
            markUnavailable()
        }
    }

    fun onStarted(token: Long) {
        if (closed) {
            return
        }
        if (activeAttempt?.token == token) {
            status = PronunciationStatus.SPEAKING
        }
    }

    fun onFinished(token: Long) {
        if (closed) {
            return
        }
        if (activeAttempt?.token == token) {
            invalidateActiveAttempt()
        }
    }

    fun cancel() {
        if (closed) return
        pendingTts = null
        ttsFailures = 0
        invalidateActiveAttempt()
    }

    fun close() {
        if (closed) {
            return
        }
        closed = true
        ttsReady = false
        ttsUnavailable = true
        pendingTts = null
        activeAttempt = null
        status = PronunciationStatus.UNAVAILABLE
    }

    private fun playAsset(request: SpeechRequest): PlaybackAction.PlayAsset {
        val token = activate(request, AttemptKind.ASSET)
        return PlaybackAction.PlayAsset(request, token)
    }

    private fun speak(request: SpeechRequest, retry: Boolean): PlaybackAction.Speak {
        val token = activate(request, AttemptKind.TTS)
        return PlaybackAction.Speak(request, retry, token)
    }

    private fun activate(request: SpeechRequest, kind: AttemptKind): Long {
        nextAttemptToken += 1
        activeAttempt = ActiveAttempt(nextAttemptToken, request, kind)
        return nextAttemptToken
    }

    private fun activeRequest(token: Long, kind: AttemptKind): SpeechRequest? =
        activeAttempt
            ?.takeIf { it.token == token && it.kind == kind }
            ?.request

    private fun invalidateActiveAttempt() {
        activeAttempt = null
        status = when {
            overallUnavailable -> PronunciationStatus.UNAVAILABLE
            ttsUnavailable -> PronunciationStatus.READY
            ttsReady -> PronunciationStatus.READY
            else -> PronunciationStatus.INITIALIZING
        }
    }

    private fun markUnavailable(): PlaybackAction {
        overallUnavailable = true
        status = PronunciationStatus.UNAVAILABLE
        return PlaybackAction.MarkUnavailable
    }

    private enum class AttemptKind {
        ASSET,
        TTS,
    }

    private data class ActiveAttempt(
        val token: Long,
        val request: SpeechRequest,
        val kind: AttemptKind,
    )
}
