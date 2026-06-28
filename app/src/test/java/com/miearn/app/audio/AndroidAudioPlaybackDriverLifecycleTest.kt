package com.miearn.app.audio

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@LooperMode(LooperMode.Mode.PAUSED)
class AndroidAudioPlaybackDriverLifecycleTest {
    @Test
    fun releaseBeforeOwnerQueueRunsMakesEveryLateOperationANoOp() {
        val callbacks = RecordingCallbacks()
        val context: Context = ApplicationProvider.getApplicationContext()
        val driver = AndroidAudioPlaybackDriver(context, callbacks)

        driver.release()
        driver.release()
        driver.initializeTts()
        driver.stopPlayback()
        driver.playAsset("audio/missing.ogg", 9L)
        assertFalse(driver.speak("fixture", "pronunciation-10"))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(callbacks.events.isEmpty())
    }

    private class RecordingCallbacks : AndroidAudioCallbacks {
        val events = mutableListOf<String>()

        override fun onTtsReady() {
            events += "ready"
        }

        override fun onTtsUnavailable() {
            events += "unavailable"
        }

        override fun onAssetStarted(token: Long) {
            events += "asset-started:$token"
        }

        override fun onAssetFinished(token: Long) {
            events += "asset-finished:$token"
        }

        override fun onAssetError(token: Long) {
            events += "asset-error:$token"
        }

        override fun onTtsStarted(utteranceId: String) {
            events += "tts-started:$utteranceId"
        }

        override fun onTtsFinished(utteranceId: String) {
            events += "tts-finished:$utteranceId"
        }

        override fun onTtsError(utteranceId: String) {
            events += "tts-error:$utteranceId"
        }
    }
}
