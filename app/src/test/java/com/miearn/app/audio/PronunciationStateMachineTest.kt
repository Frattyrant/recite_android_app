package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationStateMachineTest {
    private val request = SpeechRequest("w1", "fixture", "audio/w1.ogg")
    private val latest = SpeechRequest("w2", "clamp", "audio/w2.ogg")

    @Test
    fun startsInitializing() {
        assertEquals(
            PronunciationStatus.INITIALIZING,
            PronunciationStateMachine().status,
        )
    }

    @Test
    fun requestDuringTtsInitializationIsReplayedWhenTtsBecomesReady() {
        val machine = PronunciationStateMachine()

        assertEquals(PlaybackAction.None, machine.request(request, assetAvailable = false))
        assertEquals(PronunciationStatus.INITIALIZING, machine.status)
        assertSpeak(request, retry = false, machine.onTtsReady())
        assertEquals(PronunciationStatus.READY, machine.status)
    }

    @Test
    fun onlyLatestInitializationRequestIsReplayed() {
        val machine = PronunciationStateMachine()
        machine.request(request, assetAvailable = false)
        machine.request(latest, assetAvailable = false)

        assertSpeak(latest, retry = false, machine.onTtsReady())
    }

    @Test
    fun noAssetRequestWhileSpeakingImmediatelyReplacesCurrentSpeech() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val first = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        machine.onStarted(first.token)

        val replacement = assertSpeak(
            latest,
            retry = false,
            machine.request(latest, assetAvailable = false),
        )

        machine.onFinished(first.token)
        assertEquals(PronunciationStatus.READY, machine.status)
        machine.onStarted(replacement.token)
        assertEquals(PronunciationStatus.SPEAKING, machine.status)
        machine.onFinished(replacement.token)
        assertEquals(PronunciationStatus.READY, machine.status)
    }

    @Test
    fun availableAssetIsPlayed() {
        val machine = PronunciationStateMachine()

        assertPlayAsset(request, machine.request(request, assetAvailable = true))
    }

    @Test
    fun ttsReadyDoesNotReplayAnAssetRequestAsSpeech() {
        val machine = PronunciationStateMachine()
        machine.request(request, assetAvailable = true)

        assertEquals(PlaybackAction.None, machine.onTtsReady())
        assertEquals(PronunciationStatus.READY, machine.status)
    }

    @Test
    fun currentAssetFailureFallsBackToTts() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val asset = assertPlayAsset(
            request,
            machine.request(request, assetAvailable = true),
        )

        assertSpeak(request, retry = false, machine.onAssetFailure(asset.token))
    }

    @Test
    fun assetFailureDuringTtsInitializationIsReplayedWhenTtsBecomesReady() {
        val machine = PronunciationStateMachine()
        val asset = assertPlayAsset(
            request,
            machine.request(request, assetAvailable = true),
        )

        assertEquals(PlaybackAction.None, machine.onAssetFailure(asset.token))
        assertSpeak(request, retry = false, machine.onTtsReady())
    }

    @Test
    fun currentTtsFailureRetriesOnceThenBecomesUnavailable() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val first = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )

        val retry = assertSpeak(
            request,
            retry = true,
            machine.onTtsFailure(first.token),
        )
        assertNotEquals(first.token, retry.token)
        assertEquals(PronunciationStatus.READY, machine.status)
        assertEquals(PlaybackAction.MarkUnavailable, machine.onTtsFailure(retry.token))
        assertEquals(PronunciationStatus.UNAVAILABLE, machine.status)
    }

    @Test
    fun staleCallbacksAndFailuresAreIgnoredWithoutChangingStatus() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val stale = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        val current = assertSpeak(
            latest,
            retry = false,
            machine.request(latest, assetAvailable = false),
        )

        assertEquals(PlaybackAction.None, machine.onAssetFailure(stale.token))
        assertEquals(PlaybackAction.None, machine.onTtsFailure(stale.token))
        machine.onStarted(stale.token)
        machine.onFinished(stale.token)
        assertEquals(PronunciationStatus.READY, machine.status)

        machine.onStarted(current.token)
        assertEquals(PronunciationStatus.SPEAKING, machine.status)
        machine.onFinished(stale.token)
        assertEquals(PronunciationStatus.SPEAKING, machine.status)
    }

    @Test
    fun currentStartAndFinishCallbacksUpdateStatus() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val action = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )

        machine.onStarted(action.token)
        assertEquals(PronunciationStatus.SPEAKING, machine.status)

        machine.onFinished(action.token)
        assertEquals(PronunciationStatus.READY, machine.status)
    }

    @Test
    fun eachNewRequestResetsTheTtsRetryCounter() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val first = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        assertSpeak(request, retry = true, machine.onTtsFailure(first.token))

        val next = assertSpeak(
            latest,
            retry = false,
            machine.request(latest, assetAvailable = false),
        )

        assertSpeak(latest, retry = true, machine.onTtsFailure(next.token))
        assertEquals(PronunciationStatus.READY, machine.status)
    }

    @Test
    fun sameIdConsecutiveRequestsIgnoreCallbacksFromPriorAttempt() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val first = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        machine.onStarted(first.token)

        val second = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        assertNotEquals(first.token, second.token)

        machine.onStarted(first.token)
        machine.onFinished(first.token)
        assertEquals(PronunciationStatus.READY, machine.status)

        machine.onStarted(second.token)
        assertEquals(PronunciationStatus.SPEAKING, machine.status)
        machine.onFinished(second.token)
        assertEquals(PronunciationStatus.READY, machine.status)
    }

    @Test
    fun callbacksAfterUnavailableCannotReviveStatus() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val first = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        val retry = assertSpeak(
            request,
            retry = true,
            machine.onTtsFailure(first.token),
        )
        assertEquals(PlaybackAction.MarkUnavailable, machine.onTtsFailure(retry.token))

        machine.onStarted(retry.token)
        machine.onFinished(retry.token)

        assertEquals(PronunciationStatus.UNAVAILABLE, machine.status)
    }

    @Test
    fun replacingSpeakingTtsWithAssetUsesAssetLifecycle() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val speech = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        machine.onStarted(speech.token)
        assertEquals(PronunciationStatus.SPEAKING, machine.status)

        val asset = assertPlayAsset(
            latest,
            machine.request(latest, assetAvailable = true),
        )
        assertEquals(PronunciationStatus.READY, machine.status)

        machine.onFinished(speech.token)
        assertEquals(PronunciationStatus.READY, machine.status)
        machine.onStarted(asset.token)
        assertEquals(PronunciationStatus.SPEAKING, machine.status)
        machine.onFinished(asset.token)
        assertEquals(PronunciationStatus.READY, machine.status)
    }

    @Test
    fun staleFirstAttemptErrorCannotFailItsRetry() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val first = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        val retry = assertSpeak(
            request,
            retry = true,
            machine.onTtsFailure(first.token),
        )
        machine.onStarted(retry.token)

        assertEquals(PlaybackAction.None, machine.onTtsFailure(first.token))
        assertEquals(PronunciationStatus.SPEAKING, machine.status)
        assertEquals(PlaybackAction.MarkUnavailable, machine.onTtsFailure(retry.token))
    }

    @Test
    fun ttsInitializationFailureClearsPendingRequestAndBecomesUnavailable() {
        val machine = PronunciationStateMachine()
        machine.request(request, assetAvailable = false)

        assertEquals(PlaybackAction.MarkUnavailable, machine.onTtsUnavailable())
        assertEquals(PronunciationStatus.UNAVAILABLE, machine.status)
        assertEquals(PlaybackAction.None, machine.onTtsReady())
    }

    @Test
    fun futureNoAssetRequestsDoNotQueueAfterTtsBecomesUnavailable() {
        val machine = PronunciationStateMachine()
        machine.onTtsUnavailable()

        assertEquals(PlaybackAction.MarkUnavailable, machine.request(request, assetAvailable = false))
        assertEquals(PlaybackAction.None, machine.onTtsReady())
        assertEquals(PronunciationStatus.UNAVAILABLE, machine.status)
    }

    @Test
    fun assetPlaybackRemainsAvailableAfterTtsBecomesUnavailable() {
        val machine = PronunciationStateMachine()
        machine.onTtsUnavailable()

        val asset = assertPlayAsset(
            request,
            machine.request(request, assetAvailable = true),
        )
        machine.onStarted(asset.token)
        assertEquals(PronunciationStatus.SPEAKING, machine.status)
        machine.onFinished(asset.token)
        assertEquals(PronunciationStatus.READY, machine.status)
    }

    @Test
    fun closeInvalidatesActiveAttemptAndRejectsFutureRequests() {
        val machine = PronunciationStateMachine().apply { onTtsReady() }
        val speech = assertSpeak(
            request,
            retry = false,
            machine.request(request, assetAvailable = false),
        )
        machine.onStarted(speech.token)

        machine.close()
        machine.onFinished(speech.token)

        assertEquals(PronunciationStatus.UNAVAILABLE, machine.status)
        assertEquals(PlaybackAction.None, machine.request(latest, assetAvailable = true))
        assertEquals(PlaybackAction.None, machine.onTtsReady())
        assertEquals(PlaybackAction.None, machine.onTtsFailure(speech.token))
    }

    private fun assertPlayAsset(
        expectedRequest: SpeechRequest,
        action: PlaybackAction,
    ): PlaybackAction.PlayAsset {
        assertTrue(action is PlaybackAction.PlayAsset)
        action as PlaybackAction.PlayAsset
        assertEquals(expectedRequest, action.request)
        assertTrue(action.token > 0)
        return action
    }

    private fun assertSpeak(
        expectedRequest: SpeechRequest,
        retry: Boolean,
        action: PlaybackAction,
    ): PlaybackAction.Speak {
        assertTrue(action is PlaybackAction.Speak)
        action as PlaybackAction.Speak
        assertEquals(expectedRequest, action.request)
        assertEquals(retry, action.retry)
        assertTrue(action.token > 0)
        return action
    }
}
