package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class PronunciationActionExecutorTest {
    private val request = SpeechRequest("w1", "fixture", "audio/w1.ogg")
    private val latest = SpeechRequest("w2", "clamp", "audio/w2.ogg")

    @Test
    fun playStopsPriorAudioThenExecutesAssetAction() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)

        executor.play(request, assetAvailable = true)

        assertEquals(
            listOf("stop", "asset:audio/w1.ogg"),
            driver.events,
        )
        assertEquals(PronunciationStatus.INITIALIZING, executor.status.value)
    }

    @Test
    fun playStopsPriorAudioBeforeProbingAssetAvailability() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)

        executor.play(request) {
            driver.events += "probe"
            true
        }

        assertEquals(
            listOf("stop", "probe", "asset:audio/w1.ogg"),
            driver.events,
        )
    }

    @Test
    fun ttsReadyAutomaticallyExecutesPendingInitializationRequest() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)
        executor.play(request, assetAvailable = false)

        executor.onTtsReady()

        assertEquals(listOf("stop", "speak:fixture"), driver.events)
        assertEquals(PronunciationStatus.READY, executor.status.value)
    }

    @Test
    fun currentAssetErrorFallsBackToTts() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()
        executor.play(request, assetAvailable = true)
        val assetToken = driver.assetAttempts.single().token

        executor.onAssetError(assetToken)

        assertEquals("fixture", driver.speechAttempts.single().text)
        assertNotEquals(
            assetToken,
            PronunciationActionExecutor.tokenFromUtteranceId(
                driver.speechAttempts.single().utteranceId,
            ),
        )
    }

    @Test
    fun assetDriverExceptionAlsoExecutesFallback() {
        val driver = FakeAudioPlaybackDriver(throwOnAsset = true)
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()

        executor.play(request, assetAvailable = true)

        assertEquals(1, driver.assetAttempts.size)
        assertEquals(1, driver.speechAttempts.size)
    }

    @Test
    fun immediateSpeakErrorsRetryOnceWithFreshAttemptTokenThenBecomeUnavailable() {
        val driver = FakeAudioPlaybackDriver(speakResults = ArrayDeque(listOf(false, false)))
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()

        executor.play(request, assetAvailable = false)

        assertEquals(2, driver.speechAttempts.size)
        val firstId = driver.speechAttempts[0].utteranceId
        val retryId = driver.speechAttempts[1].utteranceId
        assertNotEquals(firstId, retryId)
        assertEquals(PronunciationStatus.UNAVAILABLE, executor.status.value)
    }

    @Test
    fun utteranceCallbacksUseAttemptTokenAndIgnoreStaleAttempt() {
        val driver = FakeAudioPlaybackDriver(speakResults = ArrayDeque(listOf(false, true)))
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()
        executor.play(request, assetAvailable = false)
        val staleId = driver.speechAttempts[0].utteranceId
        val retryId = driver.speechAttempts[1].utteranceId

        executor.onTtsStarted(staleId)
        assertEquals(PronunciationStatus.READY, executor.status.value)
        executor.onTtsStarted(retryId)
        assertEquals(PronunciationStatus.SPEAKING, executor.status.value)
        executor.onTtsFinished(staleId)
        assertEquals(PronunciationStatus.SPEAKING, executor.status.value)
        executor.onTtsFinished(retryId)
        assertEquals(PronunciationStatus.READY, executor.status.value)
    }

    @Test
    fun staleAssetCallbacksCannotChangeReplacementStatus() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()
        executor.play(request, assetAvailable = true)
        val staleToken = driver.assetAttempts.single().token
        executor.play(latest, assetAvailable = true)
        val currentToken = driver.assetAttempts.last().token

        executor.onAssetStarted(staleToken)
        executor.onAssetFinished(staleToken)
        assertEquals(PronunciationStatus.READY, executor.status.value)
        executor.onAssetStarted(currentToken)
        assertEquals(PronunciationStatus.SPEAKING, executor.status.value)
        executor.onAssetFinished(staleToken)
        assertEquals(PronunciationStatus.SPEAKING, executor.status.value)
        executor.onAssetFinished(currentToken)
        assertEquals(PronunciationStatus.READY, executor.status.value)
    }

    @Test
    fun ttsUnavailableClearsPendingAndFutureNoAssetPlaysRemainSilent() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)
        executor.play(request, assetAvailable = false)

        executor.onTtsUnavailable()
        executor.onTtsReady()
        executor.play(latest, assetAvailable = false)

        assertTrue(driver.speechAttempts.isEmpty())
        assertEquals(PronunciationStatus.UNAVAILABLE, executor.status.value)
    }

    @Test
    fun releaseStopsAndReleasesPlatformDriver() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)

        executor.release()

        assertEquals(listOf("release"), driver.events)
        assertEquals(PronunciationStatus.UNAVAILABLE, executor.status.value)
    }

    @Test
    fun malformedUtteranceIdsAreIgnored() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()
        executor.play(request, assetAvailable = false)

        executor.onTtsStarted("w1")
        executor.onTtsFinished("pronunciation-not-a-number")
        executor.onTtsError("")

        assertEquals(PronunciationStatus.READY, executor.status.value)
        assertEquals(1, driver.speechAttempts.size)
    }

    @Test
    fun concurrentCallbackCannotInterleaveWithPlayActionExecution() {
        val driver = BlockingStopDriver()
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()
        executor.play(request, assetAvailable = false)
        val staleId = driver.speechAttempts.single().utteranceId
        driver.blockNextStop = true

        val playThread = thread(name = "replacement-play") {
            executor.play(latest, assetAvailable = false)
        }
        assertTrue(driver.stopEntered.await(5, TimeUnit.SECONDS))

        val callbackStarted = CountDownLatch(1)
        val callbackThread = thread(name = "stale-tts-callback") {
            callbackStarted.countDown()
            executor.onTtsFinished(staleId)
        }
        assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
        val callbackStateWhilePlayIsBlocked = awaitBlockedOrTerminated(callbackThread)

        driver.allowStop.countDown()
        playThread.join(5_000)
        callbackThread.join(5_000)

        assertEquals(Thread.State.BLOCKED, callbackStateWhilePlayIsBlocked)
        assertFalse(playThread.isAlive)
        assertFalse(callbackThread.isAlive)

        val currentId = driver.speechAttempts.last().utteranceId
        assertNotEquals(staleId, currentId)
        executor.onTtsStarted(staleId)
        assertEquals(PronunciationStatus.READY, executor.status.value)
        executor.onTtsStarted(currentId)
        assertEquals(PronunciationStatus.SPEAKING, executor.status.value)
        executor.onTtsFinished(currentId)
        assertEquals(PronunciationStatus.READY, executor.status.value)
    }

    @Test
    fun releaseIsIdempotentAndAllLateOperationsAreNoOps() {
        val driver = FakeAudioPlaybackDriver()
        val executor = PronunciationActionExecutor(driver)
        executor.onTtsReady()
        executor.play(request, assetAvailable = false)
        val utteranceId = driver.speechAttempts.single().utteranceId
        executor.onTtsStarted(utteranceId)
        assertEquals(PronunciationStatus.SPEAKING, executor.status.value)

        executor.release()
        val eventsAfterRelease = driver.events.toList()
        var assetProbeCount = 0

        executor.onTtsStarted(utteranceId)
        executor.onTtsFinished(utteranceId)
        executor.onTtsError(utteranceId)
        executor.onAssetStarted(1L)
        executor.onAssetFinished(1L)
        executor.onAssetError(1L)
        executor.onTtsReady()
        executor.onTtsUnavailable()
        executor.play(latest) {
            assetProbeCount += 1
            true
        }
        executor.release()

        assertEquals(PronunciationStatus.UNAVAILABLE, executor.status.value)
        assertEquals(eventsAfterRelease, driver.events)
        assertEquals(0, assetProbeCount)
        assertEquals(1, driver.events.count { it == "release" })
    }

    private fun awaitBlockedOrTerminated(thread: Thread): Thread.State {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val state = thread.state
            if (state == Thread.State.BLOCKED || state == Thread.State.TERMINATED) {
                return state
            }
            Thread.onSpinWait()
        }
        return thread.state
    }

    private class FakeAudioPlaybackDriver(
        private val throwOnAsset: Boolean = false,
        private val speakResults: ArrayDeque<Boolean> = ArrayDeque(),
    ) : AudioPlaybackDriver {
        val events = mutableListOf<String>()
        val assetAttempts = mutableListOf<AssetAttempt>()
        val speechAttempts = mutableListOf<SpeechAttempt>()

        override fun stopPlayback() {
            events += "stop"
        }

        override fun playAsset(assetPath: String, token: Long) {
            events += "asset:$assetPath"
            assetAttempts += AssetAttempt(assetPath, token)
            if (throwOnAsset) {
                error("asset failure")
            }
        }

        override fun speak(text: String, utteranceId: String): Boolean {
            events += "speak:$text"
            speechAttempts += SpeechAttempt(text, utteranceId)
            return speakResults.removeFirstOrNull() ?: true
        }

        override fun release() {
            events += "release"
        }
    }

    private data class AssetAttempt(val path: String, val token: Long)

    private data class SpeechAttempt(val text: String, val utteranceId: String)

    private class BlockingStopDriver : AudioPlaybackDriver {
        @Volatile
        var blockNextStop = false
        val stopEntered = CountDownLatch(1)
        val allowStop = CountDownLatch(1)
        val speechAttempts = mutableListOf<SpeechAttempt>()

        override fun stopPlayback() {
            if (blockNextStop) {
                stopEntered.countDown()
                assertTrue(allowStop.await(5, TimeUnit.SECONDS))
            }
        }

        override fun playAsset(assetPath: String, token: Long) = Unit

        override fun speak(text: String, utteranceId: String): Boolean {
            speechAttempts += SpeechAttempt(text, utteranceId)
            return true
        }

        override fun release() = Unit
    }
}
